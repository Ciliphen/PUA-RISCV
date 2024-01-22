package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class ExeMemInfo extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new InstInfo()
  val rd_info  = new RdInfo()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class ExecuteUnitMemoryUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val inst = Vec(cpuConfig.commitNum, new ExeMemInfo())
}

class MemoryStage(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new Bundle {
      val allow_to_go = Bool()
      val clear       = Bool()
    })
    val executeUnit = Input(new ExecuteUnitMemoryUnit())
    val memoryUnit  = Output(new ExecuteUnitMemoryUnit())
  })
  val inst = Seq.fill(cpuConfig.commitNum)(RegInit(0.U.asTypeOf(new ExeMemInfo())))

  for (i <- 0 until (cpuConfig.commitNum)) {
    when(io.ctrl.clear) {
      inst(i) := 0.U.asTypeOf(new ExeMemInfo())
    }.elsewhen(io.ctrl.allow_to_go) {
      inst(i) := io.executeUnit.inst(i)
    }
  }

  io.memoryUnit.inst := inst
}
