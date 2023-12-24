package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class ExeMemInst extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new InstInfo()
  val rd_info  = new RdInfo()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class ExecuteUnitMemoryUnit(implicit val config: CpuConfig) extends Bundle {
  val inst0 = new ExeMemInst()
  val inst1 = new ExeMemInst()
}

class MemoryStage(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new Bundle {
      val allow_to_go = Bool()
      val clear       = Bool()
    })
    val executeUnit = Input(new ExecuteUnitMemoryUnit())
    val memoryUnit  = Output(new ExecuteUnitMemoryUnit())
  })
  val inst0 = RegInit(0.U.asTypeOf(new ExeMemInst()))
  val inst1 = RegInit(0.U.asTypeOf(new ExeMemInst()))

  when(io.ctrl.clear) {
    inst0 := 0.U.asTypeOf(new ExeMemInst())
    inst1 := 0.U.asTypeOf(new ExeMemInst())
  }.elsewhen(io.ctrl.allow_to_go) {
    inst0 := io.executeUnit.inst0
    inst1 := io.executeUnit.inst1
  }

  io.memoryUnit.inst0 := inst0
  io.memoryUnit.inst1 := inst1
}
