package cpu.defines

import chisel3._
import chisel3.util._

sealed trait Sv39Const extends CoreParameter {
  val PAddrBits = PADDR_WID
  val Level     = 3
  val offLen    = 12
  val ppn0Len   = 9
  val ppn1Len   = 9
  val ppn2Len   = PAddrBits - offLen - ppn0Len - ppn1Len // 2
  val ppnLen    = ppn2Len + ppn1Len + ppn0Len
  val vpn2Len   = 9
  val vpn1Len   = 9
  val vpn0Len   = 9
  val vpnLen    = vpn2Len + vpn1Len + vpn0Len

  //val paddrLen = PAddrBits
  //val vaddrLen = VAddrBits
  val satpLen     = XLEN
  val satpModeLen = 4
  val asidLen     = 16
  val flagLen     = 8

  val ptEntryLen = XLEN
  val satpResLen = XLEN - ppnLen - satpModeLen - asidLen
  //val vaResLen = 25 // unused
  //val paResLen = 25 // unused
  val pteResLen = XLEN - ppnLen - 2 - flagLen

  def vaBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
    val off  = UInt(offLen.W)
  }

  def vaBundle2 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vaBundle3 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vpnBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
  }

  def paBundle = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
    val off  = UInt(offLen.W)
  }

  def paBundle2 = new Bundle {
    val ppn = UInt(ppnLen.W)
    val off = UInt(offLen.W)
  }

  def paddrApply(ppn: UInt, vpnn: UInt): UInt = {
    Cat(Cat(ppn, vpnn), 0.U(3.W))
  }

  def pteBundle = new Bundle {
    val reserved = UInt(pteResLen.W)
    val ppn      = UInt(ppnLen.W)
    val rsw      = UInt(2.W)
    val flag = new Bundle {
      val d = UInt(1.W)
      val a = UInt(1.W)
      val g = UInt(1.W)
      val u = UInt(1.W)
      val x = UInt(1.W)
      val w = UInt(1.W)
      val r = UInt(1.W)
      val v = UInt(1.W)
    }
  }

  def satpBundle = new Bundle {
    val mode = UInt(satpModeLen.W)
    val asid = UInt(asidLen.W)
    val res  = UInt(satpResLen.W)
    val ppn  = UInt(ppnLen.W)
  }

  def flagBundle = new Bundle {
    val d = Bool()
    val a = Bool()
    val g = Bool()
    val u = Bool()
    val x = Bool()
    val w = Bool()
    val r = Bool()
    val v = Bool()
  }

  def maskPaddr(ppn: UInt, vaddr: UInt, mask: UInt) = {
    MaskData(vaddr, Cat(ppn, 0.U(offLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(offLen.W)))
  }

  def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
    (Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h1ff".U(vpn2Len.W), mask) & vpn)
  }

}

class Tlb_ICache extends Bundle {
  val fill           = Input(Bool())
  val icache_is_save = Input(Bool())
  val uncached       = Output(Bool())

  val translation_ok = Output(Bool())
  val hit            = Output(Bool())
  val tag            = Output(UInt(20.W))
  val pa             = Output(UInt(32.W))
}

class Tlb_DCache extends Bundle {
  val fill           = Input(Bool())
  val dcache_is_idle = Input(Bool())
  val uncached       = Output(Bool())
  val tlb1_ok        = Output(Bool())

  val translation_ok = Output(Bool())
  val hit            = Output(Bool())
  val tag            = Output(UInt(20.W))
  val pa             = Output(UInt(32.W))
}
