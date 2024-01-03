package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.pipeline.execute.CsrTlb

class Tlb_ICache extends Bundle {
  val cacheConfig = CacheConfig("icache")

  val uncached       = Output(Bool())
  val translation_ok = Output(Bool())
  val hit_L2         = Output(Bool())
  val ptag           = Output(UInt(cacheConfig.tagWidth.W))
  val paddr          = Output(UInt(PADDR_WID.W))
}

class ITlb extends Module with Sv39Const with HasCSRConst {
  val io = IO(new Bundle {
    val addr  = Input(UInt(XLEN.W))
    val cache = new Tlb_ICache()
    val csr   = Flipped(new CsrTlb())
    // val tlbL2 = Flipped(new TlbL2_TlbL1())
  })

  val satp = WireInit(io.csr.satp)
  val mode = WireInit(io.csr.mode)

  val vm_enabled = (satp.asTypeOf(satpBundle).mode === 8.U) && (mode < ModeM)

  io.cache.uncached       := AddressSpace.isMMIO(io.addr)
  io.cache.translation_ok := !vm_enabled
  io.cache.hit_L2         := true.B
  io.cache.ptag           := Mux(vm_enabled, DontCare, io.addr(PADDR_WID - 1, offsetLen))
  io.cache.paddr          := Cat(io.cache.ptag, io.addr(offsetLen - 1, 0))

}
