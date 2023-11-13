package cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.CpuConfig

class Cache(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Flipped(new Cache_ICache())
    val data = Flipped(new Cache_DCache())
    val axi  = new AXI()
  })

  val icache        = Module(new ICache())
  val dcache        = Module(new DCache())
  val axi_interface = Module(new CacheAXIInterface())

  icache.io.axi <> axi_interface.io.icache
  dcache.io.axi <> axi_interface.io.dcache

  io.inst <> icache.io.cpu
  io.data <> dcache.io.cpu
  io.axi <> axi_interface.io.axi
}
