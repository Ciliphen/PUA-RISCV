package cpu.pipeline.decoder

import chisel3._
import chisel3.util._

import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class JumpCtrl(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val allow_to_go = Bool()
      val pc          = UInt(PC_WID.W)
      val info   = new InstInfo()
      val src_info    = new SrcInfo()
      val forward     = Vec(config.fuNum, new DataForwardToDecoderUnit())
    })
    val out = Output(new Bundle {
      val jump_inst     = Bool()
      val jump_register = Bool()
      val jump          = Bool()
      val jump_target   = UInt(PC_WID.W)
    })
  })

  val op                 = io.in.info.op
  val jump_inst          = VecInit(ALUOpType.jal).contains(op)
  val jump_register_inst = VecInit(ALUOpType.jalr).contains(op)
  io.out.jump_inst := jump_inst || jump_register_inst
  io.out.jump      := io.in.allow_to_go && (jump_inst || jump_register_inst && !io.out.jump_register)
  if (config.decoderNum == 2) {
    io.out.jump_register := jump_register_inst &&
    ((io.in.forward(0).exe.wen && io.in.info.reg1_raddr === io.in.forward(0).exe.waddr) ||
    (io.in.forward(1).exe.wen && io.in.info.reg1_raddr === io.in.forward(1).exe.waddr) ||
    (io.in.forward(0).mem.wen && io.in.info.reg1_raddr === io.in.forward(0).mem.waddr) ||
    (io.in.forward(1).mem.wen && io.in.info.reg1_raddr === io.in.forward(1).mem.waddr))

  } else {
    io.out.jump_register := jump_register_inst &&
    ((io.in.forward(0).exe.wen && io.in.info.reg1_raddr === io.in.forward(0).exe.waddr) ||
    (io.in.forward(0).mem.wen && io.in.info.reg1_raddr === io.in.forward(0).mem.waddr))
  }
  io.out.jump_target := Mux(
    jump_inst,
    io.in.src_info.src1_data + io.in.src_info.src2_data,
    io.in.src_info.src1_data
  )
}
