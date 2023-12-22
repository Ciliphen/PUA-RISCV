package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class DTlbL1 extends Module {
  val io = IO(new Bundle {
    val cache = new Tlb_DCache()
    val addr  = Input(UInt(DATA_ADDR_WID.W))
  })

  io.cache.uncached       := AddressSpace.isMMIO(io.addr)
  io.cache.translation_ok := true.B
  io.cache.hit            := true.B
  io.cache.tlb1_ok        := true.B
  io.cache.tag            := io.addr(XLEN - 1, 12)
  io.cache.pa             := Cat(io.cache.tag, io.addr(11, 0))
}
