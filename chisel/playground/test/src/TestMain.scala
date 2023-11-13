import cpu._
import circt.stage._
import cache.Cache

object TestMain extends App {
  implicit val config = new CpuConfig()
  def top             = new Cache()
  val useMFC          = false // use MLIR-based firrtl compiler
  val generator       = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}