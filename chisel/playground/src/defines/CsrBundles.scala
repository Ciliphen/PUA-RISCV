package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines.Const._

class Mstatus extends Bundle {
  val sd    = Bool()
  val wpri0 = UInt(25.W)
  val mbe   = Bool()
  val sbe   = Bool()
  val sxl   = UInt(2.W)
  val uxl   = UInt(2.W)
  val wpri1 = UInt(9.W)
  val tsr   = Bool()
  val tw    = Bool()
  val tvm   = Bool()
  val mxr   = Bool()
  val sum   = Bool()
  val mprv  = Bool()
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val mpp   = UInt(2.W)
  val vs    = UInt(2.W)
  val spp   = Bool()
  val mpie  = Bool()
  val ube   = Bool()
  val spie  = Bool()
  val wpri2 = Bool()
  val mie   = Bool()
  val wpri3 = Bool()
  val sie   = Bool()
  val wpri4 = Bool()
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

object Priv {
  def u       = "b00".U
  def s       = "b01".U
  def h       = "b10".U
  def m       = "b11".U
  def apply() = UInt(2.W)
}
