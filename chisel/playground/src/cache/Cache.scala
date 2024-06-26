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
}
