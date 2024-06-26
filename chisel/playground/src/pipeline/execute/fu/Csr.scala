package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class CsrMemoryUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val pc   = UInt(XLEN.W)
    val ex   = new ExceptionInfo()
    val info = new Info()
  })
  val out = Output(new Bundle {
    val flush  = Bool()
    val target = UInt(XLEN.W)
  })
}

class CsrExecuteUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val valid    = Bool()
    val pc       = UInt(XLEN.W)
    val info     = new Info()
    val src_info = new SrcInfo()
    val ex       = new ExceptionInfo()
  })
  val out = Output(new Bundle {
    val rdata  = UInt(XLEN.W)
    val ex     = new ExceptionInfo()
    val flush  = Bool()
    val target = UInt(XLEN.W)

    val debug = new CSR_DEBUG()
  })
}

class CsrTlb extends Bundle {
  val satp    = Output(UInt(XLEN.W))
  val mstatus = Output(UInt(XLEN.W))
  val imode   = Output(Priv())
  val dmode   = Output(Priv())
}

class Csr(implicit val cpuConfig: CpuConfig) extends Module with HasCSRConst {
  val io = IO(new Bundle {
    val ext_int     = Input(new ExtInterrupt())
    val executeUnit = new CsrExecuteUnit()
    val memoryUnit  = new CsrMemoryUnit()
    val tlb         = new CsrTlb()
  })

  // 目前的csr只支持64位
  require(XLEN == 64, "XLEN must be 64")

  /* CSR寄存器定义 */
  // Machine Information Registers
  val mvendorid = RegInit(UInt(XLEN.W), 0.U) // 厂商ID
  val marchid   = RegInit(UInt(XLEN.W), 0.U) // 架构ID
  val mimpid    = RegInit(UInt(XLEN.W), 0.U) // 实现ID
  val mhartid   = RegInit(UInt(XLEN.W), 0.U) // 硬件线程ID

  // Machine Trap Setup
  val mstatusWmask = "h00000000007e19aa".U(64.W)
  val mstatus_init = Wire(new Mstatus())
  mstatus_init     := 0.U.asTypeOf(new Mstatus())
  mstatus_init.sxl := 2.U
  mstatus_init.uxl := 2.U
  val mstatus   = RegInit(UInt(XLEN.W), mstatus_init.asUInt) // 状态寄存器
  val misa_init = Wire(new Misa())
  misa_init     := 0.U.asTypeOf(new Misa())
  misa_init.mxl := 2.U
  def getMisaExt(ext: Char): UInt = { 1.U << (ext.toInt - 'a'.toInt) }
  var extensions = List('i')
  if (cpuConfig.hasMExtension) { extensions = extensions :+ 'm' }
  if (cpuConfig.hasAExtension) { extensions = extensions :+ 'a' }
  if (cpuConfig.hasSMode) { extensions = extensions :+ 's' }
  if (cpuConfig.hasUMode) { extensions = extensions :+ 'u' }
  misa_init.extensions := extensions.foldLeft(0.U)((sum, i) => sum | getMisaExt(i))
  val misa       = RegInit(UInt(XLEN.W), misa_init.asUInt) // ISA寄存器
  val medeleg    = RegInit(UInt(XLEN.W), 0.U) // 异常代理寄存器
  val mideleg    = RegInit(UInt(XLEN.W), 0.U) // 中断代理寄存器
  val mie        = RegInit(UInt(XLEN.W), 0.U) // 中断使能寄存器
  val mtvec      = RegInit(UInt(XLEN.W), 0.U) // 中断向量基址寄存器
  val mcounteren = RegInit(UInt(XLEN.W), 0.U) // 计数器使能寄存器

  // Machine Trap Handling
  val mscratch   = RegInit(UInt(XLEN.W), 0.U) // 临时寄存器
  val mepc       = RegInit(UInt(XLEN.W), 0.U) // 异常程序计数器
  val mcause     = RegInit(UInt(XLEN.W), 0.U) // 异常原因寄存器
  val mtval      = RegInit(UInt(XLEN.W), 0.U) // 异常值寄存器
  val mipWire    = WireInit(0.U.asTypeOf(new Interrupt))
  val mipReg     = RegInit(UInt(XLEN.W), 0.U)
  val mipFixMask = "hAAA".U(64.W)
  val mip        = mipWire.asUInt | mipReg // 中断挂起寄存器

  // Machine Memory Protection
  val pmpcfg0  = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg1  = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg2  = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg3  = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr0 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr1 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr2 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr3 = RegInit(UInt(XLEN.W), 0.U)

  val pmpaddrWmask = "h3fffffff".U(64.W) // 32bit physical address

  // Machine Counter/Timers
  val mcycle = RegInit(UInt(XLEN.W), 0.U) // 时钟周期计数器
  mcycle := mcycle + 1.U
  val minstret = RegInit(UInt(XLEN.W), 0.U) // 指令计数器 // TODO:存在更新不准确的问题

  val commit_num = Wire(UInt(2.W))
  BoringUtils.addSink(commit_num, "commit")
  minstret := minstret + commit_num

  // Supervisor Trap Setup
  // sstatus 状态寄存器，源自mstatus
  val sstatusWmask = "h00000000000c0122".U(XLEN.W)
  val sstatusRmask = "h80000003000de762".U(XLEN.W)
  // sedeleg 异常代理寄存器，未实现
  // sideleg 中断代理寄存器，未实现
  // sie 中断使能寄存器，源自mie
  val sieMask    = "h222".U(64.W) & mideleg
  val stvec      = RegInit(UInt(XLEN.W), 0.U) // 中断向量基址寄存器
  val scounteren = RegInit(UInt(XLEN.W), 0.U) // 计数器使能寄存器

  // Supervisor Trap Handling
  val sscratch = RegInit(UInt(XLEN.W), 0.U) // 临时寄存器
  val sepc     = RegInit(UInt(XLEN.W), 0.U) // 异常程序计数器
  val scause   = RegInit(UInt(XLEN.W), 0.U) // 异常原因寄存器
  val stval    = RegInit(UInt(XLEN.W), 0.U) // 异常值寄存器
  // sip 中断挂起寄存器，源自mip
  val sipMask = "h222".U(64.W) & mideleg

  // Supervisor Protection and Translation
  val satp = RegInit(UInt(XLEN.W), 0.U) // 页表基址寄存器

  // Debug/Trace Registers (shared with Debug Mode)
  val tselect = RegInit(1.U(XLEN.W)) // 跟踪寄存器选择寄存器
  val tdata1  = RegInit(UInt(XLEN.W), 0.U) // 跟踪寄存器数据1寄存器

  val counterWmask = WireInit(UInt(XLEN.W), ((1.U << 0.U) | (1.U << 2.U)))

  val rdata = Wire(UInt(XLEN.W))
  val wdata = Wire(UInt(XLEN.W))

  // Atom LR/SC Control Bits
  val lr_wen   = Wire(Bool())
  val lr_wbit  = Wire(Bool())
  val lr_waddr = Wire(UInt(XLEN.W))
  BoringUtils.addSink(lr_wen, "lr_wen")
  BoringUtils.addSink(lr_wbit, "lr_wbit")
  BoringUtils.addSink(lr_waddr, "lr_waddr")

  val lr      = RegInit(Bool(), false.B)
  val lr_addr = RegInit(UInt(XLEN.W), 0.U)
  BoringUtils.addSource(lr, "lr")
  BoringUtils.addSource(lr_addr, "lr_addr")

  when(lr_wen) {
    lr      := lr_wbit
    lr_addr := lr_waddr
  }

  // Side Effect
  def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN - 2, 0))
    mstatusNew
  }

  // CSR reg map
  val mapping = Map(
    // User Counter/Timers
    MaskedRegMap(Cycle, mcycle),
    // Supervisor Trap Setup
    MaskedRegMap(Sstatus, mstatus, sstatusWmask, mstatusUpdateSideEffect, sstatusRmask),
    MaskedRegMap(Sie, mie, sieMask, MaskedRegMap.NoSideEffect, sieMask),
    MaskedRegMap(Stvec, stvec),
    MaskedRegMap(Scounteren, scounteren, counterWmask),
    // Supervisor Trap Handling
    MaskedRegMap(Sscratch, sscratch),
    MaskedRegMap(Sepc, sepc),
    MaskedRegMap(Scause, scause),
    MaskedRegMap(Stval, stval),
    MaskedRegMap(Sip, mip, sipMask, MaskedRegMap.Unwritable, sipMask),
    // Supervisor Protection and Translation
    MaskedRegMap(Satp, satp),
    // Machine Information Registers
    MaskedRegMap(Mvendorid, mvendorid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Marchid, marchid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mimpid, mimpid, 0.U, MaskedRegMap.Unwritable),
    MaskedRegMap(Mhartid, mhartid, 0.U, MaskedRegMap.Unwritable),
    // Machine Trap Setup
    MaskedRegMap(Mstatus, mstatus, mstatusWmask),
    MaskedRegMap(Misa, misa, 0.U, MaskedRegMap.Unwritable), // MXL，EXT目前不支持可变
    MaskedRegMap(Medeleg, medeleg, "hbbff".U(XLEN.W)),
    MaskedRegMap(Mideleg, mideleg, "h222".U(XLEN.W)),
    MaskedRegMap(Mie, mie),
    MaskedRegMap(Mtvec, mtvec),
    MaskedRegMap(Mcounteren, mcounteren, counterWmask),
    // Machine Trap Handling
    MaskedRegMap(Mscratch, mscratch),
    MaskedRegMap(Mepc, mepc),
    MaskedRegMap(Mcause, mcause),
    MaskedRegMap(Mtval, mtval),
    MaskedRegMap(Mip, mip, 0.U, MaskedRegMap.Unwritable),
    // Machine Memory Protection
    // MaskedRegMap(Pmpcfg0, pmpcfg0),
    // MaskedRegMap(Pmpcfg1, pmpcfg1),
    // MaskedRegMap(Pmpcfg2, pmpcfg2),
    // MaskedRegMap(Pmpcfg3, pmpcfg3),
    // MaskedRegMap(PmpaddrBase + 0, pmpaddr0, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 1, pmpaddr1, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 2, pmpaddr2, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 3, pmpaddr3, pmpaddrWmask)

    // Machine Counter/Timers
    MaskedRegMap(Mcycle, mcycle),
    MaskedRegMap(Minstret, minstret),
    // Debug/Trace Registers (shared with Debug Mode)
    MaskedRegMap(Tselect, tselect, 0.U, MaskedRegMap.Unwritable), // 用于通过 risc-v test
    MaskedRegMap(Tdata1, tdata1, 0.U, MaskedRegMap.Unwritable)
  )

  val mode = RegInit(Priv.m) // 当前特权模式

  // interrupts
  val mtip = io.ext_int.mti
  val meip = io.ext_int.mei
  val msip = io.ext_int.msi
  val seip = io.ext_int.sei
  mipWire.t.m := mtip
  mipWire.e.m := meip
  mipWire.s.m := msip
  mipWire.e.s := seip
  val mip_raise_interrupt = WireInit(mip.asTypeOf(new Interrupt()))

  val mstatusBundle = mstatus.asTypeOf(new Mstatus())

  val tvm = mstatusBundle.tvm
  BoringUtils.addSource(tvm, "tvm")

  val ideleg = (mideleg & mip_raise_interrupt.asUInt)
  def privilegedEnableDetect(x: Bool): Bool = Mux(
    x,
    ((mode === ModeS) && mstatusBundle.ie.s) || (mode < ModeS),
    ((mode === ModeM) && mstatusBundle.ie.m) || (mode < ModeM)
  )

  val interrupt_enable = Wire(Vec(INT_WID, Bool()))
  interrupt_enable.zip(ideleg.asBools).map { case (x, y) => x := privilegedEnableDetect(y) }
  val interrupt = Wire(UInt(INT_WID.W))
  interrupt := mie(INT_WID - 1, 0) & mip_raise_interrupt.asUInt & interrupt_enable.asUInt

  BoringUtils.addSource(interrupt, "interrupt")
  BoringUtils.addSource(mode, "mode")

  // 优先使用inst0的信息
  val mem_pc        = io.memoryUnit.in.pc
  val mem_ex        = io.memoryUnit.in.ex
  val mem_inst_info = io.memoryUnit.in.info
  val mem_inst      = mem_inst_info.inst
  val mem_valid     = mem_inst_info.valid
  val mem_addr      = mem_inst(31, 20)

  val raise_exception = mem_ex.exception.asUInt.orR && mem_valid
  val raise_interrupt = mem_ex.interrupt.asUInt.orR && mem_valid
  val raise_exc_int   = raise_exception || raise_interrupt

  val mem_flush = Wire(Bool())
  BoringUtils.addSink(mem_flush, "mem_flush")
  // 不带前缀的信号为exe阶段的信号
  val valid = io.executeUnit.in.valid && !mem_flush // mem发生flush时，清刷掉exe的信号
  val info  = io.executeUnit.in.info
  val op    = io.executeUnit.in.info.op
  val fusel = io.executeUnit.in.info.fusel
  val addr  = io.executeUnit.in.info.inst(31, 20)
  val src1  = io.executeUnit.in.src_info.src1_data
  val csri  = ZeroExtend(io.executeUnit.in.info.inst(19, 15), XLEN)
  wdata := LookupTree(
    op,
    List(
      CSROpType.csrrw  -> src1,
      CSROpType.csrrs  -> (rdata | src1),
      CSROpType.csrrc  -> (rdata & ~src1),
      CSROpType.csrrwi -> csri,
      CSROpType.csrrsi -> (rdata | csri),
      CSROpType.csrrci -> (rdata & ~csri)
    )
  )

  // 非法访问satp的情况：
  // 1. 写入satp的模式位不是0、8
  // 2. tvm为1时，任何读写satp的操作
  val satp_illegal = addr === Satp.U &&
    (!VecInit(0.U, 8.U).contains(wdata.asTypeOf(new Satp()).mode) || tvm && (addr === Satp.U))
  // 非法访问mstatus的情况：
  // 1. 写入mstatus的mpp不是M、S、U
  val mstatus_illegal = addr === Mstatus.U &&
    !VecInit(ModeM, ModeS, ModeU).contains(wdata.asTypeOf(new Mstatus).mpp)

  val write = (valid && CSROpType.isCSROp(op))
  val only_read =
    VecInit(CSROpType.csrrs, CSROpType.csrrsi, CSROpType.csrrc, CSROpType.csrrci).contains(op) && src1 === 0.U
  val illegal_mode  = mode < addr(9, 8)
  val illegal_write = write && (addr(11, 10) === "b11".U) && !only_read
  // 特殊位设置导致的非法访问
  val illegal_some   = satp_illegal || mstatus_illegal
  val illegal_access = illegal_mode || illegal_write || illegal_some
  val wen            = write && !illegal_access

  MaskedRegMap.generate(mapping, addr, rdata, wen, wdata)
  val illegal_addr = MaskedRegMap.isIllegalAddr(mapping, addr)
  val write_satp   = (addr === Satp.U) && wen
  val ipMapping = Map(
    MaskedRegMap(Mip, mipReg, mipFixMask),
    MaskedRegMap(Sip, mipReg, sipMask, MaskedRegMap.NoSideEffect, sipMask)
  )
  val rdataDummy = Wire(UInt(XLEN.W))
  MaskedRegMap.generate(ipMapping, addr, rdataDummy, wen, wdata)

  // CSR inst decode
  // ret指令在exe阶段执行
  val ret           = Wire(Bool())
  val isMretInst    = IsMret(info) && valid
  val isMretIllegal = mode < ModeM
  val isMret        = isMretInst && !isMretIllegal
  val isSretInst    = IsSret(info) && valid
  val isSretIllegal = mode < ModeS || mstatusBundle.tsr
  val isSret        = isSretInst && !isSretIllegal
  ret := isMret || isSret
  val retIllegal = isMretInst && isMretIllegal || isSretInst && isSretIllegal

  val exceptionNO = ExcPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(mem_ex.exception(i), i.U, sum))
  val interruptNO = IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(mem_ex.interrupt(i), i.U, sum))
  val causeNO     = (raise_interrupt << (XLEN - 1)) | Mux(raise_interrupt, interruptNO, exceptionNO)

  val deleg  = Mux(raise_interrupt, mideleg, medeleg)
  val delegS = (deleg(causeNO(log2Ceil(EXC_WID) - 1, 0))) && (mode < ModeM)

  val tval_wen = raise_interrupt ||
    !raise_exception

  when(raise_exception) {
    val tval = mem_ex.tval(exceptionNO)
    when(delegS) {
      stval := tval
    }.otherwise {
      mtval := tval
    }
  }

  when(raise_exc_int) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = WireInit(mstatus.asTypeOf(new Mstatus))

    when(delegS) {
      scause           := causeNO
      sepc             := SignedExtend(mem_pc, XLEN)
      mstatusNew.spp   := mode
      mstatusNew.pie.s := mstatusOld.ie.s
      mstatusNew.ie.s  := false.B
      mode             := ModeS
      when(tval_wen) { stval := 0.U }
    }.otherwise {
      mcause           := causeNO
      mepc             := SignedExtend(mem_pc, XLEN)
      mstatusNew.mpp   := mode
      mstatusNew.pie.m := mstatusOld.ie.m
      mstatusNew.ie.m  := false.B
      mode             := ModeM
      when(tval_wen) { mtval := 0.U }
    }
    mstatus := mstatusNew.asUInt
  }

  val ret_target = Wire(UInt(XLEN.W))
  ret_target := DontCare

  val trap_target = Wire(UInt(XLEN.W))
  val tvec        = Mux(delegS, stvec, mtvec)
  trap_target := (tvec(XLEN - 1, 2) << 2) + Mux(
    tvec(0) && raise_interrupt,
    (causeNO << 2),
    0.U
  )

  when(isMret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = WireInit(mstatus.asTypeOf(new Mstatus))
    when(mstatusOld.mpp =/= ModeM) {
      mstatusNew.mprv := false.B
    }
    mstatusNew.ie.m  := mstatusOld.pie.m
    mode             := mstatusOld.mpp
    mstatusNew.pie.m := true.B
    mstatusNew.mpp   := ModeU
    mstatus          := mstatusNew.asUInt
    lr               := false.B
    ret_target       := mepc
  }

  when(isSret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = WireInit(mstatus.asTypeOf(new Mstatus))
    when(mstatusOld.spp =/= ModeM) {
      mstatusNew.mprv := false.B
    }
    mstatusNew.ie.s  := mstatusOld.pie.s
    mode             := Cat(0.U(1.W), mstatusOld.spp)
    mstatusNew.pie.s := true.B
    mstatusNew.spp   := ModeU
    mstatus          := mstatusNew.asUInt
    lr               := false.B
    ret_target       := sepc
  }

  io.tlb.imode          := mode
  io.tlb.dmode          := Mux(mstatusBundle.mprv, mstatusBundle.mpp, mode)
  io.tlb.satp           := satp
  io.tlb.mstatus        := mstatus
  io.executeUnit.out.ex := io.executeUnit.in.ex
  io.executeUnit.out.ex.exception(illegalInst) :=
    (illegal_addr || illegal_access) && write |
      retIllegal |
      io.executeUnit.in.ex.exception(illegalInst)
  io.executeUnit.out.ex.tval(illegalInst) := io.executeUnit.in.info.inst
  io.executeUnit.out.rdata                := rdata
  io.executeUnit.out.flush                := write_satp || ret
  io.executeUnit.out.target               := Mux(ret, ret_target, io.executeUnit.in.pc + 4.U)
  io.memoryUnit.out.flush                 := raise_exc_int
  io.memoryUnit.out.target                := trap_target

  // for debug
  io.executeUnit.out.debug.mcycle    := mcycle
  io.executeUnit.out.debug.mip       := mip
  io.executeUnit.out.debug.minstret  := minstret
  io.executeUnit.out.debug.interrupt := DontCare
}
