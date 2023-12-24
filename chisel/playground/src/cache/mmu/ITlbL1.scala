package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class ITlbL1 extends Module {
  val io = IO(new Bundle {
    val addr  = Input(UInt(XLEN.W))
    val cache = new Tlb_ICache()
  })

  io.cache.uncached       := AddressSpace.isMMIO(io.addr)
  io.cache.translation_ok := true.B
  io.cache.hit            := true.B
  io.cache.tag            := io.addr(XLEN - 1, 12)
  io.cache.pa             := Cat(io.cache.tag, io.addr(11, 0))
}
