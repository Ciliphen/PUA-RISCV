package cpu.defines

import chisel3._
import chisel3.util._

object Privileged extends HasInstrType with CoreParameter {
  def ECALL      = BitPat("b000000000000_00000_000_00000_1110011")
  def EBREAK     = BitPat("b000000000001_00000_000_00000_1110011")
  def MRET       = BitPat("b001100000010_00000_000_00000_1110011")
  def SRET       = BitPat("b000100000010_00000_000_00000_1110011")
  def SFANCE_VMA = BitPat("b0001001_?????_?????_000_00000_1110011")
  def FENCE      = BitPat("b????????????_?????_000_?????_0001111")
  def WFI        = BitPat("b0001000_00101_00000_000_00000_1110011")

  val table_s = Array(
    SRET       -> List(InstrI, FuType.csr, CSROpType.sret),
    SFANCE_VMA -> List(InstrR, FuType.mou, MOUOpType.sfence_vma)
  )

  val table = Array(
    ECALL  -> List(InstrI, FuType.csr, CSROpType.ecall),
    EBREAK -> List(InstrI, FuType.csr, CSROpType.ebreak),
    MRET   -> List(InstrI, FuType.csr, CSROpType.mret),
    FENCE  -> List(InstrS, FuType.mou, MOUOpType.fence), // nop    InstrS -> !wen
    WFI    -> List(InstrI, FuType.alu, ALUOpType.add) // nop    rd = x0
  ) ++ (if (cpuConfig.hasSMode) table_s else Array.empty)
}
