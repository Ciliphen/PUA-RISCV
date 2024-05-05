import cpu._
import circt.stage._

import cpu.pipeline.Csr
import icache.mmu.Tlb
import cpu.pipeline.ARegFile
import cpu.pipeline.Alu

object TestMain extends App {
  implicit val cpuConfig    = new CpuConfig()
  implicit val dCacheConfig = CacheConfig(cacheType = "dcache")
  def top                   = new Alu()
  val generator             = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
}
