package cpu

import chisel3.util._
import cpu.defines.Const._
import cpu.defines.Sv39Const

case class CpuConfig(
  val build: Boolean = false, // 是否为build模式
  // 指令集
  val isRV32:        Boolean = false, // 是否为RV32
  val hasMExtension: Boolean = true, // 是否有乘除法单元
  val hasAExtension: Boolean = true, // 是否有原子指令
  // 特权模式
  val hasSMode: Boolean = true, // 是否有S模式
  val hasUMode: Boolean = true, // 是否有U模式
  // 模块配置
  val hasCommitBuffer: Boolean = true, // 是否有提交缓存
  val decoderNum:      Int     = 2, // 译码级最大解码的指令数，也是同时访问寄存器的指令数
  val commitNum:       Int     = 2, // 同时提交的指令数, 也是最大发射的指令数
  val instFetchNum:    Int     = 2, // iCache取到的指令数量，最大取值为4
  val instFifoDepth:   Int     = 8, // 指令缓存深度
  val mulClockNum:     Int     = 2, // 乘法器的时钟周期数
  val divClockNum:     Int     = 8, // 除法器的时钟周期数
  val branchPredictor: String  = "adaptive" // adaptive, global
)

/* BPU 的配置文件 */
case class BranchPredictorConfig(
  val bhtDepth: Int = 5,
  val phtDepth: Int = 6)

/* TLB L2 的配置文件 */
case class TLBConfig(
  nindex: Int = 16,
  nway:   Int = 2)

case class CacheConfig(
  cacheType: String = "icache" // icache, dcache
) extends Sv39Const {
// ==========================================================
// |        tag         |  index |         offset           |
// |                    |        | bank index | bank offset |
// ==========================================================
  val nway   = 2 // 路数，目前只支持2路
  val nbank  = if (cacheType == "icache") (16 / cpuConfig.instFetchNum) else 8 // 每个项目中的bank数
  val nindex = if (cacheType == "icache") 64 else 64 // 每路的项目数
  val bitsPerBank = // 每个bank的位数
    if (cacheType == "icache") INST_WID * cpuConfig.instFetchNum
    else XLEN
  val bytesPerBank    = bitsPerBank / 8 //每个bank中的字节数
  val indexWidth      = log2Ceil(nindex) // index的位宽
  val bankIndexWidth  = log2Ceil(nbank) // bank index的位宽
  val bankOffsetWidth = log2Ceil(bytesPerBank) // bank offset的位宽
  val offsetWidth     = bankIndexWidth + bankOffsetWidth // offset的位宽
  val tagWidth        = PADDR_WID - pageOffsetLen // tag的位宽
  require(offsetWidth + indexWidth == pageOffsetLen) // offsetLen是页内偏移的位宽，为简化设计，这里直接保证每路容量等于页大小
  require(isPow2(nindex))
  require(isPow2(nway))
  require(isPow2(nbank))
  require(isPow2(bytesPerBank))
  require(isPow2(cpuConfig.instFetchNum))
  require(cpuConfig.instFetchNum <= 4, "instFetchNum should be less than 4")
  require(nbank * nindex * bytesPerBank <= 4 * 1024, "VIPT requires the cache size to be less than 4KB")
}
