package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Mdu(implicit cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val info        = Input(new InstInfo())
    val src_info    = Input(new SrcInfo())
    val allow_to_go = Input(Bool())

    val ready  = Output(Bool())
    val result = Output(UInt(XLEN.W))
  })

  val mul = Module(new Mul()).io
  val div = Module(new Div()).io

  val valid  = io.info.valid
  val op     = io.info.op
  val is_div = MDUOpType.isDiv(op)
  val is_w   = MDUOpType.isW(op)

  val src1 = io.src_info.src1_data
  val src2 = io.src_info.src2_data

  val zeroExtend   = ZeroExtend(_: UInt, XLEN + 1)
  val signedExtend = SignedExtend(_: UInt, XLEN + 1)
  val srcMulConvertTable = Seq(
    MDUOpType.mul    -> (zeroExtend, zeroExtend),
    MDUOpType.mulh   -> (signedExtend, signedExtend),
    MDUOpType.mulhsu -> (signedExtend, zeroExtend),
    MDUOpType.mulhu  -> (zeroExtend, zeroExtend)
  )

  mul.src1        := LookupTree(op(1, 0), srcMulConvertTable.map(p => (p._1, p._2._1(src1))))
  mul.src2        := LookupTree(op(1, 0), srcMulConvertTable.map(p => (p._1, p._2._2(src2))))
  mul.start       := valid && !is_div
  mul.allow_to_go := io.allow_to_go

  val srcDivConvertFunc = (x: UInt) =>
    Mux(is_w, Mux(MDUOpType.isDivSign(op), SignedExtend(x(31, 0), XLEN), ZeroExtend(x(31, 0), XLEN)), x)
  div.src1        := srcDivConvertFunc(src1)
  div.src2        := srcDivConvertFunc(src2)
  div.signed      := MDUOpType.isDivSign(op)
  div.start       := valid && is_div
  div.allow_to_go := io.allow_to_go

  val mul_result = Mux(op(1, 0) === MDUOpType.mul, mul.result(XLEN - 1, 0), mul.result(2 * XLEN - 1, XLEN))
  val div_result = Mux(op(1), div.result(2 * XLEN - 1, XLEN), div.result(XLEN - 1, 0))
  val result     = Mux(is_div, div_result, mul_result)

  io.ready  := Mux(is_div, div.ready, mul.ready)
  io.result := Mux(is_w, SignedExtend(result(31, 0), XLEN), result)
}
