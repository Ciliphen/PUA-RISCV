import cpu._
import circt.stage._

object Elaborate extends App {
  implicit val cpuConfig = new CpuConfig()
  def top                = new PuaCpu()
  val generator          = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
}
