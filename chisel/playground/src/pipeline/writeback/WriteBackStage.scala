package cpu.pipeline.writeback

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class MemWbData extends Bundle {
  val pc      = UInt(XLEN.W)
  val info    = new Info()
  val rd_info = new RdInfo()
  val ex      = new ExceptionInfo()
}

class MemoryUnitWriteBackUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val inst = Vec(cpuConfig.commitNum, new MemWbData())
}
class WriteBackStage(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new Bundle {
      val allow_to_go = Bool()
      val clear       = Bool()
    })
    val memoryUnit    = Input(new MemoryUnitWriteBackUnit())
    val writeBackUnit = Output(new MemoryUnitWriteBackUnit())
  })

  val inst = Seq.fill(cpuConfig.commitNum)(RegInit(0.U.asTypeOf(new MemWbData())))

  for (i <- 0 until (cpuConfig.commitNum)) {
    when(io.ctrl.clear) {
      inst(i) := 0.U.asTypeOf(new MemWbData())
    }.elsewhen(io.ctrl.allow_to_go) {
      inst(i) := io.memoryUnit.inst(i)
    }
  }

  io.writeBackUnit.inst := inst
}
