package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import chisel3.util.experimental.BoringUtils

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
      val ex         = new ExceptionInfo()
    })
  })

  val mode = Wire(Priv())
  val tvm  = Wire(Bool())
  BoringUtils.addSink(mode, "mode")
  BoringUtils.addSink(tvm, "tvm")

  val valid   = io.in.info.valid && io.in.info.fusel === FuType.mou
  val fence_i = valid && io.in.info.op === MOUOpType.fencei
  // sfence.vma非法的条件为：
  // 1. 当前模式为S模式，且tvm为1
  // 2. 当前模式为U模式
  val sfence_vma_illegal = tvm && mode === Priv.s || mode < Priv.s
  val sfence_vma         = valid && io.in.info.op === MOUOpType.sfence_vma && !sfence_vma_illegal

  io.out.flush      := valid
  io.out.fence_i    := fence_i
  io.out.sfence_vma := sfence_vma
  io.out.target     := io.in.pc + 4.U

  io.out.ex                        := DontCare
  io.out.ex.exception(illegalInst) := sfence_vma_illegal
  io.out.ex.tval(illegalInst)      := io.in.info.inst

}
