package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig

class DTlbL1 extends Module {
  val io = IO(new Bundle {
    val cache = new Tlb_DCache()
    val addr  = Input(UInt(DATA_ADDR_WID.W))
  })

  val cacheConfig = CacheConfig("dcache")

  io.cache.uncached       := AddressSpace.isMMIO(io.addr)
  io.cache.translation_ok := true.B
  io.cache.hit            := true.B
  io.cache.tlb1_ok        := true.B
  io.cache.tag            := io.addr(PADDR_WID - 1, cacheConfig.offsetWidth + cacheConfig.indexWidth)
  io.cache.pa             := Cat(io.cache.tag, io.addr(cacheConfig.offsetWidth + cacheConfig.indexWidth - 1, 0))

  println("----------------------------------------")
  println("DTlbL1")
  println("tag from " + (PADDR_WID - 1) + " to " + (cacheConfig.offsetWidth + cacheConfig.indexWidth))
  println("----------------------------------------")
}
