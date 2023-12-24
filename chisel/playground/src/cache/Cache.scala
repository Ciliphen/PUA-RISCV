package cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.CacheConfig

class Cache(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Flipped(new Cache_ICache())
    val data = Flipped(new Cache_DCache())
    val axi  = new AXI()
  })

  // 每个 bank 存 2 条 32 bit 指令
  implicit val iCacheConfig =
    CacheConfig(nindex = 64, nbank = 4, bytesPerBank = (INST_WID / 8) * config.instFetchNum)
  // 每个 bank 存 1 条 XLEN bit 数据
  implicit val dCacheConfig =
    CacheConfig(nindex = 128, nbank = 8, bytesPerBank = XLEN / 8)

  val icache        = Module(new ICache(iCacheConfig))
  val dcache        = Module(new DCache(dCacheConfig))
  val axi_interface = Module(new CacheAXIInterface())

  icache.io.axi <> axi_interface.io.icache
  dcache.io.axi <> axi_interface.io.dcache

  io.inst <> icache.io.cpu
  io.data <> dcache.io.cpu
  io.axi <> axi_interface.io.axi
}
