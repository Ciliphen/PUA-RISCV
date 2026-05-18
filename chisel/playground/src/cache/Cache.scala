package cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.CacheConfig

class Cache(implicit cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Flipped(new Cache_ICache())
    val data = Flipped(new Cache_DCache())
    val axi  = new AXI()
    val debug = Output(new MMU_DEBUG())
  })

  implicit val iCacheConfig = CacheConfig(cacheType = "icache")
  implicit val dCacheConfig = CacheConfig(cacheType = "dcache")

  val icache        = Module(new ICache(iCacheConfig))
  val dcache        = Module(new DCache(dCacheConfig))
  val axi_interface = Module(new CacheAXIInterface())

  icache.io.axi <> axi_interface.io.icache
  dcache.io.axi <> axi_interface.io.dcache

  io.inst <> icache.io.cpu
  io.data <> dcache.io.cpu
  io.axi <> axi_interface.io.axi

  io.debug := 0.U.asTypeOf(new MMU_DEBUG())
  io.debug.icache_state := icache.io.debug.icache_state
  io.debug.icache_stall := icache.io.debug.icache_stall
  io.debug.icache_tlb_hit := icache.io.debug.icache_tlb_hit
  io.debug.icache_page_fault := icache.io.debug.icache_page_fault
  io.debug.icache_access_fault := icache.io.debug.icache_access_fault
  io.debug.dcache_state := dcache.io.debug.dcache_state
  io.debug.ptw_state := dcache.io.debug.ptw_state
  io.debug.ptw_working := dcache.io.debug.ptw_working
  io.debug.ptw_vpn_valid := dcache.io.debug.ptw_vpn_valid
  io.debug.ptw_vpn_ready := dcache.io.debug.ptw_vpn_ready
  io.debug.ptw_pte_valid := dcache.io.debug.ptw_pte_valid
  io.debug.dcache_tlb_hit := dcache.io.debug.dcache_tlb_hit
  io.debug.dcache_page_fault := dcache.io.debug.dcache_page_fault
  io.debug.dcache_access_fault := dcache.io.debug.dcache_access_fault
  io.debug.dcache_ready := dcache.io.debug.dcache_ready
}
