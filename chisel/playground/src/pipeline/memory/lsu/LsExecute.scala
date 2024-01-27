package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class LsExecute extends Module {
  val io = IO(new Bundle {
    val dataMemory = new Lsu_DataMemory()
    val in = Input(new Bundle {
      val mem_en   = Bool()
      val mem_addr = UInt(XLEN.W)
      val wdata    = UInt(XLEN.W)
      val info     = new Info()
    })
    val out = Output(new Bundle {
      val addr_misaligned = Bool()
      val access_fault    = Bool()
      val page_fault      = Bool()
      val rdata           = UInt(XLEN.W)
      val ready           = Bool()
    })
  })

  def genWmask(addr: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> 0x1.U, //0001 << addr(2:0)
        "b01".U -> 0x3.U, //0011
        "b10".U -> 0xf.U, //1111
        "b11".U -> 0xff.U //11111111
      )
    ) << addr(2, 0)
  }
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

  def genWmask32(addr: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> 0x1.U, //0001 << addr(1:0)
        "b01".U -> 0x3.U, //0011
        "b10".U -> 0xf.U //1111
      )
    ) << addr(1, 0)
  }
  def genWdata32(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> Fill(4, data(7, 0)),
        "b01".U -> Fill(2, data(15, 0)),
        "b10".U -> data
      )
    )
  }

  val valid = io.in.mem_en
  val addr  = io.in.mem_addr
  val op    = io.in.info.op

  val is_store     = valid && LSUOpType.isStore(op)
  val partial_load = !is_store && (op =/= LSUOpType.ld)

  val size      = op(1, 0)
  val req_addr  = if (XLEN == 32) SignedExtend(addr, XLEN) else addr
  val req_wdata = if (XLEN == 32) genWdata32(io.in.wdata, size) else genWdata(io.in.wdata, size)
  val req_wmask = if (XLEN == 32) genWmask32(addr, size) else genWmask(addr, size)

  val rdata        = io.dataMemory.in.rdata
  val access_fault = io.dataMemory.in.access_fault
  val page_fault   = io.dataMemory.in.page_fault

  val rdata64 = LookupTree(
    addr(2, 0),
    List(
      "b000".U -> rdata(63, 0),
      "b001".U -> rdata(63, 8),
      "b010".U -> rdata(63, 16),
      "b011".U -> rdata(63, 24),
      "b100".U -> rdata(63, 32),
      "b101".U -> rdata(63, 40),
      "b110".U -> rdata(63, 48),
      "b111".U -> rdata(63, 56)
    )
  )
  val rdata32 = LookupTree(
    addr(1, 0),
    List(
      "b00".U -> rdata(31, 0),
      "b01".U -> rdata(31, 8),
      "b10".U -> rdata(31, 16),
      "b11".U -> rdata(31, 24)
    )
  )
  val rdata_result = if (XLEN == 32) rdata32 else rdata64
  val rdata_partial_result = LookupTree(
    op,
    List(
      LSUOpType.lb  -> SignedExtend(rdata_result(7, 0), XLEN),
      LSUOpType.lh  -> SignedExtend(rdata_result(15, 0), XLEN),
      LSUOpType.lw  -> SignedExtend(rdata_result(31, 0), XLEN),
      LSUOpType.lbu -> ZeroExtend(rdata_result(7, 0), XLEN),
      LSUOpType.lhu -> ZeroExtend(rdata_result(15, 0), XLEN),
      LSUOpType.lwu -> ZeroExtend(rdata_result(31, 0), XLEN)
    )
  )
  val addr_aligned = LookupTree(
    op(1, 0),
    List(
      "b00".U -> true.B, //b
      "b01".U -> (addr(0) === 0.U), //h
      "b10".U -> (addr(1, 0) === 0.U), //w
      "b11".U -> (addr(2, 0) === 0.U) //d
    )
  )

  io.dataMemory.out.en    := valid && !io.out.addr_misaligned
  io.dataMemory.out.rlen  := size
  io.dataMemory.out.wen   := is_store
  io.dataMemory.out.wstrb := req_wmask
  io.dataMemory.out.addr  := req_addr
  io.dataMemory.out.wdata := req_wdata

  io.out.ready           := io.dataMemory.in.ready && io.dataMemory.out.en
  io.out.rdata           := Mux(partial_load, rdata_partial_result, rdata_result)
  io.out.addr_misaligned := valid && !addr_aligned
  io.out.access_fault    := valid && access_fault
  io.out.page_fault      := valid && page_fault
}
