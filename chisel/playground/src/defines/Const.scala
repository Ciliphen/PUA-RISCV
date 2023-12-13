package cpu.defines

import chisel3._
import chisel3.util._
import cpu.CpuConfig

trait CoreParameter {
  def config    = new CpuConfig
  val XLEN      = if (config.isRV32) 32 else 64
  val VADDR_WID = 32
  val PADDR_WID = 32
}

trait Constants extends CoreParameter {
  // 全局
  val PC_WID  = XLEN
  val PC_INIT = "h60000000".U(PC_WID.W)

  val SINGLE_ISSUE = false.B
  val DUAL_ISSUE   = true.B

  val EXT_INT_WID = 3
  val INT_WID     = 12
  val EXC_WID     = 16

  // div
  val DIV_CTRL_WID         = 2
  val DIV_FREE             = 0.U(DIV_CTRL_WID.W)
  val DIV_BY_ZERO          = 1.U(DIV_CTRL_WID.W)
  val DIV_ON               = 2.U(DIV_CTRL_WID.W)
  val DIV_END              = 3.U(DIV_CTRL_WID.W)
  val DIV_RESULT_READY     = true.B
  val DIV_RESULT_NOT_READY = false.B
  val DIV_START            = true.B
  val DIV_STOP             = false.B

  // inst rom
  val INST_WID      = XLEN
  val INST_ADDR_WID = XLEN

  // data ram
  val DATA_ADDR_WID = XLEN

  // GPR RegFile
  val AREG_NUM     = 32
  val REG_ADDR_WID = 5
  val DATA_WID     = XLEN
}

trait AXIConst {
  // AXI
  val BURST_FIXED    = 0
  val BURST_INCR     = 1
  val BURST_WRAP     = 2
  val BURST_RESERVED = 3

  val RESP_OKEY   = 0
  val RESP_EXOKEY = 1
  val RESP_SLVERR = 2
  val RESP_DECERR = 3

  val AXI_ID_WID    = 4
  val AXI_ADDR_WID  = 32
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
  val DecodeDefault = List(InstrN, FuType.csr, CSROpType.jmp)
  def DecodeTable = RVIInstr.table ++ (if (config.hasMExtension) RVMInstr.table else Array.empty) ++
    Priviledged.table ++
    RVAInstr.table ++
    RVZicsrInstr.table ++ RVZifenceiInstr.table
}

object AddressSpace extends CoreParameter {
  // (start, size)
  // address out of MMIO will be considered as DRAM
  def mmio = List(
    (0x30000000L, 0x10000000L), // internal devices, such as CLINT and PLIC
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
