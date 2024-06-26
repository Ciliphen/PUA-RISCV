package cpu.defines

import chisel3._
import chisel3.util._
import cpu.CpuConfig

trait CoreParameter {
  def cpuConfig = new CpuConfig
  val XLEN      = if (cpuConfig.isRV32) 32 else 64
  val VADDR_WID = if (cpuConfig.isRV32) 32 else 39
  val PADDR_WID = 32
}

trait Constants extends CoreParameter {
  // 全局
  val PC_INIT = "h80000000".U(XLEN.W)

  val INT_WID = 12
  val EXC_WID = 16

  // inst rom
  val INST_WID = 32

  // GPR RegFile
  val AREG_NUM     = 32
  val REG_ADDR_WID = 5
}

trait AXIConst extends Constants {
  // AXI
  val BURST_FIXED    = 0
  val BURST_INCR     = 1
  val BURST_WRAP     = 2
  val BURST_RESERVED = 3

  val RESP_OKAY = 0

  val AXI_ID_WID    = 4
  val AXI_ADDR_WID  = PADDR_WID // 32
  val AXI_DATA_WID  = 64
  val AXI_STRB_WID  = 8
  val AXI_RESP_WID  = 2
  val AXI_LEN_WID   = 8
  val AXI_SIZE_WID  = 3
  val AXI_BURST_WID = 2
  val AXI_LOCK_WID  = 2
  val AXI_CACHE_WID = 4
  val AXI_PROT_WID  = 3
}
object Const extends Constants with AXIConst with HasExceptionNO

object Instructions extends HasInstrType with CoreParameter {
  def NOP           = 0x00000013.U
  val DecodeDefault = List(InstrN, FuType.alu, ALUOpType.add)
  def DecodeTable = RVIInstr.table ++
    (if (cpuConfig.hasMExtension) RVMInstr.table else Array.empty) ++
    (if (cpuConfig.hasAExtension) RVAInstr.table else Array.empty) ++
    Privileged.table ++
    RVZicsrInstr.table ++
    RVZifenceiInstr.table
}

object AddressSpace extends CoreParameter {
  // (start, size)
  // address out of MMIO will be considered as DRAM
  def mmio = List(
    (0x00000000L, 0x40000000L), // internal devices, such as CLINT and PLIC
    (0x40000000L, 0x40000000L) // external devices
  )

  def isMMIO(addr: UInt) = mmio
    .map(range => {
      require(isPow2(range._2))
      val bits = log2Up(range._2)
      (addr ^ range._1.U)(PADDR_WID - 1, bits) === 0.U
    })
    .reduce(_ || _)
}
