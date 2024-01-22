package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.CpuConfig

class IdExeInfo extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new InstInfo()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class JumpBranchInfo extends Bundle {
  val jump_regiser     = Bool()
  val branch_inst      = Bool()
  val pred_branch      = Bool()
  val branch_target    = UInt(XLEN.W)
  val update_pht_index = UInt(XLEN.W)
}

class DecodeUnitExecuteUnit(implicit cpuConfig: CpuConfig) extends Bundle {
  val inst             = Vec(cpuConfig.commitNum, new IdExeInfo())
  val jump_branch_info = new JumpBranchInfo()
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

  val inst             = Seq.fill(cpuConfig.commitNum)(RegInit(0.U.asTypeOf(new IdExeInfo())))
  val jump_branch_info = RegInit(0.U.asTypeOf(new JumpBranchInfo()))

  for (i <- 0 until (cpuConfig.commitNum)) {
    when(io.ctrl.clear(i)) {
      inst(i) := 0.U.asTypeOf(new IdExeInfo())
    }.elsewhen(io.ctrl.allow_to_go(i)) {
      inst(i) := io.decodeUnit.inst(i)
    }
  }

  // inst0携带分支预测相关信息
  when(io.ctrl.clear(0)) {
    jump_branch_info := 0.U.asTypeOf(new JumpBranchInfo())
  }.elsewhen(io.ctrl.allow_to_go(0)) {
    jump_branch_info := io.decodeUnit.jump_branch_info
  }

  io.executeUnit.inst             := inst
  io.executeUnit.jump_branch_info := jump_branch_info
}
