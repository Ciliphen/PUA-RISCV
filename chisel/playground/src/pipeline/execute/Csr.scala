package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.memory.CsrInfo
import cpu.CpuConfig
import cpu.pipeline.decoder.CsrDecoderUnit

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
    val inst_info  = Vec(config.fuNum, new InstInfo())
    val mtc0_wdata = UInt(DATA_WID.W)
  })
  val out = Output(new Bundle {
    val csr_rdata = Vec(config.fuNum, UInt(DATA_WID.W))
    val debug     = Output(new CsrInfo())
  })
}

class Csr(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ext_int = Input(UInt(EXT_INT_WID.W))
    val ctrl = Input(new Bundle {
      val exe_stall = Bool()
      val mem_stall = Bool()
    })
    val decoderUnit = Output(new CsrDecoderUnit())
    val executeUnit = new CsrExecuteUnit()
    val memoryUnit  = new CsrMemoryUnit()
  })
  // 优先使用inst0的信息
  val ex_sel    = io.memoryUnit.in.inst(0).ex.flush_req || !io.memoryUnit.in.inst(1).ex.flush_req
  val pc        = Mux(ex_sel, io.memoryUnit.in.inst(0).pc, io.memoryUnit.in.inst(1).pc)
  val ex        = Mux(ex_sel, io.memoryUnit.in.inst(0).ex, io.memoryUnit.in.inst(1).ex)
  val exe_op    = io.executeUnit.in.inst_info(0).op
  val exe_stall = io.ctrl.exe_stall
  val mem_stall = io.ctrl.mem_stall

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
  val mstatus   = RegInit(mstatus_init) // 状态寄存器
  val misa_init = Wire(new Misa())
  misa_init            := 0.U.asTypeOf(new Misa())
  misa_init.mxl        := 2.U
  misa_init.extensions := "h101100".U
  val misa       = RegInit(misa_init) // ISA寄存器
  val mie        = RegInit(0.U.asTypeOf(new Mie())) // 中断使能寄存器
  val mtvec      = RegInit(0.U.asTypeOf(new Mtvec())) // 中断向量基址寄存器
  val mcounteren = RegInit(0.U(XLEN.W)) // 计数器使能寄存器
  val mscratch   = RegInit(0.U(XLEN.W)) // 临时寄存器
  val mepc       = RegInit(0.U(XLEN.W)) // 异常程序计数器
  val mcause     = RegInit(0.U.asTypeOf(new Mcause())) // 异常原因寄存器
  val mtval      = RegInit(0.U(XLEN.W)) // 异常值寄存器
  val mip        = RegInit(0.U.asTypeOf(new Mip())) // 中断挂起寄存器
  val mcycle     = cycle // 时钟周期计数器
  val minstret   = instret // 指令计数器

  val tselect = RegInit(1.U(XLEN.W)) // 跟踪寄存器选择寄存器
  val tdata1  = RegInit(0.U(XLEN.W)) // 跟踪寄存器数据1寄存器
}
