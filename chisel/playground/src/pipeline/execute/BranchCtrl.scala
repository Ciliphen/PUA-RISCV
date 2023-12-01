package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class BranchCtrl extends Module {
  val io = IO(new Bundle {
    val in = new Bundle {
      val pc            = Input(UInt(PC_WID.W))
      val info          = Input(new InstInfo())
      val src_info      = Input(new SrcInfo())
      val pred_branch   = Input(Bool())
      val jump_regiser  = Input(Bool())
      val branch_target = Input(UInt(PC_WID.W))
    }
    val out = new Bundle {
      val branch    = Output(Bool())
      val pred_fail = Output(Bool())
      val target    = Output(UInt(PC_WID.W))
    }
  })
  val valid =
    io.in.info.fusel === FuType.bru && ALUOpType.isBranch(io.in.info.op) && io.in.info.valid
  val src1   = io.in.src_info.src1_data
  val src2   = io.in.src_info.src2_data
  val op     = io.in.info.op
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
  io.out.target := MuxCase(
    io.in.pc + 4.U, // 默认顺序运行吧
    Seq(
      (io.out.pred_fail && io.out.branch)  -> io.in.branch_target,
      (io.out.pred_fail && !io.out.branch) -> (io.in.pc + 4.U),
      (io.in.jump_regiser)                 -> ((src1 + src2) & ~1.U(XLEN.W))
    )
  )
}
