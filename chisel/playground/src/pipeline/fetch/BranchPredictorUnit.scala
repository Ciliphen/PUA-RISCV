package cpu.pipeline.fetch

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu._
import cpu.pipeline.decode.Src12Read
import cpu.defines.BRUOpType
import cpu.defines.FuOpType
import cpu.defines.FuType
import cpu.defines.SignedExtend
import cpu.pipeline.decode.DecoderBranchPredictorUnit
import pipeline.decode.{DecoderBranchPredictorUnit, Src12Read}

class ExecuteUnitBranchPredictor extends Bundle {
  val bpuConfig        = new BranchPredictorConfig()
  val pc               = Output(UInt(XLEN.W))
  val update_pht_index = Output(UInt(bpuConfig.phtDepth.W))
  val branch_inst      = Output(Bool())
  val branch           = Output(Bool())
}

class BranchPredictorIO(implicit cpuConfig: CpuConfig) extends Bundle {
  val bpuConfig = new BranchPredictorConfig()
  val decode    = Flipped(new DecoderBranchPredictorUnit())

  val instBuffer = new Bundle {
    val pc        = Input(Vec(cpuConfig.instFetchNum, UInt(XLEN.W)))
    val pht_index = Output(Vec(cpuConfig.instFetchNum, UInt(bpuConfig.phtDepth.W)))
  }

  val execute = Flipped(new ExecuteUnitBranchPredictor())
}

class BranchPredictorUnit(implicit cpuConfig: CpuConfig) extends Module {
  val io = IO(new BranchPredictorIO())

  if (cpuConfig.branchPredictor == "adaptive") {
    val adaptive_predictor = Module(new AdaptiveTwoLevelPredictor())
    io <> adaptive_predictor.io
  }

  if (cpuConfig.branchPredictor == "global") {
    val global_predictor = Module(new GlobalBranchPredictor())
    io <> global_predictor.io
  }
}

class GlobalBranchPredictor(
  GHR_DEPTH:   Int = 4, // 可以记录的历史记录个数
  PC_HASH_WID: Int = 4, // 取得PC的宽度
  PHT_DEPTH:   Int = 6, // 可以记录的历史个数
  BHT_DEPTH:   Int = 4 // 取得PC的宽度
)(
  implicit
  cpuConfig: CpuConfig)
    extends Module {
  val io = IO(new BranchPredictorIO())

  val strongly_not_taken :: weakly_not_taken :: weakly_taken :: strongly_taken :: Nil = Enum(4)

  val imm = io.decode.info.imm

  io.decode.branch_inst := io.decode.info.valid &&
    FuType.bru === io.decode.info.fusel && BRUOpType.isBranch(io.decode.info.op)
  io.decode.target := io.decode.pc + imm
  // 局部预测模式

  val bht       = RegInit(VecInit(Seq.fill(1 << BHT_DEPTH)(0.U(PHT_DEPTH.W))))
  val pht       = RegInit(VecInit(Seq.fill(1 << PHT_DEPTH)(strongly_taken)))
  val bht_index = io.decode.pc(1 + BHT_DEPTH, 2)
  val pht_index = bht(bht_index)

  io.decode.branch :=
    io.decode.branch_inst && (pht(pht_index) === weakly_taken || pht(pht_index) === strongly_taken)
  val update_bht_index = io.execute.pc(1 + BHT_DEPTH, 2)
  val update_pht_index = bht(update_bht_index)

  when(io.execute.branch_inst) {
    bht(update_bht_index) := Cat(bht(update_bht_index)(PHT_DEPTH - 2, 0), io.execute.branch)
    switch(pht(update_pht_index)) {
      is(strongly_not_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, weakly_not_taken, strongly_not_taken)
      }
      is(weakly_not_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, weakly_taken, strongly_not_taken)
      }
      is(weakly_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, strongly_taken, weakly_not_taken)
      }
      is(strongly_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, strongly_taken, weakly_taken)
      }
    }
  }

}

class AdaptiveTwoLevelPredictor(
)(
  implicit
  cpuConfig: CpuConfig)
    extends Module {
  val bpuConfig = new BranchPredictorConfig()
  val PHT_DEPTH = bpuConfig.phtDepth
  val BHT_DEPTH = bpuConfig.bhtDepth
  val io        = IO(new BranchPredictorIO())

  val strongly_not_taken :: weakly_not_taken :: weakly_taken :: strongly_taken :: Nil = Enum(4)

  val imm = io.decode.info.imm

  io.decode.branch_inst := io.decode.info.valid &&
    FuType.bru === io.decode.info.fusel && BRUOpType.isBranch(io.decode.info.op)
  io.decode.target := io.decode.pc + imm

  val bht       = RegInit(VecInit(Seq.fill(1 << BHT_DEPTH)(0.U(PHT_DEPTH.W))))
  val pht       = RegInit(VecInit(Seq.fill(1 << PHT_DEPTH)(strongly_taken)))
  val pht_index = io.decode.pht_index

  for (i <- 0 until cpuConfig.instFetchNum) {
    io.instBuffer.pht_index(i) := bht(io.instBuffer.pc(i)(1 + BHT_DEPTH, 2))
  }

  io.decode.branch :=
    io.decode.branch_inst && (pht(pht_index) === weakly_taken || pht(pht_index) === strongly_taken)
  io.decode.update_pht_index := bht(io.decode.pc(1 + BHT_DEPTH, 2))

  val update_bht_index = io.execute.pc(1 + BHT_DEPTH, 2)
  val update_pht_index = io.execute.update_pht_index

  when(io.execute.branch_inst) {
    bht(update_bht_index) := Cat(bht(update_bht_index)(PHT_DEPTH - 2, 0), io.execute.branch)
    switch(pht(update_pht_index)) {
      is(strongly_not_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, weakly_not_taken, strongly_not_taken)
      }
      is(weakly_not_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, weakly_taken, strongly_not_taken)
      }
      is(weakly_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, strongly_taken, weakly_not_taken)
      }
      is(strongly_taken) {
        pht(update_pht_index) := Mux(io.execute.branch, strongly_taken, weakly_taken)
      }
    }
  }

}
