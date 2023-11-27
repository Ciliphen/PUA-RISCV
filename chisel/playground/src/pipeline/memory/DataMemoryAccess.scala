package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class DataMemoryAccess(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val memoryUnit = new Bundle {
      val in = Input(new Bundle {
        val mem_en    = Bool()
        val inst_info = new InstInfo()
        val mem_wdata = UInt(DATA_WID.W)
        val mem_addr  = UInt(DATA_ADDR_WID.W)
        val mem_sel   = Vec(config.fuNum, Bool())
        val ex        = Vec(config.fuNum, new ExceptionInfo())
      })
      val out = Output(new Bundle {
        val rdata = Output(UInt(DATA_WID.W))
      })
    }

    val dataMemory = new Bundle {
      val in = Input(new Bundle {
        val rdata = UInt(DATA_WID.W)
      })
      val out = Output(new Bundle {
        val en    = Bool()
        val rlen  = UInt(2.W)
        val wen   = Bool()
        val addr  = UInt(DATA_ADDR_WID.W)
        val wdata = UInt(DATA_WID.W)
      })
    }
  })
  val mem_addr  = io.memoryUnit.in.mem_addr
  val mem_addr2 = mem_addr(1, 0)
  val mem_rdata = io.dataMemory.in.rdata
  val mem_wdata = io.memoryUnit.in.mem_wdata
  val op        = io.memoryUnit.in.inst_info.op
  io.dataMemory.out.en := io.memoryUnit.in.mem_en &&
    (io.memoryUnit.in.mem_sel(0) && !io.memoryUnit.in.ex(0).exception.asUInt.orR ||
      io.memoryUnit.in.mem_sel(1) && !io.memoryUnit.in.ex(0).exception.asUInt.orR &&
        !io.memoryUnit.in.ex(1).exception.asUInt.orR)
  io.dataMemory.out.addr := mem_addr
  val rdata = LookupTree(
    mem_addr(2, 0),
    List(
      "b000".U -> mem_rdata(63, 0),
      "b001".U -> mem_rdata(63, 8),
      "b010".U -> mem_rdata(63, 16),
      "b011".U -> mem_rdata(63, 24),
      "b100".U -> mem_rdata(63, 32),
      "b101".U -> mem_rdata(63, 40),
      "b110".U -> mem_rdata(63, 48),
      "b111".U -> mem_rdata(63, 56)
    )
  )
  io.memoryUnit.out.rdata := LookupTree(
    op,
    List(
      LSUOpType.lb  -> SignedExtend(rdata(7, 0), XLEN),
      LSUOpType.lh  -> SignedExtend(rdata(15, 0), XLEN),
      LSUOpType.lw  -> SignedExtend(rdata(31, 0), XLEN),
      LSUOpType.lbu -> ZeroExtend(rdata(7, 0), XLEN),
      LSUOpType.lhu -> ZeroExtend(rdata(15, 0), XLEN),
      LSUOpType.lwu -> ZeroExtend(rdata(31, 0), XLEN)
    )
  )
  def genWdata(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> Fill(8, data(7, 0)),
        "b01".U -> Fill(4, data(15, 0)),
        "b10".U -> Fill(2, data(31, 0)),
        "b11".U -> data
      )
    )
  }
  io.dataMemory.out.wdata := genWdata(mem_wdata, op(1, 0))
  io.dataMemory.out.wen   := LSUOpType.isStore(op) && io.memoryUnit.in.mem_en
  io.dataMemory.out.rlen  := op(1, 0)
}
