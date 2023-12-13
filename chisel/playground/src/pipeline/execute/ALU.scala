package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Alu extends Module {
  val io = IO(new Bundle {
    val info     = Input(new InstInfo())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(DATA_WID.W))
  })
  val op     = io.info.op
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data
  val is_sub = !ALUOpType.isAdd(op)
  val sum    = (src1 +& (src2 ^ Fill(XLEN, is_sub))) + is_sub
  val xor    = src1 ^ src2
  val sltu   = !sum(XLEN)
  val slt    = xor(XLEN - 1) ^ sltu

  val shsrc1 = MuxLookup(op, src1(XLEN - 1, 0))(
    List(
      ALUOpType.srlw -> ZeroExtend(src1(31, 0), XLEN),
      ALUOpType.sraw -> SignedExtend(src1(31, 0), XLEN)
    )
  )
  val shamt = Mux(ALUOpType.isWordOp(op), src2(4, 0), if (XLEN == 64) src2(5, 0) else src2(4, 0))
  val res = MuxLookup(op(3, 0), sum)(
    List(
      ALUOpType.sll  -> ((shsrc1 << shamt)(XLEN - 1, 0)),
      ALUOpType.slt  -> ZeroExtend(slt, XLEN),
      ALUOpType.sltu -> ZeroExtend(sltu, XLEN),
      ALUOpType.xor  -> xor,
      ALUOpType.srl  -> (shsrc1 >> shamt),
      ALUOpType.or   -> (src1 | src2),
      ALUOpType.and  -> (src1 & src2),
      ALUOpType.sra  -> ((shsrc1.asSInt >> shamt).asUInt)
    )
  )
  io.result := Mux(ALUOpType.isWordOp(op), SignedExtend(res(31, 0), 64), res)
}
