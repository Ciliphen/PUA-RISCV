package icache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.pipeline.execute.CsrTlb

class Tlb_ICache extends Bundle {
  val cacheConfig = CacheConfig("icache")
  val addr        = Input(UInt(XLEN.W))

  val uncached = Output(Bool())
  val l1_hit   = Output(Bool())
  val ptag     = Output(UInt(cacheConfig.tagWidth.W))
  val paddr    = Output(UInt(PADDR_WID.W))
}

class Tlb_DCache extends Bundle {
  val cacheConfig = CacheConfig("dcache")
  val addr        = Input(UInt(XLEN.W))

  val uncached = Output(Bool())
  val l1_hit   = Output(Bool())
  val ptag     = Output(UInt(cacheConfig.tagWidth.W))
  val paddr    = Output(UInt(PADDR_WID.W))
}

class Tlb extends Module with HasTlbConst with HasCSRConst {
  val io = IO(new Bundle {
    val icache = new Tlb_ICache()
    val dcache = new Tlb_DCache()
    val csr    = Flipped(new CsrTlb())
  })

  val satp = WireInit(io.csr.satp)
  val mode = WireInit(io.csr.mode)

  val vm_enabled = (satp.asTypeOf(satpBundle).mode === 8.U) && (mode < ModeM)
  val itlb       = RegInit(0.U.asTypeOf(tlbBundle))

  val l1_hit = itlb.asid === satp.asTypeOf(satpBundle).asid

  io.icache.uncached := AddressSpace.isMMIO(io.icache.addr)
  io.icache.l1_hit   := !vm_enabled
  io.icache.ptag     := Mux(vm_enabled, DontCare, io.icache.addr(PADDR_WID - 1, pageOffsetLen))
  io.icache.paddr    := Cat(io.icache.ptag, io.icache.addr(pageOffsetLen - 1, 0))

  io.dcache.uncached := AddressSpace.isMMIO(io.dcache.addr)
  io.dcache.l1_hit   := !vm_enabled
  io.dcache.ptag     := Mux(vm_enabled, DontCare, io.dcache.addr(PADDR_WID - 1, pageOffsetLen))
  io.dcache.paddr    := Cat(io.dcache.ptag, io.dcache.addr(pageOffsetLen - 1, 0))

}
