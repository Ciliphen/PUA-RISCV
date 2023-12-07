package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class DataMemoryAccess_DataMemory extends Bundle {
  val in = Input(new Bundle {
    val acc_err = Bool()
    val ready   = Bool()
    val rdata   = UInt(DATA_WID.W)
  })
  val out = Output(new Bundle {
    val en    = Bool()
    val rlen  = UInt(AXI_LEN_WID.W)
    val wen   = Bool()
    val wstrb = UInt(AXI_STRB_WID.W)
    val addr  = UInt(DATA_ADDR_WID.W)
    val wdata = UInt(DATA_WID.W)
  })
}

class DataMemoryAccess_MemoryUnit extends Bundle {
  val in = Input(new Bundle {
    val mem_en   = Bool()
    val info     = new InstInfo()
    val src_info = new SrcInfo()
    val ex       = new ExceptionInfo()

    val lr      = Bool()
    val lr_addr = UInt(DATA_ADDR_WID.W)
  })
  val out = Output(new Bundle {
    val ready = Bool()
    val rdata = UInt(DATA_WID.W)
    val ex    = new ExceptionInfo()

    val set_lr      = Bool()
    val set_lr_val  = Bool()
    val set_lr_addr = UInt(DATA_ADDR_WID.W)
  })
}

class DataMemoryAccess(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val memoryUnit = new DataMemoryAccess_MemoryUnit()
    val dataMemory = new DataMemoryAccess_DataMemory()
  })

  val atomAlu = Module(new AtomAlu()).io
  val lsExe   = Module(new LSExe()).io

  val valid = io.memoryUnit.in.mem_en
  val src1  = io.memoryUnit.in.src_info.src1_data
  val src2  = io.memoryUnit.in.src_info.src2_data
  val imm   = io.memoryUnit.in.info.imm
  val func  = io.memoryUnit.in.info.op
  val inst  = io.memoryUnit.in.info.inst

  val storeReq = valid & LSUOpType.isStore(func)
  val loadReq  = valid & LSUOpType.isLoad(func)
  val atomReq  = valid & LSUOpType.isAtom(func)
  val amoReq   = valid & LSUOpType.isAMO(func)
  val lrReq    = valid & LSUOpType.isLR(func)
  val scReq    = valid & LSUOpType.isSC(func)

  val aq     = inst(26)
  val rl     = inst(25)
  val funct3 = inst(14, 12)

  val atomWidthW = !funct3(0)
  val atomWidthD = funct3(0)

  // Atom LR/SC Control Bits
  val setLr     = Wire(Bool())
  val setLrVal  = Wire(Bool())
  val setLrAddr = Wire(UInt(DATA_ADDR_WID.W))
  val lr        = WireInit(Bool(), false.B)
  val lrAddr    = WireInit(UInt(DATA_ADDR_WID.W), DontCare)
  io.memoryUnit.out.set_lr      := setLr
  io.memoryUnit.out.set_lr_val  := setLrVal
  io.memoryUnit.out.set_lr_addr := setLrAddr
  lr                            := io.memoryUnit.in.lr
  lrAddr                        := io.memoryUnit.in.lr_addr

  val s_idle :: s_lr :: s_sc :: s_amo_l :: s_amo_a :: s_amo_s :: Nil = Enum(6)

  val state      = RegInit(s_idle)
  val atomMemReg = Reg(UInt(XLEN.W))
  val atomRegReg = Reg(UInt(XLEN.W))
  atomAlu.in.rdata := atomMemReg
  atomAlu.in.src2  := src2
  atomAlu.in.info  := io.memoryUnit.in.info

  val scInvalid = (src1 =/= lrAddr || !lr) && scReq

  lsExe.in.info           := io.memoryUnit.in.info
  lsExe.in.mem_addr       := 0.U
  lsExe.in.mem_en         := false.B
  lsExe.in.wdata          := 0.U
  io.memoryUnit.out.ready := false.B

  switch(state) {
    is(s_idle) { // calculate address
      lsExe.in.mem_en         := io.memoryUnit.in.mem_en && !atomReq
      lsExe.in.mem_addr       := src1 + imm
      lsExe.in.info.op        := func
      lsExe.in.wdata          := src2
      io.memoryUnit.out.ready := lsExe.out.ready || scInvalid
      state                   := s_idle

      when(amoReq) { state := s_amo_l }
      when(lrReq) { state := s_lr }
      when(scReq) { state := Mux(scInvalid, s_idle, s_sc) }

    }

    is(s_amo_l) {
      lsExe.in.mem_en         := true.B
      lsExe.in.mem_addr       := src1
      lsExe.in.info.op        := Mux(atomWidthD, LSUOpType.ld, LSUOpType.lw)
      lsExe.in.wdata          := DontCare
      io.memoryUnit.out.ready := false.B
      when(lsExe.out.ready) {
        state := s_amo_a;
      }
      atomMemReg := lsExe.out.rdata
      atomRegReg := lsExe.out.rdata
    }

    is(s_amo_a) {
      lsExe.in.mem_en         := false.B
      lsExe.in.mem_addr       := DontCare
      lsExe.in.info.op        := DontCare
      lsExe.in.wdata          := DontCare
      io.memoryUnit.out.ready := false.B
      state                   := s_amo_s
      atomMemReg              := atomAlu.out.result
    }

    is(s_amo_s) {
      lsExe.in.mem_en         := true.B
      lsExe.in.mem_addr       := src1
      lsExe.in.info.op        := Mux(atomWidthD, LSUOpType.sd, LSUOpType.sw)
      lsExe.in.wdata          := atomMemReg
      io.memoryUnit.out.ready := lsExe.out.ready
      when(lsExe.out.ready) {
        state := s_idle;
      }
    }
    is(s_lr) {
      lsExe.in.mem_en         := true.B
      lsExe.in.mem_addr       := src1
      lsExe.in.info.op        := Mux(atomWidthD, LSUOpType.ld, LSUOpType.lw)
      lsExe.in.wdata          := DontCare
      io.memoryUnit.out.ready := lsExe.out.ready
      when(lsExe.out.ready) {
        state := s_idle;
      }
    }
    is(s_sc) {
      lsExe.in.mem_en         := true.B
      lsExe.in.mem_addr       := src1
      lsExe.in.info.op        := Mux(atomWidthD, LSUOpType.sd, LSUOpType.sw)
      lsExe.in.wdata          := src2
      io.memoryUnit.out.ready := lsExe.out.ready
      when(lsExe.out.ready) {
        state := s_idle;
      }
    }
  }
  when(lsExe.out.loadAddrMisaligned || lsExe.out.storeAddrMisaligned) {
    state                   := s_idle
    io.memoryUnit.out.ready := true.B
    io.memoryUnit.out.ready := true.B
  }

  setLr     := io.memoryUnit.out.ready && (lrReq || scReq)
  setLrVal  := lrReq
  setLrAddr := src1

  io.dataMemory <> lsExe.dataMemory

  io.memoryUnit.out.ex                                := io.memoryUnit.in.ex
  io.memoryUnit.out.ex.exception(loadAddrMisaligned)  := lsExe.out.loadAddrMisaligned
  io.memoryUnit.out.ex.exception(storeAddrMisaligned) := lsExe.out.storeAddrMisaligned
  io.memoryUnit.out.ex.exception(loadAccessFault)     := lsExe.out.loadAccessFault
  io.memoryUnit.out.ex.exception(storeAccessFault)    := lsExe.out.storeAccessFault
  io.memoryUnit.out.ex.tval                           := io.dataMemory.out.addr
  io.memoryUnit.out.rdata                             := lsExe.out.rdata
}
