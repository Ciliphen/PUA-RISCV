package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._

class ExeAccessMemCtrl(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val mem = new Bundle {
      val out = Output(new Bundle {
        val en        = Bool()
        val ren       = Bool()
        val wen       = Bool()
        val info = new InstInfo()
        val addr      = UInt(DATA_ADDR_WID.W)
        val wdata     = UInt(DATA_WID.W)
      })
    }

    val inst = Vec(
      config.fuNum,
      new Bundle {
        val info = Input(new InstInfo())
        val src_info  = Input(new SrcInfo())
        val ex = new Bundle {
          val in  = Input(new ExceptionInfo())
          val out = Output(new ExceptionInfo())
        }
        val mem_sel = Output(Bool())
      }
    )
  })
  io.mem.out.en := io.inst.map(_.mem_sel).reduce(_ || _)
  io.mem.out.ren := io.inst(0).mem_sel && LSUOpType.isLoad(io.inst(0).info.op) ||
    io.inst(1).mem_sel && LSUOpType.isLoad(io.inst(1).info.op)
  io.mem.out.wen := io.inst(0).mem_sel && LSUOpType.isStore(io.inst(0).info.op) ||
    io.inst(1).mem_sel && LSUOpType.isStore(io.inst(1).info.op)
  io.mem.out.info := Mux1H(
    Seq(
      (io.inst(0).info.fusel === FuType.lsu) -> io.inst(0).info,
      (io.inst(1).info.fusel === FuType.lsu) -> io.inst(1).info
    )
  )
  val mem_addr = Wire(Vec(config.fuNum, UInt(DATA_ADDR_WID.W)))
  mem_addr(0) := io.inst(0).src_info.src1_data + io.inst(0).info.imm
  mem_addr(1) := io.inst(1).src_info.src1_data + io.inst(1).info.imm
  io.mem.out.addr := Mux1H(
    Seq(
      (io.inst(0).info.fusel === FuType.lsu) -> mem_addr(0),
      (io.inst(1).info.fusel === FuType.lsu) -> mem_addr(1)
    )
  )
  io.mem.out.wdata := Mux1H(
    Seq(
      (io.inst(0).info.fusel === FuType.lsu) ->
        io.inst(0).src_info.src2_data,
      (io.inst(1).info.fusel === FuType.lsu) ->
        io.inst(1).src_info.src2_data
    )
  )
  val addr_aligned = Wire(Vec(config.fuNum, Bool()))
  for (i <- 0 until config.fuNum) {
    addr_aligned(i) := LookupTree(
      io.inst(i).info.op(1, 0),
      List(
        "b00".U -> true.B, //b
        "b01".U -> (mem_addr(i)(0) === 0.U), //h
        "b10".U -> (mem_addr(i)(1, 0) === 0.U), //w
        "b11".U -> (mem_addr(i)(2, 0) === 0.U) //d
      )
    )
  }

  for (i <- 0 until config.fuNum) {
    val store_inst = LSUOpType.isStore(io.inst(i).info.op)
    io.inst(i).ex.out                                := io.inst(i).ex.in
    io.inst(i).ex.out.exception(loadAddrMisaligned)  := !store_inst && !addr_aligned(i)
    io.inst(i).ex.out.exception(storeAddrMisaligned) := store_inst && !addr_aligned(i)
    io.inst(i).ex.out.tval                           := Mux(
      io.inst(i).ex.in.exception.asUInt.orR,
      io.inst(i).ex.in.tval,
      mem_addr(i)
    )
  }
  io.inst(0).mem_sel := (io.inst(0).info.fusel === FuType.lsu) &&
    !(io.inst(0).ex.out.exception.asUInt.orR || io.inst(0).ex.out.interrupt.asUInt.orR) &&
    io.inst(0).info.valid
  io.inst(1).mem_sel := (io.inst(1).info.fusel === FuType.lsu) &&
    !(io.inst(0).ex.out.exception.asUInt.orR || io.inst(0).ex.out.interrupt.asUInt.orR) &&
    !(io.inst(1).ex.out.exception.asUInt.orR || io.inst(1).ex.out.interrupt.asUInt.orR) &&
    io.inst(1).info.valid

}
