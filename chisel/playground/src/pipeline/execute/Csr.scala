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
        val pc = UInt(PC_WID.W)
        val ex = new ExceptionInfo()
      }
    )
  })
  val out = Output(new Bundle {
    val flush    = Bool()
    val flush_pc = UInt(PC_WID.W)
  })
}

class CsrExecuteUnit(implicit val config: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val valid     = Bool()
    val inst_info = new InstInfo()
    val src_info  = new SrcInfo()
    val ex        = new ExceptionInfo()
  })
  val out = Output(new Bundle {
    val rdata = UInt(DATA_WID.W)
    val ex    = new ExceptionInfo()
  })
}

class CsrDecoderUnit extends Bundle {
  val priv_mode = Output(Priv())
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
  val cycle = RegInit(0.U(XLEN.W)) // 时钟周期计数器

  val instret = RegInit(0.U(XLEN.W)) // 指令计数器

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
  val mcycle     = cycle // 时钟周期计数器
  val minstret   = instret // 指令计数器

  val tselect = RegInit(1.U(XLEN.W)) // 跟踪寄存器选择寄存器
  val tdata1  = RegInit(0.U(XLEN.W)) // 跟踪寄存器数据1寄存器

  // Side Effect
  def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
    val mstatusOld = WireInit(mstatus.asTypeOf(new Mstatus))
    val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN - 2, 0))
    mstatusNew
  }

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
    // MaskedRegMap(Cycle, cycle),
    // MaskedRegMap(Time, time),
    // MaskedRegMap(Instret, instret),

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
    // // Supervisor Protection and Translation
    // MaskedRegMap(Satp, satp),
    // // Machine Information Registers
    // MaskedRegMap(Mvendorid, mvendorid, 0.U, MaskedRegMap.Unwritable),
    // MaskedRegMap(Marchid, marchid, 0.U, MaskedRegMap.Unwritable),
    // MaskedRegMap(Mimpid, mimpid, 0.U, MaskedRegMap.Unwritable),
    // MaskedRegMap(Mhartid, mhartid, 0.U, MaskedRegMap.Unwritable),
    // Machine Trap Setup
    // MaskedRegMap(Mstatus, mstatus, "hffffffffffffffee".U, (x=>{printf("mstatus write: %x time: %d\n", x, GTimer()); x})),
    MaskedRegMap(Mstatus, mstatus, "hffffffffffffffff".U(64.W), mstatusUpdateSideEffect),
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
    MaskedRegMap(Mip, mip.asUInt, 0.U, MaskedRegMap.Unwritable)
    // // Machine Memory Protection TODO
    // MaskedRegMap(Pmpcfg0, pmpcfg0),
    // MaskedRegMap(Pmpcfg1, pmpcfg1),
    // MaskedRegMap(Pmpcfg2, pmpcfg2),
    // MaskedRegMap(Pmpcfg3, pmpcfg3),
    // MaskedRegMap(PmpaddrBase + 0, pmpaddr0, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 1, pmpaddr1, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 2, pmpaddr2, pmpaddrWmask),
    // MaskedRegMap(PmpaddrBase + 3, pmpaddr3, pmpaddrWmask)
  ) //++ perfCntsLoMapping

  val priv_mode = RegInit(Priv.m) // 当前特权模式

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
    (((priv_mode === ModeM) && mstatus.asTypeOf(new Mstatus()).mie) || (priv_mode < ModeM))
  )
  io.decoderUnit.interrupt := mie(11, 0) & mip_has_interrupt.asUInt & interrupt_enable.asUInt

  // 优先使用inst0的信息
  val exc_sel = io.memoryUnit.in.inst(0).ex.exception.asUInt.orR ||
    !io.memoryUnit.in.inst(1).ex.exception.asUInt.orR
  val pc        = Mux(exc_sel, io.memoryUnit.in.inst(0).pc, io.memoryUnit.in.inst(1).pc)
  val exc       = Mux(exc_sel, io.memoryUnit.in.inst(0).ex, io.memoryUnit.in.inst(1).ex)
  val valid     = io.executeUnit.in.valid
  val op        = io.executeUnit.in.inst_info.op
  val fusel     = io.executeUnit.in.inst_info.fusel
  val addr      = io.executeUnit.in.inst_info.inst(31, 20)
  val rdata     = Wire(UInt(XLEN.W))
  val src1      = io.executeUnit.in.src_info.src1_data
  val csri      = ZeroExtend(io.executeUnit.in.inst_info.inst(19, 15), XLEN)
  val exe_stall = io.ctrl.exe_stall
  val mem_stall = io.ctrl.mem_stall
  val wdata = LookupTree(
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

  //val satp_legal  = (wdata.asTypeOf(new Satp()).mode === 0.U) || (wdata.asTypeOf(new Satp()).mode === 8.U)
  val wen            = (valid && op =/= CSROpType.jmp) //&& (addr =/= Satp.U || satp_legal)
  val illegal_mode   = priv_mode < addr(9, 8)
  val csr_ren        = (op === CSROpType.set || op === CSROpType.seti) && src1 === 0.U
  val illegal_write  = wen && (addr(11, 10) === "b11".U) && !csr_ren
  val illegal_access = illegal_mode || illegal_write

  MaskedRegMap.generate(mapping, addr, rdata, wen && !illegal_access, wdata)
  val illegal_addr = MaskedRegMap.isIllegalAddr(mapping, addr)
  // Fix Mip/Sip write
  val fixMapping = Map(
    MaskedRegMap(Mip, mipReg.asUInt, mipFixMask)
    // MaskedRegMap(Sip, mipReg.asUInt, sipMask, MaskedRegMap.NoSideEffect, sipMask) TODO
  )
  val rdataDummy = Wire(UInt(XLEN.W))
  MaskedRegMap.generate(fixMapping, addr, rdataDummy, wen && !illegal_access, wdata)

  // CSR inst decode
  val ret    = Wire(Bool())
  val isMret = addr === privMret && op === CSROpType.jmp
  val isSret = addr === privSret && op === CSROpType.jmp
  val isUret = addr === privUret && op === CSROpType.jmp
  ret := isMret || isSret || isUret

  io.executeUnit.out.ex                         := io.executeUnit.in.ex
  io.executeUnit.out.ex.exception(illegalInstr) := (illegal_addr || illegal_access) && wen
  io.executeUnit.out.rdata                      := rdata

  io.decoderUnit.priv_mode                      := priv_mode

  io.memoryUnit.out.flush := exc.exception.asUInt.orR || exc.interrupt.asUInt.orR
  io.memoryUnit.out.flush_pc := mtvec

}
