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
        val inst_info = new InstInfo()
        val addr      = UInt(DATA_ADDR_WID.W)
        val wdata     = UInt(DATA_WID.W)
      })
    }

    val inst = Vec(
      config.fuNum,
      new Bundle {
        val inst_info = Input(new InstInfo())
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
  io.mem.out.ren := io.inst(0).mem_sel && LSUOpType.isLoad(io.inst(0).inst_info.op) ||
    io.inst(1).mem_sel && LSUOpType.isLoad(io.inst(1).inst_info.op)
  io.mem.out.wen := io.inst(0).mem_sel && LSUOpType.isStore(io.inst(0).inst_info.op) ||
    io.inst(1).mem_sel && LSUOpType.isStore(io.inst(1).inst_info.op)
  io.mem.out.inst_info := Mux1H(
    Seq(
      (io.inst(0).inst_info.fusel === FuType.lsu) -> io.inst(0).inst_info,
      (io.inst(1).inst_info.fusel === FuType.lsu) -> io.inst(1).inst_info
    )
  )
  val mem_addr = Wire(Vec(config.fuNum, UInt(DATA_ADDR_WID.W)))
  mem_addr(0) := io.inst(0).src_info.src1_data + io.inst(0).src_info.src2_data
  mem_addr(1) := io.inst(1).src_info.src1_data + io.inst(1).src_info.src2_data
  io.mem.out.addr := Mux1H(
    Seq(
      (io.inst(0).inst_info.fusel === FuType.lsu) -> mem_addr(0),
      (io.inst(1).inst_info.fusel === FuType.lsu) -> mem_addr(1)
    )
  )
  io.mem.out.wdata := Mux1H(
    Seq(
      (io.inst(0).inst_info.fusel === FuType.lsu) ->
        io.inst(0).src_info.src2_data,
      (io.inst(1).inst_info.fusel === FuType.lsu) ->
        io.inst(1).src_info.src2_data
    )
  )
  val addr_aligned = Wire(Vec(config.fuNum, Bool()))
  for (i <- 0 until config.fuNum) {
    addr_aligned(i) := LookupTree(
      io.inst(i).inst_info.op(1, 0),
      List(
        "b00".U -> true.B, //b
        "b01".U -> (mem_addr(i)(0) === 0.U), //h
        "b10".U -> (mem_addr(i)(1, 0) === 0.U), //w
        "b11".U -> (mem_addr(i)(2, 0) === 0.U) //d
      )
    )
  }

  for (i <- 0 until config.fuNum) {
    val store_inst = LSUOpType.isStore(io.inst(i).inst_info.op)
    io.inst(i).ex.out                             := io.inst(i).ex.in
    io.inst(i).ex.out.excode(loadAddrMisaligned)  := store_inst && !addr_aligned(i)
    io.inst(i).ex.out.excode(storeAddrMisaligned) := !store_inst && !addr_aligned(i)
  }
  io.inst(0).mem_sel := (LSUOpType.isStore(io.inst(0).inst_info.op) || LSUOpType.isLoad(io.inst(0).inst_info.op)) &&
    !io.inst(0).ex.out.excode.asUInt.orR
  io.inst(1).mem_sel := (LSUOpType.isStore(io.inst(1).inst_info.op) || LSUOpType.isLoad(io.inst(1).inst_info.op)) &&
    !io.inst(0).ex.out.excode.asUInt.orR && !io.inst(1).ex.out.excode.asUInt.orR

}
