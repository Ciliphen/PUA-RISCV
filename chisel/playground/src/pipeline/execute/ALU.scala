package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class DivSignal extends Bundle {
  val ready  = Input(Bool())
  val result = Input(UInt(64.W))

  val en     = Output(Bool())
  val signed = Output(Bool())
}
class MultSignal extends Bundle {
  val ready  = Input(Bool())
  val result = Input(UInt(64.W))

  val en     = Output(Bool())
  val signed = Output(Bool())
}
class Alu extends Module {
  val io = IO(new Bundle {
    val inst_info = Input(new InstInfo())
    val src_info  = Input(new SrcInfo())
    val csr_rdata = Input(UInt(DATA_WID.W))
    val result    = Output(UInt(DATA_WID.W))
  })
  val op     = io.inst_info.op
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data
  val is_sub = !ALUOpType.isAdd(op)
  val sum    = (src1 +& (src2 ^ Fill(XLEN, is_sub))) + is_sub
  val xor    = src1 ^ src2
  val sltu   = !sum(XLEN)
  val slt    = xor(XLEN - 1) ^ sltu

  val shsrc1 = MuxLookup(op, src1(XLEN - 1, 0))(
    List(
      ALUOpType.srlw -> Util.zeroExtend(src1(31, 0), XLEN),
      ALUOpType.sraw -> Util.signedExtend(src1(31, 0), XLEN)
    )
  )
  val shamt = Mux(ALUOpType.isWordOp(op), src2(4, 0), if (XLEN == 64) src2(5, 0) else src2(4, 0))
  val res = MuxLookup(op(3, 0), sum)(
    List(
      ALUOpType.sll  -> ((shsrc1 << shamt)(XLEN - 1, 0)),
      ALUOpType.slt  -> Util.zeroExtend(slt, XLEN),
      ALUOpType.sltu -> Util.zeroExtend(sltu, XLEN),
      ALUOpType.xor  -> xor,
      ALUOpType.srl  -> (shsrc1 >> shamt),
      ALUOpType.or   -> (src1 | src2),
      ALUOpType.and  -> (src1 & src2),
      ALUOpType.sra  -> ((shsrc1.asSInt >> shamt).asUInt)
    )
  )
  io.result := Mux(ALUOpType.isWordOp(op), Util.signedExtend(res(31, 0), 64), res)
}
