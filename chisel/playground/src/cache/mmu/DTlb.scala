package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.pipeline.execute.CsrTlb

class Tlb_DCache extends Bundle {
  val cacheConfig = CacheConfig("dcache")

  val uncached       = Output(Bool())
  val translation_ok = Output(Bool())
  val hit_L2         = Output(Bool())
  val ptag           = Output(UInt(cacheConfig.tagWidth.W))
  val paddr          = Output(UInt(PADDR_WID.W))
}

class DTlb extends Module with Sv39Const {
  val io = IO(new Bundle {
    val addr  = Input(UInt(XLEN.W))
    val cache = new Tlb_DCache()
    val csr   = Flipped(new CsrTlb())
  })

  io.cache.uncached       := AddressSpace.isMMIO(io.addr)
  io.cache.translation_ok := true.B
  io.cache.hit_L2         := true.B
  io.cache.ptag           := io.addr(PADDR_WID - 1, pageOffsetLen)
  io.cache.paddr          := Cat(io.cache.ptag, io.addr(pageOffsetLen - 1, 0))

}
