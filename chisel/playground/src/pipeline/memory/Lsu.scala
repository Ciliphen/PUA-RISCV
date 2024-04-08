package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class Lsu_DataMemory extends Bundle {
  val in = Input(new Bundle {
    val access_fault = Bool()
    val page_fault   = Bool()
    val ready        = Bool()
    val rdata        = UInt(XLEN.W)
  })
  val out = Output(new Bundle {
    val en    = Bool()
    val rlen  = UInt(AXI_LEN_WID.W)
    val wen   = Bool()
    val wstrb = UInt(AXI_STRB_WID.W)
    val addr  = UInt(XLEN.W)
    val wdata = UInt(XLEN.W)
  })
}

class Lsu_MemoryUnit extends Bundle {
  val in = Input(new Bundle {
    val mem_en   = Bool()
    val info     = new Info()
    val src_info = new SrcInfo()
    val ex       = new ExceptionInfo()

    val lr      = Bool()
    val lr_addr = UInt(XLEN.W)

    val allow_to_go = Bool()
  })
  val out = Output(new Bundle {
    val ready = Bool()
    val rdata = UInt(XLEN.W)
    val ex    = new ExceptionInfo()
    // 用于指示dcache完成一次请求
    val complete_single_request = Bool()

    val lr_wen   = Bool()
    val lr_wbit  = Bool()
    val lr_waddr = UInt(XLEN.W)
  })
}

class Lsu(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val memoryUnit = new Lsu_MemoryUnit()
    val dataMemory = new Lsu_DataMemory()
  })

  val atomAlu   = Module(new AtomAlu()).io
  val lsExecute = Module(new LsExecute()).io

  val valid = io.memoryUnit.in.mem_en
  val src1  = io.memoryUnit.in.src_info.src1_data
  val src2  = io.memoryUnit.in.src_info.src2_data
  val imm   = io.memoryUnit.in.info.imm
  val func  = io.memoryUnit.in.info.op
  val inst  = io.memoryUnit.in.info.inst

  val store_req = valid & LSUOpType.isStore(func)
  val load_req  = valid & LSUOpType.isLoad(func)
  val atom_req  = valid & LSUOpType.isAtom(func)
  val amo_req   = valid & LSUOpType.isAMO(func)
  val lr_req    = valid & LSUOpType.isLR(func)
  val sc_req    = valid & LSUOpType.isSC(func)

  val funct3 = inst(14, 12)
  val atom_d = funct3(0)

  // Atom LR/SC Control Bits
  val lr      = WireInit(Bool(), false.B)
  val lr_addr = WireInit(UInt(XLEN.W), DontCare)
  io.memoryUnit.out.lr_wen   := io.memoryUnit.out.ready && (lr_req || sc_req)
  io.memoryUnit.out.lr_wbit  := lr_req
  io.memoryUnit.out.lr_waddr := src1
  lr                         := io.memoryUnit.in.lr
  lr_addr                    := io.memoryUnit.in.lr_addr

  val s_idle :: s_sc :: s_amo_a :: s_amo_s :: Nil = Enum(4)

  val state      = RegInit(s_idle)
  val atom_wdata = Reg(UInt(XLEN.W))
  val atom_rdata = Reg(UInt(XLEN.W))
  atomAlu.in.rdata := atom_wdata
  atomAlu.in.src2  := src2
  atomAlu.in.info  := io.memoryUnit.in.info

  val sc_invalid = (src1 =/= lr_addr || !lr) && sc_req

  lsExecute.in.info       := DontCare
  lsExecute.in.mem_addr   := DontCare
  lsExecute.in.mem_en     := false.B
  lsExecute.in.wdata      := DontCare
  io.memoryUnit.out.ready := false.B

  val allow_to_go             = io.memoryUnit.in.allow_to_go
  val complete_single_request = Wire(Bool())
  // 只有amo操作时该信号才发挥作用
  complete_single_request := false.B

  io.memoryUnit.out.complete_single_request := complete_single_request

  switch(state) {
    is(s_idle) { // 0
      lsExecute.in.mem_en     := io.memoryUnit.in.mem_en && !atom_req
      lsExecute.in.mem_addr   := src1 + imm
      lsExecute.in.info.op    := func
      lsExecute.in.wdata      := src2
      io.memoryUnit.out.ready := lsExecute.out.ready || sc_invalid
      when(amo_req) {
        lsExecute.in.mem_en     := true.B
        lsExecute.in.mem_addr   := src1
        lsExecute.in.info.op    := Mux(atom_d, LSUOpType.ld, LSUOpType.lw)
        lsExecute.in.wdata      := DontCare
        io.memoryUnit.out.ready := false.B
        when(lsExecute.out.ready) {
          state := s_amo_a;
          // 告诉dcache已经完成一次访存操作，可以进入下一次访存
          complete_single_request := true.B
        }
        atom_wdata := lsExecute.out.rdata
        atom_rdata := lsExecute.out.rdata
      }
      when(lr_req) {
        lsExecute.in.mem_en     := true.B
        lsExecute.in.mem_addr   := src1
        lsExecute.in.info.op    := Mux(atom_d, LSUOpType.ld, LSUOpType.lw)
        lsExecute.in.wdata      := DontCare
        io.memoryUnit.out.ready := lsExecute.out.ready
      }
      when(sc_req) { state := Mux(sc_invalid, s_idle, s_sc) }
    }

    is(s_sc) { // 1
      lsExecute.in.mem_en     := true.B
      lsExecute.in.mem_addr   := src1
      lsExecute.in.info.op    := Mux(atom_d, LSUOpType.sd, LSUOpType.sw)
      lsExecute.in.wdata      := src2
      io.memoryUnit.out.ready := lsExecute.out.ready
      when(allow_to_go) {
        state := s_idle
      }
    }

    is(s_amo_a) { // 2
      lsExecute.in.mem_en     := false.B
      lsExecute.in.mem_addr   := DontCare
      lsExecute.in.info.op    := DontCare
      lsExecute.in.wdata      := DontCare
      io.memoryUnit.out.ready := false.B
      state                   := s_amo_s
      atom_wdata              := atomAlu.out.result
    }

    is(s_amo_s) { // 3
      lsExecute.in.mem_en     := true.B
      lsExecute.in.mem_addr   := src1
      lsExecute.in.info.op    := Mux(atom_d, LSUOpType.sd, LSUOpType.sw)
      lsExecute.in.wdata      := atom_wdata
      io.memoryUnit.out.ready := lsExecute.out.ready
      when(allow_to_go) {
        state := s_idle
      }
    }
  }

  when(
    lsExecute.out.addr_misaligned ||
      lsExecute.out.access_fault ||
      lsExecute.out.page_fault
  ) {
    state                   := s_idle
    io.memoryUnit.out.ready := true.B
    complete_single_request := false.B // 发生例外时应该由ctrl的allow to go控制
  }

  io.dataMemory <> lsExecute.dataMemory

  io.memoryUnit.out.ex                               := io.memoryUnit.in.ex
  io.memoryUnit.out.ex.exception(loadAddrMisaligned) := (load_req || lr_req) && lsExecute.out.addr_misaligned
  io.memoryUnit.out.ex.exception(loadAccessFault)    := (load_req || lr_req) && lsExecute.out.access_fault
  io.memoryUnit.out.ex.exception(loadPageFault)      := (load_req || lr_req) && lsExecute.out.page_fault
  io.memoryUnit.out.ex
    .exception(storeAddrMisaligned)                := (store_req || sc_req || amo_req) && lsExecute.out.addr_misaligned
  io.memoryUnit.out.ex.exception(storeAccessFault) := (store_req || sc_req || amo_req) && lsExecute.out.addr_misaligned
  io.memoryUnit.out.ex.exception(storePageFault)   := (store_req || sc_req || amo_req) && lsExecute.out.page_fault

  io.memoryUnit.out.ex.tval(loadAddrMisaligned)  := io.dataMemory.out.addr
  io.memoryUnit.out.ex.tval(loadAccessFault)     := io.dataMemory.out.addr
  io.memoryUnit.out.ex.tval(loadPageFault)       := io.dataMemory.out.addr
  io.memoryUnit.out.ex.tval(storeAddrMisaligned) := io.dataMemory.out.addr
  io.memoryUnit.out.ex.tval(storeAccessFault)    := io.dataMemory.out.addr
  io.memoryUnit.out.ex.tval(storePageFault)      := io.dataMemory.out.addr

  io.memoryUnit.out.rdata := MuxCase(
    lsExecute.out.rdata,
    Seq(
      (sc_req)  -> sc_invalid,
      (amo_req) -> atom_rdata
    )
  )
}
