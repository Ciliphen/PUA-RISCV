package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Mou extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val info = new Info()
      val pc   = UInt(XLEN.W)
    })
    val out = Output(new Bundle {
      val flush      = Bool()
      val fence_i    = Bool()
      val sfence_vma = Bool()
      val target     = UInt(XLEN.W)
    })
  })

  val valid      = io.in.info.valid && io.in.info.fusel === FuType.mou
  val fence_i    = valid && io.in.info.op === MOUOpType.fencei
  val sfence_vma = valid && io.in.info.op === MOUOpType.sfence_vma

  io.out.flush      := valid
  io.out.fence_i    := fence_i
  io.out.sfence_vma := sfence_vma
  io.out.target     := io.in.pc + 4.U

}
