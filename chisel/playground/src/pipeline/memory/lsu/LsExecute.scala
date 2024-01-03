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
      val info     = new InstInfo()
    })
    val out = Output(new Bundle {
      val loadAddrMisaligned  = Bool()
      val storeAddrMisaligned = Bool()
      val loadAccessFault     = Bool()
      val storeAccessFault    = Bool()
      val rdata               = UInt(XLEN.W)
      val ready               = Bool()
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

  val isStore     = valid && LSUOpType.isStore(op)
  val partialLoad = !isStore && (op =/= LSUOpType.ld)

  val has_acc_err = addr(XLEN - 1, VADDR_WID).orR

  val size     = op(1, 0)
  val reqAddr  = if (XLEN == 32) SignedExtend(addr, VADDR_WID) else addr(VADDR_WID - 1, 0)
  val reqWdata = if (XLEN == 32) genWdata32(io.in.wdata, size) else genWdata(io.in.wdata, size)
  val reqWmask = if (XLEN == 32) genWmask32(addr, size) else genWmask(addr, size)

  val rdata   = io.dataMemory.in.rdata
  val acc_err = io.dataMemory.in.acc_err

  val rdataSel64 = LookupTree(
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
  val rdataSel32 = LookupTree(
    addr(1, 0),
    List(
      "b00".U -> rdata(31, 0),
      "b01".U -> rdata(31, 8),
      "b10".U -> rdata(31, 16),
      "b11".U -> rdata(31, 24)
    )
  )
  val rdataSel = if (XLEN == 32) rdataSel32 else rdataSel64
  val rdataPartialLoad = LookupTree(
    op,
    List(
      LSUOpType.lb  -> SignedExtend(rdataSel(7, 0), XLEN),
      LSUOpType.lh  -> SignedExtend(rdataSel(15, 0), XLEN),
      LSUOpType.lw  -> SignedExtend(rdataSel(31, 0), XLEN),
      LSUOpType.lbu -> ZeroExtend(rdataSel(7, 0), XLEN),
      LSUOpType.lhu -> ZeroExtend(rdataSel(15, 0), XLEN),
      LSUOpType.lwu -> ZeroExtend(rdataSel(31, 0), XLEN)
    )
  )
  val addrAligned = LookupTree(
    op(1, 0),
    List(
      "b00".U -> true.B, //b
      "b01".U -> (addr(0) === 0.U), //h
      "b10".U -> (addr(1, 0) === 0.U), //w
      "b11".U -> (addr(2, 0) === 0.U) //d
    )
  )

  io.dataMemory.out.en    := valid && !io.out.storeAddrMisaligned && !io.out.loadAddrMisaligned && !has_acc_err
  io.dataMemory.out.rlen  := size
  io.dataMemory.out.wen   := isStore
  io.dataMemory.out.wstrb := reqWmask
  io.dataMemory.out.addr  := reqAddr
  io.dataMemory.out.wdata := reqWdata

  val is_amo = valid && LSUOpType.isAMO(op)
  io.out.ready               := io.dataMemory.in.ready && io.dataMemory.out.en
  io.out.rdata               := Mux(partialLoad, rdataPartialLoad, rdataSel)
  io.out.loadAddrMisaligned  := valid && !isStore && !is_amo && !addrAligned
  io.out.loadAccessFault     := valid && !isStore && !is_amo && (acc_err || has_acc_err)
  io.out.storeAddrMisaligned := valid && (isStore || is_amo) && !addrAligned
  io.out.storeAccessFault    := valid && (isStore || is_amo) && (acc_err || has_acc_err)
}
