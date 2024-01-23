package cpu.pipeline.fetch

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.pipeline.decode.DecodeUnitInstFifo

class IfIdData extends Bundle {
  val bpuConfig    = new BranchPredictorConfig()
  val inst         = UInt(XLEN.W)
  val pht_index    = UInt(bpuConfig.phtDepth.W)
  val access_fault = Bool()
  val page_fault   = Bool()
  val pc           = UInt(XLEN.W)
}

class InstFifo(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val do_flush = Input(Bool())

    val wen   = Input(Vec(cpuConfig.instFetchNum, Bool()))
    val write = Input(Vec(cpuConfig.instFetchNum, new IfIdData()))
    val full  = Output(Bool())

    val decoderUint = Flipped(new DecodeUnitInstFifo())
  })
  // fifo buffer
  val buffer = RegInit(VecInit(Seq.fill(cpuConfig.instFifoDepth)(0.U.asTypeOf(new IfIdData()))))

  // fifo ptr
  val enq_ptr = RegInit(0.U(log2Ceil(cpuConfig.instFifoDepth).W))
  val deq_ptr = RegInit(0.U(log2Ceil(cpuConfig.instFifoDepth).W))
  val count   = RegInit(0.U(log2Ceil(cpuConfig.instFifoDepth).W))

  // config.instFifoDepth - 1 is the last element, config.instFifoDepth - 2 is the last second element
  // the second last element's valid decide whether the fifo is full

  val full         = count >= (cpuConfig.instFifoDepth - cpuConfig.instFetchNum).U
  val empty        = count === 0.U
  val almost_empty = count === 1.U

  io.full                          := full
  io.decoderUint.info.empty        := empty
  io.decoderUint.info.almost_empty := almost_empty

  // * deq * //
  io.decoderUint.inst(0) := MuxCase(
    buffer(deq_ptr),
    Seq(
      empty        -> 0.U.asTypeOf(new IfIdData()),
      almost_empty -> buffer(deq_ptr)
    )
  )

  io.decoderUint.inst(1) := MuxCase(
    buffer(deq_ptr + 1.U),
    Seq(
      (empty || almost_empty) -> 0.U.asTypeOf(new IfIdData())
    )
  )

  val deq_num = MuxCase(
    0.U,
    Seq(
      (empty)                       -> 0.U,
      io.decoderUint.allow_to_go(1) -> 2.U,
      io.decoderUint.allow_to_go(0) -> 1.U
    )
  )

  when(io.do_flush) {
    deq_ptr := 0.U
  }.otherwise {
    deq_ptr := deq_ptr + deq_num
  }

  // * enq * //
  val enq_num = Wire(UInt(log2Ceil(cpuConfig.instFetchNum + 1).W))

  for (i <- 0 until cpuConfig.instFetchNum) {
    when(io.wen(i)) {
      buffer(enq_ptr + i.U) := io.write(i)
    }
  }

  when(io.do_flush) {
    enq_ptr := 0.U
  }.otherwise {
    enq_ptr := enq_ptr + enq_num
  }

  enq_num := 0.U
  for (i <- 0 until cpuConfig.instFetchNum) {
    when(io.wen(i)) {
      enq_num := (i + 1).U
    }
  }

  count := Mux(io.do_flush, 0.U, count + enq_num + cpuConfig.instFifoDepth.U - deq_num)
}
