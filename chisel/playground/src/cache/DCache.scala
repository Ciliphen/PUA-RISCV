// * Cache 设计借鉴了nscscc2021 cqu的cdim * //
package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.CacheConfig
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

class WriteBufferUnit extends Bundle {
  val data = UInt(DATA_WID.W)
  val addr = UInt(DATA_ADDR_WID.W)
  val strb = UInt(4.W)
  val size = UInt(2.W)
}

class DCache(cacheConfig: CacheConfig)(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cpu       = Flipped(new Cache_DCache())
    val axi       = new DCache_AXIInterface()
  })

  // * fsm * //
  val s_idle :: s_read :: s_write :: s_finishwait :: Nil = Enum(4)
  val state                                              = RegInit(s_idle)
}
