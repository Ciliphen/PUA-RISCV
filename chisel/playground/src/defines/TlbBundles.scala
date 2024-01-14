package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.CpuConfig

trait Sv39Const extends CoreParameter {
  val PAddrBits     = PADDR_WID // 32
  val level         = 3
  val pageOffsetLen = 12 // 页面大小为4KB，对应的偏移量长度为12位
  val ppn0Len       = 9
  val ppn1Len       = 9
  val ppn2Len       = PAddrBits - pageOffsetLen - ppn0Len - ppn1Len // 2
  val ppnLen        = ppn2Len + ppn1Len + ppn0Len // 20
  val vpn2Len       = 9
  val vpn1Len       = 9
  val vpn0Len       = 9
  val vpnLen        = vpn2Len + vpn1Len + vpn0Len // 27

  val satpLen     = XLEN
  val satpModeLen = 4
  val asidLen     = 16
  val flagLen     = 8

  val ptEntryLen = XLEN
  val satpResLen = XLEN - ppnLen - satpModeLen - asidLen
  val pteResLen  = XLEN - ppnLen - 2 - flagLen

  val cacheTagLen = PADDR_WID - pageOffsetLen
  require(ppnLen == cacheTagLen)

  def vaBundle = new Bundle {
    val vpn2   = UInt(vpn2Len.W)
    val vpn1   = UInt(vpn1Len.W)
    val vpn0   = UInt(vpn0Len.W)
    val offset = UInt(pageOffsetLen.W)
  }

  def vaBundle2 = new Bundle {
    val vpn    = UInt(vpnLen.W)
    val offset = UInt(pageOffsetLen.W)
  }

  def vaBundle3 = new Bundle {
    val vpn    = UInt(vpnLen.W)
    val offset = UInt(pageOffsetLen.W)
  }

  def vpnBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
  }

  def paBundle = new Bundle {
    val ppn2   = UInt(ppn2Len.W)
    val ppn1   = UInt(ppn1Len.W)
    val ppn0   = UInt(ppn0Len.W)
    val offset = UInt(pageOffsetLen.W)
  }

  def paBundle2 = new Bundle {
    val ppn    = UInt(ppnLen.W)
    val offset = UInt(pageOffsetLen.W)
  }

  def ppnBundle = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
  }

  def paddrApply(ppn: UInt, vpnn: UInt): UInt = {
    Cat(Cat(ppn, vpnn), 0.U(3.W))
  }

  def pteBundle = new Bundle {
    val reserved = UInt(pteResLen.W)
    val ppn      = UInt(ppnLen.W)
    val rsw      = UInt(2.W)
    val flag = new Bundle {
      val d = Bool()
      val a = Bool()
      val g = Bool()
      val u = Bool()
      val x = Bool()
      val w = Bool()
      val r = Bool()
      val v = Bool()
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
    MaskData(vaddr, Cat(ppn, 0.U(pageOffsetLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(pageOffsetLen.W)))
  }

  def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
    (Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h1ff".U(vpn2Len.W), mask) & vpn)
  }

}

trait HasTlbConst extends Sv39Const {
  def tlbBundle = new Bundle {
    val vpn     = UInt(vpnLen.W)
    val asid    = UInt(asidLen.W)
    val flag    = flagBundle
    val ppn     = UInt(ppnLen.W)
    val pteaddr = UInt(PAddrBits.W)
  }

}
