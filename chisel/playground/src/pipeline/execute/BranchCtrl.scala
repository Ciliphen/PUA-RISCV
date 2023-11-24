package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class BranchCtrl extends Module {
  val io = IO(new Bundle {
    val in = new Bundle {
      val inst_info   = Input(new InstInfo())
      val src_info    = Input(new SrcInfo())
      val pred_branch = Input(Bool())
    }
    val out = new Bundle {
      val branch    = Output(Bool())
      val pred_fail = Output(Bool())
    }
  })
  val valid =
    io.in.inst_info.fusel === FuType.bru && ALUOpType.isBranch(io.in.inst_info.op) && io.in.inst_info.valid
  val src1   = io.in.src_info.src1_data
  val src2   = io.in.src_info.src2_data
  val op     = io.in.inst_info.op
  val is_sub = !ALUOpType.isAdd(op)
  val adder  = (src1 +& (src2 ^ Fill(XLEN, is_sub))) + is_sub
  val xor    = src1 ^ src2
  val sltu   = !adder(XLEN)
  val slt    = xor(XLEN - 1) ^ sltu
  val table = List(
    ALUOpType.getBranchType(ALUOpType.beq)  -> !xor.orR,
    ALUOpType.getBranchType(ALUOpType.blt)  -> slt,
    ALUOpType.getBranchType(ALUOpType.bltu) -> sltu
  )
  io.out.pred_fail := io.in.pred_branch =/= io.out.branch
  io.out.branch := (LookupTree(ALUOpType.getBranchType(op), table) ^
    ALUOpType.isBranchInvert(op)) & valid
}
