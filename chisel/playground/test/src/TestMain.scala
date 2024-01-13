import cpu._
import circt.stage._

import cpu.pipeline.execute.Csr
import cache.DCache
import icache.mmu.Tlb

object TestMain extends App {
  implicit val cpuConfig       = new CpuConfig()
  implicit val dCacheConfig = CacheConfig(cacheType = "dcache")
  def top                   = new Tlb
  val useMFC                = false // use MLIR-based firrtl compiler
  val generator             = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}
