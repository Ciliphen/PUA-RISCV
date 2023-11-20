package cpu.pipeline.fetch

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu._
import cpu.pipeline.decoder.Src12Read
import cpu.defines.ALUOpType
import cpu.defines.FuOpType

class ExecuteUnitBranchPredictor extends Bundle {
  val bpuConfig        = new BranchPredictorConfig()
  val pc               = Output(UInt(PC_WID.W))
  val update_pht_index = Output(UInt(bpuConfig.phtDepth.W))
  val branch_inst      = Output(Bool())
  val branch           = Output(Bool())
}

class BranchPredictorIO(implicit config: CpuConfig) extends Bundle {
  val bpuConfig = new BranchPredictorConfig()
  val decoder = new Bundle {
    val inst      = Input(UInt(INST_WID.W))
    val op        = Input(FuOpType())
    val ena       = Input(Bool())
    val pc        = Input(UInt(PC_WID.W))
    val pc_plus4  = Input(UInt(PC_WID.W))
    val pht_index = Input(UInt(bpuConfig.phtDepth.W))

    val rs1 = Input(UInt(REG_ADDR_WID.W))
    val rs2 = Input(UInt(REG_ADDR_WID.W))

    val branch_inst      = Output(Bool())
    val pred_branch      = Output(Bool())
    val branch_target    = Output(UInt(PC_WID.W))
    val update_pht_index = Output(UInt(bpuConfig.phtDepth.W))
  }

  val instBuffer = new Bundle {
    val pc        = Input(Vec(config.instFetchNum, UInt(PC_WID.W)))
    val pht_index = Output(Vec(config.instFetchNum, UInt(bpuConfig.phtDepth.W)))
  }

  val execute = Flipped(new ExecuteUnitBranchPredictor())
}

class BranchPredictorUnit(implicit config: CpuConfig) extends Module {
  val io = IO(new BranchPredictorIO())

  if (config.branchPredictor == "adaptive") {
    val adaptive_predictor = Module(new AdaptiveTwoLevelPredictor())
    io <> adaptive_predictor.io
  }

  if (config.branchPredictor == "global") {
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
  config: CpuConfig)
    extends Module {
  val io = IO(new BranchPredictorIO())

  val strongly_not_taken :: weakly_not_taken :: weakly_taken :: strongly_taken :: Nil = Enum(4)

  io.decoder.branch_inst := ALUOpType.isBru(io.decoder.op) && ALUOpType.isBranch(io.decoder.op)
  io.decoder.branch_target := io.decoder.pc_plus4 + Cat(
    Fill(14, io.decoder.inst(15)),
    io.decoder.inst(15, 0),
    0.U(2.W)
  )
  // 局部预测模式

  val bht       = RegInit(VecInit(Seq.fill(1 << BHT_DEPTH)(0.U(PHT_DEPTH.W))))
  val pht       = RegInit(VecInit(Seq.fill(1 << PHT_DEPTH)(strongly_taken)))
  val bht_index = io.decoder.pc(1 + BHT_DEPTH, 2)
  val pht_index = bht(bht_index)

  io.decoder.pred_branch :=
    io.decoder.ena && io.decoder.branch_inst && (pht(pht_index) === weakly_taken || pht(pht_index) === strongly_taken)
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
  config: CpuConfig)
    extends Module {
  val bpuConfig = new BranchPredictorConfig()
  val PHT_DEPTH = bpuConfig.phtDepth
  val BHT_DEPTH = bpuConfig.bhtDepth
  val io        = IO(new BranchPredictorIO())

  val strongly_not_taken :: weakly_not_taken :: weakly_taken :: strongly_taken :: Nil = Enum(4)

  io.decoder.branch_inst := ALUOpType.isBru(io.decoder.op) && ALUOpType.isBranch(io.decoder.op)
  io.decoder.branch_target := io.decoder.pc_plus4 + Cat(
    Fill(14, io.decoder.inst(15)),
    io.decoder.inst(15, 0),
    0.U(2.W)
  )

  val bht       = RegInit(VecInit(Seq.fill(1 << BHT_DEPTH)(0.U(PHT_DEPTH.W))))
  val pht       = RegInit(VecInit(Seq.fill(1 << PHT_DEPTH)(strongly_taken)))
  val pht_index = io.decoder.pht_index

  for (i <- 0 until config.instFetchNum) {
    io.instBuffer.pht_index(i) := bht(io.instBuffer.pc(i)(1 + BHT_DEPTH, 2))
  }

  io.decoder.pred_branch :=
    io.decoder.ena && io.decoder.branch_inst && (pht(pht_index) === weakly_taken || pht(pht_index) === strongly_taken)
  io.decoder.update_pht_index := bht(io.decoder.pc(1 + BHT_DEPTH, 2))

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
