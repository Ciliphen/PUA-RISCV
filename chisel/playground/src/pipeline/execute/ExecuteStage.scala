package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.CpuConfig

class IdExeData extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new Info()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class JumpBranchData extends Bundle {
  val jump_register    = Bool()
  val branch_inst      = Bool()
  val pred_branch      = Bool()
  val branch_target    = UInt(XLEN.W)
  val update_pht_index = UInt(XLEN.W)
}

class DecodeUnitExecuteUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val inst             = Vec(cpuConfig.commitNum, new IdExeData())
  val jump_branch_info = new JumpBranchData()
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

  val inst = Seq.fill(cpuConfig.commitNum)(RegInit(0.U.asTypeOf(new IdExeData())))
  val jump_branch_info = RegEnable(io.decodeUnit.jump_branch_info, io.ctrl.allow_to_go(0))

  for (i <- 0 until (cpuConfig.commitNum)) {
    when(io.ctrl.clear(i)) {
      inst(i).info.valid   := false.B
      inst(i).info.reg_wen := false.B
    }.elsewhen(io.ctrl.allow_to_go(i)) {
      inst(i) := io.decodeUnit.inst(i)
    }
  }

  io.executeUnit.inst             := inst
  io.executeUnit.jump_branch_info := jump_branch_info
}
