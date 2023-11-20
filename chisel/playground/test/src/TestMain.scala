import cpu._
import circt.stage._
import cache.Cache
import cpu.pipeline.decoder.Decoder
import cpu.pipeline.decoder.DecoderUnit
import cache.ICache
import cpu.pipeline.fetch.BranchPredictorUnit
import cpu.pipeline.execute.Alu
import cpu.pipeline.execute.BranchCtrl
import cpu.pipeline.execute.Fu
import cpu.pipeline.execute.ExeAccessMemCtrl

object TestMain extends App {
  implicit val config = new CpuConfig()
  def top             = new ExeAccessMemCtrl()
  val useMFC          = false // use MLIR-based firrtl compiler
  val generator       = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}