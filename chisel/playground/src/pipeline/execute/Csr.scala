package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class CsrMemoryUnit(implicit val config: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val inst = Vec(
      config.fuNum,
      new Bundle {
        val pc   = UInt(PC_WID.W)
        val ex   = new ExceptionInfo()
        val info = new InstInfo()
      }
    )
    val set_lr      = Bool()
    val set_lr_val  = Bool()
    val set_lr_addr = UInt(DATA_ADDR_WID.W)
  })
  val out = Output(new Bundle {
    val flush    = Bool()
    val flush_pc = UInt(PC_WID.W)

    val lr      = Bool()
    val lr_addr = UInt(DATA_ADDR_WID.W)
  })
}

class CsrExecuteUnit(implicit val config: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val valid    = Bool()
    val info     = new InstInfo()
    val src_info = new SrcInfo()
    val ex       = new ExceptionInfo()
  })
  val out = Output(new Bundle {
    val rdata = UInt(DATA_WID.W)
    val ex    = new ExceptionInfo()
  })
}

class CsrDecoderUnit extends Bundle {
  val mode      = Output(Priv())
  val interrupt = Output(UInt(INT_WID.W))
}

class Csr(implicit val config: CpuConfig) extends Module with HasCSRConst {
  val io = IO(new Bundle {
    val ext_int = Input(new ExtInterrupt())
    val ctrl = Input(new Bundle {
      val exe_stall = Bool()
      val mem_stall = Bool()
    })
    val decoderUnit = new CsrDecoderUnit()
    val executeUnit = new CsrExecuteUnit()
    val memoryUnit  = new CsrMemoryUnit()
  })

  /* CSR寄存器定义 */
  val mvendorid    = RegInit(0.U(XLEN.W)) // 厂商ID
  val marchid      = RegInit(0.U(XLEN.W)) // 架构ID
  val mimpid       = RegInit(0.U(XLEN.W)) // 实现ID
  val mhartid      = RegInit(0.U(XLEN.W)) // 硬件线程ID
  val mconfigptr   = RegInit(0.U(XLEN.W)) // 配置寄存器指针
  val mstatus_init = Wire(new Mstatus())
  mstatus_init     := 0.U.asTypeOf(new Mstatus())
  mstatus_init.uxl := 2.U
  val mstatus   = RegInit(UInt(XLEN.W), mstatus_init.asUInt) // 状态寄存器
  val misa_init = Wire(new Misa())
  misa_init            := 0.U.asTypeOf(new Misa())
  misa_init.mxl        := 2.U
  misa_init.extensions := "h101100".U
  val misa       = RegInit(UInt(XLEN.W), misa_init.asUInt) // ISA寄存器
  val mie        = RegInit(0.U(XLEN.W)) // 中断使能寄存器
  val mtvec      = RegInit(0.U(XLEN.W)) // 中断向量基址寄存器
  val mcounteren = RegInit(0.U(XLEN.W)) // 计数器使能寄存器
  val mscratch   = RegInit(0.U(XLEN.W)) // 临时寄存器
  val mepc       = RegInit(0.U(XLEN.W)) // 异常程序计数器
  val mcause     = RegInit(0.U(XLEN.W)) // 异常原因寄存器
  val mtval      = RegInit(0.U(XLEN.W)) // 异常值寄存器
  val mipWire    = WireInit(0.U.asTypeOf(new Interrupt))
  val mipReg     = RegInit(0.U(64.W))
  val mipFixMask = "h77f".U(64.W)
  val mip        = (mipWire.asUInt | mipReg).asTypeOf(new Interrupt) // 中断挂起寄存器
  val mcycle     = RegInit(0.U(XLEN.W)) // 时钟周期计数器
  mcycle := mcycle + 1.U
  val minstret = RegInit(0.U(XLEN.W)) // 指令计数器

  val tselect = RegInit(1.U(XLEN.W)) // 跟踪寄存器选择寄存器
  val tdata1  = RegInit(0.U(XLEN.W)) // 跟踪寄存器数据1寄存器

  // 仅供调试使用
  val satp = RegInit(UInt(XLEN.W), 0.U)

  val pmpcfg0      = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg1      = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg2      = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg3      = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr0     = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr1     = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr2     = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr3     = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddrWmask = "h3fffffff".U(64.W) // 32bit physical address

  val rdata = Wire(UInt(XLEN.W))
  val wdata = Wire(UInt(XLEN.W))

  // Atom LR/SC Control Bits
  val set_lr      = WireInit(Bool(), false.B)
  val set_lr_val  = WireInit(Bool(), false.B)
  val set_lr_addr = WireInit(UInt(DATA_ADDR_WID.W), 0.U)
  val lr          = RegInit(Bool(), false.B)
  val lr_addr     = RegInit(UInt(DATA_ADDR_WID.W), 0.U)
  set_lr                    := io.memoryUnit.in.set_lr
  set_lr_val                := io.memoryUnit.in.set_lr_val
  set_lr_addr               := io.memoryUnit.in.set_lr_addr
  io.memoryUnit.out.lr      := lr
  io.memoryUnit.out.lr_addr := lr_addr

  when(set_lr) {
    lr      := set_lr_val
    lr_addr := set_lr_addr
  }

  // Side Effect
  def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN - 2, 0))
    mstatusNew
  }

  val mstatus_wmask = Mux(
    wdata.asTypeOf(new Mstatus).mpp === ModeM || wdata.asTypeOf(new Mstatus).mpp === ModeU,
    "h0000000000021888".U(64.W),
    "h0000000000020088".U(64.W)
  )

  // CSR reg map
  val mapping = Map(
    // User Trap Setup
    // MaskedRegMap(Ustatus, ustatus),
    // MaskedRegMap(Uie, uie, 0.U, MaskedRegMap.Unwritable),
    // MaskedRegMap(Utvec, utvec),

    // User Trap Handling
    // MaskedRegMap(Uscratch, uscratch),
    // MaskedRegMap(Uepc, uepc),
    // MaskedRegMap(Ucause, ucause),
    // MaskedRegMap(Utval, utval),
    // MaskedRegMap(Uip, uip),

    // User Floating-Point CSRs (not implemented)
    // MaskedRegMap(Fflags, fflags),
    // MaskedRegMap(Frm, frm),
    // MaskedRegMap(Fcsr, fcsr),

    // User Counter/Timers
    MaskedRegMap(Cycle, mcycle),
    // MaskedRegMap(Time, time),
    // MaskedRegMap(Instret, minstret),
    // // Supervisor Trap Setup TODO
    // MaskedRegMap(Sstatus, mstatus, sstatusWmask, mstatusUpdateSideEffect, sstatusRmask),
    // // MaskedRegMap(Sedeleg, Sedeleg),
    // // MaskedRegMap(Sideleg, Sideleg),
    // MaskedRegMap(Sie, mie, sieMask, MaskedRegMap.NoSideEffect, sieMask),
    // MaskedRegMap(Stvec, stvec),
    // MaskedRegMap(Scounteren, scounteren),
    // // Supervisor Trap Handling
    // MaskedRegMap(Sscratch, sscratch),
    // MaskedRegMap(Sepc, sepc),
    // MaskedRegMap(Scause, scause),
    // MaskedRegMap(Stval, stval),
    // MaskedRegMap(Sip, mip.asUInt, sipMask, MaskedRegMap.Unwritable, sipMask),
    // Supervisor Protection and Translation
    MaskedRegMap(Satp, satp),
    // Machine Information Registers
    MaskedRegMap(Mvendorid, mvendorid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Marchid, marchid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mimpid, mimpid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mhartid, mhartid, 0.U, MaskedRegMap.Unwritable),
    // Machine Trap Setup
    MaskedRegMap(Mstatus, mstatus, mstatus_wmask),
    MaskedRegMap(Misa, misa), // now MXL, EXT is not changeable
    // MaskedRegMap(Medeleg, medeleg, "hbbff".U(64.W)), TODO
    // MaskedRegMap(Mideleg, mideleg, "h222".U(64.W)),
    MaskedRegMap(Mie, mie),
    MaskedRegMap(Mtvec, mtvec),
    MaskedRegMap(Mcounteren, mcounteren),
    // Machine Trap Handling
    MaskedRegMap(Mscratch, mscratch),
    MaskedRegMap(Mepc, mepc),
    MaskedRegMap(Mcause, mcause),
    MaskedRegMap(Mtval, mtval),
    MaskedRegMap(Mip, mip.asUInt, 0.U, MaskedRegMap.Unwritable),
    // Machine Memory Protection
    // MaskedRegMap(Pmpcfg0, pmpcfg0),
    // MaskedRegMap(Pmpcfg1, pmpcfg1),
    // MaskedRegMap(Pmpcfg2, pmpcfg2),
    // MaskedRegMap(Pmpcfg3, pmpcfg3),
    // MaskedRegMap(PmpaddrBase + 0, pmpaddr0, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 1, pmpaddr1, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 2, pmpaddr2, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 3, pmpaddr3, pmpaddrWmask)

    // Debug/Trace Registers (shared with Debug Mode)
    MaskedRegMap(Tselect, tselect, 0.U, MaskedRegMap.Unwritable), // 用于通过 risc-v test
    MaskedRegMap(Tdata1, tdata1, 0.U, MaskedRegMap.Unwritable)
  ) //++ perfCntsLoMapping

  val mode = RegInit(Priv.m) // 当前特权模式

  // interrupts
  val mtip = io.ext_int.ti
  val meip = io.ext_int.ei
  val msip = io.ext_int.si
  mipWire.t.m := mtip
  mipWire.e.m := meip
  mipWire.s.m := msip
  val seip              = meip
  val mip_has_interrupt = WireInit(mip)
  mip_has_interrupt.e.s := mip.e.s | seip
  val interrupt_enable = Wire(UInt(INT_WID.W)) // 不用考虑ideleg
  interrupt_enable := Fill(
    INT_WID,
    (((mode === ModeM) && mstatus.asTypeOf(new Mstatus()).ie.m) || (mode < ModeM))
  )
  io.decoderUnit.interrupt := mie(11, 0) & mip_has_interrupt.asUInt & interrupt_enable.asUInt

  // 优先使用inst0的信息
  val exc_sel =
    (HasExcInt(io.memoryUnit.in.inst(0).ex)) || !(HasExcInt(io.memoryUnit.in.inst(1).ex))
  val mem_pc        = Mux(exc_sel, io.memoryUnit.in.inst(0).pc, io.memoryUnit.in.inst(1).pc)
  val mem_ex        = Mux(exc_sel, io.memoryUnit.in.inst(0).ex, io.memoryUnit.in.inst(1).ex)
  val mem_inst_info = Mux(exc_sel, io.memoryUnit.in.inst(0).info, io.memoryUnit.in.inst(1).info)
  val mem_inst      = mem_inst_info.inst
  val mem_valid     = mem_inst_info.valid
  val mem_addr      = mem_inst(31, 20)

  val has_exception = mem_ex.exception.asUInt.orR
  val has_interrupt = mem_ex.interrupt.asUInt.orR
  val has_exc_int   = has_exception || has_interrupt
  // 不带前缀的信号为exe阶段的信号
  val valid     = io.executeUnit.in.valid && !has_exc_int
  val info      = io.executeUnit.in.info
  val op        = io.executeUnit.in.info.op
  val fusel     = io.executeUnit.in.info.fusel
  val addr      = io.executeUnit.in.info.inst(31, 20)
  val src1      = io.executeUnit.in.src_info.src1_data
  val csri      = ZeroExtend(io.executeUnit.in.info.inst(19, 15), XLEN)
  val exe_stall = io.ctrl.exe_stall
  val mem_stall = io.ctrl.mem_stall
  wdata := LookupTree(
    op,
    List(
      CSROpType.wrt  -> src1,
      CSROpType.set  -> (rdata | src1),
      CSROpType.clr  -> (rdata & ~src1),
      CSROpType.wrti -> csri, //TODO: csri --> src2
      CSROpType.seti -> (rdata | csri),
      CSROpType.clri -> (rdata & ~csri)
    )
  )

  val satp_legal     = (wdata.asTypeOf(new Satp()).mode === 0.U) || (wdata.asTypeOf(new Satp()).mode === 8.U)
  val write          = (valid && op =/= CSROpType.jmp) && (addr =/= Satp.U || satp_legal)
  val only_read      = VecInit(CSROpType.set, CSROpType.seti, CSROpType.clr, CSROpType.clri).contains(op) && src1 === 0.U
  val illegal_mode   = mode < addr(9, 8)
  val illegal_write  = write && (addr(11, 10) === "b11".U) && !only_read
  val illegal_access = illegal_mode || illegal_write
  val wen            = write && !illegal_access

  MaskedRegMap.generate(mapping, addr, rdata, wen, wdata)
  val illegal_addr = MaskedRegMap.isIllegalAddr(mapping, addr)
  // Fix Mip/Sip write
  val fixMapping = Map(
    MaskedRegMap(Mip, mipReg.asUInt, mipFixMask)
    // MaskedRegMap(Sip, mipReg.asUInt, sipMask, MaskedRegMap.NoSideEffect, sipMask) //TODO
  )
  val rdataDummy = Wire(UInt(XLEN.W))
  MaskedRegMap.generate(fixMapping, addr, rdataDummy, wen, wdata)

  // CSR inst decode
  val ret = Wire(Bool())
  val isMret =
    mem_addr === privMret && mem_inst_info.op === CSROpType.jmp && mem_inst_info.fusel === FuType.csr && mem_valid
  val isSret =
    mem_addr === privSret && mem_inst_info.op === CSROpType.jmp && mem_inst_info.fusel === FuType.csr && mem_valid
  val isUret =
    mem_addr === privUret && mem_inst_info.op === CSROpType.jmp && mem_inst_info.fusel === FuType.csr && mem_valid
  ret := isMret || isSret || isUret

  val exceptionNO = ExcPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(mem_ex.exception(i), i.U, sum))
  val interruptNO = IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(mem_ex.interrupt(i), i.U, sum))
  val causeNO     = (has_interrupt << (XLEN - 1)) | Mux(has_interrupt, interruptNO, exceptionNO)

  val has_instrPageFault      = mem_ex.exception(instrPageFault)
  val has_loadPageFault       = mem_ex.exception(loadPageFault)
  val has_storePageFault      = mem_ex.exception(storePageFault)
  val has_loadAddrMisaligned  = mem_ex.exception(loadAddrMisaligned)
  val has_storeAddrMisaligned = mem_ex.exception(storeAddrMisaligned)
  val has_instrAddrMisaligned = mem_ex.exception(instrAddrMisaligned)

  val tval_wen = has_interrupt ||
    !(has_instrPageFault ||
      has_loadPageFault ||
      has_storePageFault ||
      has_instrAddrMisaligned ||
      has_loadAddrMisaligned ||
      has_storeAddrMisaligned)

  when(
    has_instrPageFault ||
      has_loadPageFault ||
      has_storePageFault ||
      has_instrAddrMisaligned ||
      has_loadAddrMisaligned ||
      has_storeAddrMisaligned
  ) {
    mtval := SignedExtend(mem_ex.tval, XLEN)
  }

  when(has_exc_int) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = WireInit(mstatus.asTypeOf(new Mstatus))
    mcause           := causeNO
    mepc             := SignedExtend(mem_pc, XLEN)
    mstatusNew.mpp   := mode
    mstatusNew.pie.m := mstatusOld.ie.m
    mstatusNew.ie.m  := false.B
    mode             := ModeM
    when(tval_wen) { mtval := 0.U }
    mstatus := mstatusNew.asUInt
  }

  val ret_target = Wire(UInt(VADDR_WID.W))
  ret_target := DontCare
  val trap_target = Wire(UInt(VADDR_WID.W))
  trap_target := mtvec(VADDR_WID - 1, 0)

  when(isMret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = WireInit(mstatus.asTypeOf(new Mstatus))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.m  := mstatusOld.pie.m
    mode             := mstatusOld.mpp
    mstatusNew.pie.m := true.B
    mstatusNew.mpp   := ModeU
    mstatus          := mstatusNew.asUInt
    lr               := false.B
    ret_target       := mepc(VADDR_WID - 1, 0)
  }

  io.decoderUnit.mode   := mode
  io.executeUnit.out.ex := io.executeUnit.in.ex
  io.executeUnit.out.ex.exception(illegalInstr) :=
    (illegal_addr || illegal_access) && write | io.executeUnit.in.ex.exception(illegalInstr)
  io.executeUnit.out.rdata   := rdata
  io.memoryUnit.out.flush    := has_exc_int || ret
  io.memoryUnit.out.flush_pc := Mux(has_exc_int, trap_target, ret_target)
}
