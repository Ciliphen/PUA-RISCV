package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class ExeMemData extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new Info()
  val rd_info  = new RdInfo()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class ExecuteUnitMemoryUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val inst  = Vec(cpuConfig.commitNum, new ExeMemData())
  val debug = new CSR_DEBUG()
}

class MemoryStage(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new CtrlSignal())
    val executeUnit = Input(new ExecuteUnitMemoryUnit())
    val memoryUnit  = Output(new ExecuteUnitMemoryUnit())
  })
  val inst  = Seq.fill(cpuConfig.commitNum)(RegInit(0.U.asTypeOf(new ExeMemData())))
  val debug = RegInit(0.U.asTypeOf(new CSR_DEBUG()))

  for (i <- 0 until (cpuConfig.commitNum)) {
    when(io.ctrl.do_flush) {
      inst(i).info.valid := false.B
      inst(i).info.reg_wen := false.B
    }.elsewhen(io.ctrl.allow_to_go) {
      inst(i) := io.executeUnit.inst(i)
      debug   := io.executeUnit.debug
    }
  }

  io.memoryUnit.inst  := inst
  io.memoryUnit.debug := debug
}
