package cpu

import chisel3.util._

case class CpuConfig(
  val build: Boolean = false, // 是否为build模式
  // 指令集
  val isRV32:        Boolean = false, // 是否为RV32
  val hasMExtension: Boolean = true, // 是否有乘除法单元
  val hasAExtension: Boolean = true, // 是否有原子指令
  // 特权模式
  val hasSMode: Boolean = false, // 是否有S模式
  val hasUMode: Boolean = true, // 是否有U模式
  // 模块配置
  val hasCommitBuffer: Boolean = true, // 是否有提交缓存
  val decoderNum:      Int     = 2, // 同时访问寄存器的指令数
  val commitNum:       Int     = 2, // 同时提交的指令数
  val fuNum:           Int     = 2, // 功能单元数
  val instFetchNum:    Int     = 2, // iCache取到的指令数量
  val instFifoDepth:   Int     = 8, // 指令缓存深度
  val mulClockNum:     Int     = 2, // 乘法器的时钟周期数
  val divClockNum:     Int     = 8, // 除法器的时钟周期数
  val branchPredictor: String  = "adaptive" // adaptive, global
)

case class BranchPredictorConfig(
  val bhtDepth: Int = 5,
  val phtDepth: Int = 6)

case class CacheConfig(
  nway:         Int = 2, // 路数
  nbank:        Int, // 每个项目中的bank数
  nindex:       Int, // 每路的项目数
  bytesPerBank: Int // 每个bank中的字节数
) {
  val config          = CpuConfig()
  val indexWidth      = log2Ceil(nindex) // index的位宽
  val bankIndexWidth  = log2Ceil(nbank)
  val bankOffsetWidth = log2Ceil(bytesPerBank)
  val offsetWidth     = bankIndexWidth + bankOffsetWidth // offset的位宽
  val tagWidth        = 32 - indexWidth - offsetWidth // tag的位宽
  val bitsPerBank     = bytesPerBank * 8
  require(isPow2(nindex))
  require(isPow2(nway))
  require(isPow2(nbank))
  require(isPow2(bytesPerBank))
  require(
    tagWidth + indexWidth + bankIndexWidth + bankOffsetWidth == 32,
    "basic request calculation"
  )
}
