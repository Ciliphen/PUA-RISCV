package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}

class IdExeInst0 extends Bundle {
  val cpuConfig = new BranchPredictorConfig()
  val pc        = UInt(XLEN.W)
  val info      = new InstInfo()
  val src_info  = new SrcInfo()
  val ex        = new ExceptionInfo()
  val jb_info = new Bundle {
    // jump ctrl
    val jump_regiser = Bool()
    // bpu
    val branch_inst      = Bool()
    val pred_branch      = Bool()
    val branch_target    = UInt(XLEN.W)
    val update_pht_index = UInt(cpuConfig.phtDepth.W)
  }
}

class IdExeInst1 extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new InstInfo()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class DecodeUnitExecuteUnit extends Bundle {
  val inst0 = new IdExeInst0()
  val inst1 = new IdExeInst1()
}

class ExecuteStage(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new Bundle {
      val allow_to_go = Vec(cpuConfig.decoderNum, Bool())
      val clear       = Vec(cpuConfig.decoderNum, Bool())
    })
    val decodeUnit  = Input(new DecodeUnitExecuteUnit())
    val executeUnit = Output(new DecodeUnitExecuteUnit())
  })

  val inst0 = RegInit(0.U.asTypeOf(new IdExeInst0()))
  val inst1 = RegInit(0.U.asTypeOf(new IdExeInst1()))

  when(io.ctrl.clear(0)) {
    inst0 := 0.U.asTypeOf(new IdExeInst0())
  }.elsewhen(io.ctrl.allow_to_go(0)) {
    inst0 := io.decodeUnit.inst0
  }

  when(io.ctrl.clear(1)) {
    inst1 := 0.U.asTypeOf(new IdExeInst1())
  }.elsewhen(io.ctrl.allow_to_go(1)) {
    inst1 := io.decodeUnit.inst1
  }

  io.executeUnit.inst0 := inst0
  io.executeUnit.inst1 := inst1
}
