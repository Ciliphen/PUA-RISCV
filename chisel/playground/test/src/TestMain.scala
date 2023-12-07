import cpu._
import circt.stage._

import cpu.pipeline.execute.Csr
import cpu.pipeline.memory.LSExe
import cpu.pipeline.memory.AtomAlu

object TestMain extends App {
  implicit val config = new CpuConfig()
  def top             = new AtomAlu()
  val useMFC          = false // use MLIR-based firrtl compiler
  val generator       = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  if (useMFC) {
    (new ChiselStage).execute(args, generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
  } else {
    (new chisel3.stage.ChiselStage).execute(args, generator)
  }
}