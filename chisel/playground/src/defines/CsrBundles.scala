package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines.Const._

class Mstatus extends Bundle {
  val sd = Output(Bool())

  val pad1 = if (XLEN == 64) Output(UInt(27.W)) else null
  val sxl  = if (XLEN == 64) Output(UInt(2.W)) else null
  val uxl  = if (XLEN == 64) Output(UInt(2.W)) else null
  val pad0 = if (XLEN == 64) Output(UInt(9.W)) else Output(UInt(8.W))

  val tsr  = Output(Bool())
  val tw   = Output(Bool())
  val tvm  = Output(Bool())
  val mxr  = Output(Bool())
  val sum  = Output(Bool())
  val mprv = Output(Bool())
  val xs   = Output(UInt(2.W))
  val fs   = Output(UInt(2.W))
  val mpp  = Output(UInt(2.W))
  val hpp  = Output(UInt(2.W))
  val spp  = Output(Bool())
  val pie  = new Priv
  val ie   = new Priv
}

class Misa extends Bundle {
  val mxl        = UInt(2.W)
  val blank      = UInt((XLEN - 28).W)
  val extensions = UInt(26.W)
}

class Mtvec extends Bundle {
  val base = UInt((XLEN - 2).W)
  val mode = UInt(2.W)
}

class Mcause extends Bundle {
  val interrupt = Bool()
  val excode    = UInt((XLEN - 1).W)
}

class Mip extends Bundle {
  val blank0 = UInt(52.W)
  val meip   = Bool()
  val blank1 = UInt(2.W)
  val seip   = Bool()
  val blank2 = UInt(2.W)
  val mtip   = Bool()
  val blank3 = UInt(2.W)
  val stip   = Bool()
  val blank4 = UInt(2.W)
  val msip   = Bool()
  val blank5 = UInt(2.W)
  val ssip   = Bool()
  val blank6 = UInt(2.W)
}

class Mie extends Bundle {
  val blank0 = UInt(52.W)
  val meie   = Bool()
  val blank1 = UInt(2.W)
  val seie   = Bool()
  val blank2 = UInt(2.W)
  val mtie   = Bool()
  val blank3 = UInt(2.W)
  val stie   = Bool()
  val blank4 = UInt(2.W)
  val msie   = Bool()
  val blank5 = UInt(2.W)
  val ssie   = Bool()
  val blank6 = UInt(2.W)
}

class Satp extends Bundle {
  val mode = UInt(4.W)
  val asid = UInt(16.W)
  val ppn  = UInt(44.W)
}

class Priv extends Bundle {
  val m = Output(Bool())
  val h = Output(Bool())
  val s = Output(Bool())
  val u = Output(Bool())
}

class Interrupt extends Bundle {
  val e = new Priv()
  val t = new Priv()
  val s = new Priv()
}

object Priv {
  def u       = "b00".U
  def s       = "b01".U
  def h       = "b10".U
  def m       = "b11".U
  def apply() = UInt(2.W)
}
