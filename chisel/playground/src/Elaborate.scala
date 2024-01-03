import cpu._
import circt.stage._

object Elaborate extends App {
  implicit val cpuConfig = new CpuConfig()
  def top                = new PuaCpu()
  val useMFC             = false // use MLIR-based firrtl compiler
  val generator          = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}
