package cpu.pipeline.writeback

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class MemWbInst extends Bundle {
  val pc      = UInt(XLEN.W)
  val info    = new InstInfo()
  val rd_info = new RdInfo()
  val ex      = new ExceptionInfo()
}

class MemoryUnitWriteBackUnit extends Bundle {
  val inst0 = new MemWbInst()
  val inst1 = new MemWbInst()
}
class WriteBackStage(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new Bundle {
      val allow_to_go = Bool()
      val clear       = Bool()
    })
    val memoryUnit    = Input(new MemoryUnitWriteBackUnit())
    val writeBackUnit = Output(new MemoryUnitWriteBackUnit())
  })
  val inst0 = RegInit(0.U.asTypeOf(new MemWbInst()))
  val inst1 = RegInit(0.U.asTypeOf(new MemWbInst()))

  when(io.ctrl.clear(0)) {
    inst0 := 0.U.asTypeOf(new MemWbInst())
    inst1 := 0.U.asTypeOf(new MemWbInst())
  }.elsewhen(io.ctrl.allow_to_go) {
    inst0 := io.memoryUnit.inst0
    inst1 := io.memoryUnit.inst1
  }

  io.writeBackUnit.inst0 := inst0
  io.writeBackUnit.inst1 := inst1
}
