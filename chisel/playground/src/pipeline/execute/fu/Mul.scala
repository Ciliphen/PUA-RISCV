package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class SignedMul extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val CLK = Input(Clock())
    val CE  = Input(Bool())
    val A   = Input(UInt((XLEN + 1).W))
    val B   = Input(UInt((XLEN + 1).W))

    val P = Output(UInt(((2 * XLEN) + 2).W))
  })
}

class Mul(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val src1        = Input(UInt((XLEN + 1).W))
    val src2        = Input(UInt((XLEN + 1).W))
    val en          = Input(Bool())
    val allow_to_go = Input(Bool())

    val ready  = Output(Bool())
    val result = Output(UInt((2 * XLEN).W))
  })

  if (cpuConfig.build) {
    // TODO:未经测试
    val signedMul = Module(new SignedMul()).io
    val cnt       = RegInit(0.U(log2Ceil(cpuConfig.mulClockNum + 1).W))

    cnt := MuxCase(
      cnt,
      Seq(
        (io.en && !io.ready) -> (cnt + 1.U),
        io.allow_to_go       -> 0.U
      )
    )

    signedMul.CLK := clock
    signedMul.CE  := io.en

    signedMul.A := io.src1
    signedMul.B := io.src2
    io.ready    := cnt >= cpuConfig.mulClockNum.U
    io.result   := signedMul.P((2 * XLEN) - 1, 0)
  } else {
    val cnt = RegInit(0.U(log2Ceil(cpuConfig.mulClockNum + 1).W))
    cnt := MuxCase(
      cnt,
      Seq(
        (io.en && !io.ready) -> (cnt + 1.U),
        io.allow_to_go       -> 0.U
      )
    )

    val signed = RegInit(0.U((2 * XLEN).W))
    when(io.en) {
      signed := (io.src1.asSInt * io.src2.asSInt).asUInt
    }
    io.result := signed
    io.ready  := cnt >= cpuConfig.mulClockNum.U
  }
}
