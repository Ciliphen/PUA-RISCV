package cpu

import chisel3.util._

case class CpuConfig(
    val build: Boolean = false,              // 是否为build模式
    val hasCommitBuffer: Boolean = false,    // 是否有提交缓存
    val decoderNum: Int = 1,                 // 同时访问寄存器的指令数
    val commitNum: Int = 1,                  // 同时提交的指令数
    val fuNum: Int = 1,                      // 功能单元数
    val instFetchNum: Int = 1,               // iCache取到的指令数量
    val instFifoDepth: Int = 8,              // 指令缓存深度
    val mulClockNum: Int = 2,                // 乘法器的时钟周期数
    val divClockNum: Int = 8,                // 除法器的时钟周期数
    val branchPredictor: String = "adaptive",// adaptive, pesudo, global
)

case class BranchPredictorConfig(
    val bhtDepth: Int = 5,
    val phtDepth: Int = 6,
)
