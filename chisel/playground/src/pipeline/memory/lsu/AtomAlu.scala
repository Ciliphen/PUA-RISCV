package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class AtomAlu extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val rdata = Input(UInt(XLEN.W)) // load data
      val src2  = Input(UInt(XLEN.W)) // reg data
      val info  = new Info()
    })
    val out = Output(new Bundle {
      val result = Output(UInt(XLEN.W))
    })
  })

  val src1    = io.in.rdata
  val src2    = io.in.src2
  val op      = io.in.info.op
  val is_sub  = !LSUOpType.isAdd(op)
  val sum     = (src1 +& (src2 ^ Fill(XLEN, is_sub))) + is_sub
  val oxr     = src1 ^ src2
  val sltu    = !sum(XLEN)
  val slt     = oxr(XLEN - 1) ^ sltu
  val is_word = !io.in.info.inst(12)

  val res = LookupTreeDefault(
    op(5, 0),
    sum,
    List(
      LSUOpType.amoswap -> src2,
      LSUOpType.amoadd  -> sum,
      LSUOpType.amoxor  -> oxr,
      LSUOpType.amoand  -> (src1 & src2),
      LSUOpType.amoor   -> (src1 | src2),
      LSUOpType.amomin  -> Mux(slt(0), src1, src2),
      LSUOpType.amomax  -> Mux(slt(0), src2, src1),
      LSUOpType.amominu -> Mux(sltu(0), src1, src2),
      LSUOpType.amomaxu -> Mux(sltu(0), src2, src1)
    )
  )

  io.out.result := Mux(is_word, SignedExtend(res(31, 0), 64), res(XLEN - 1, 0))
}
