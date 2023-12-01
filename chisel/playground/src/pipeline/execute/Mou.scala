package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Mou extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val info = new InstInfo()
      val pc   = UInt(PC_WID.W)
    })
    val out = Output(new Bundle {
      val flush  = Bool()
      val target = UInt(PC_WID.W)
    })
  })

  val valid   = io.in.info.valid
  val fence_i = valid && io.in.info.fusel === FuType.mou && io.in.info.op === MOUOpType.fencei

  // TODO:增加其他fence指令时只要在后面加就行
  io.out.flush := fence_i
  io.out.target := MuxCase(
    io.in.pc + 4.U,
    Seq(
      fence_i -> (io.in.pc + 4.U)
    )
  )
}
