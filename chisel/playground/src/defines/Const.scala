package cpu.defines

import chisel3._
import chisel3.util._
import cpu.CpuConfig

trait CoreParameter {
  def config = new CpuConfig
  val XLEN   = if (config.isRV32) 32 else 64
}

trait Constants extends CoreParameter {
  def config = new CpuConfig
  // 全局
  def PC_WID  = XLEN
  def PC_INIT = "h60000000".U(PC_WID.W)

  def EXT_INT_WID = 6
  def HILO_WID    = 64

  def WRITE_ENABLE  = true.B
  def WRITE_DISABLE = false.B
  def READ_ENABLE   = true.B
  def READ_DISABLE  = false.B
  def SINGLE_ISSUE  = false.B
  def DUAL_ISSUE    = true.B

  // div
  def DIV_CTRL_WID         = 2
  def DIV_FREE             = 0.U(DIV_CTRL_WID.W)
  def DIV_BY_ZERO          = 1.U(DIV_CTRL_WID.W)
  def DIV_ON               = 2.U(DIV_CTRL_WID.W)
  def DIV_END              = 3.U(DIV_CTRL_WID.W)
  def DIV_RESULT_READY     = true.B
  def DIV_RESULT_NOT_READY = false.B
  def DIV_START            = true.B
  def DIV_STOP             = false.B

  // inst rom
  def INST_WID      = 32
  def INST_ADDR_WID = PC_WID

  // data ram
  def DATA_ADDR_WID = PC_WID

  // GPR RegFile
  def AREG_NUM     = 32
  def REG_ADDR_WID = 5
  def DATA_WID     = XLEN

  // CSR寄存器
  // CSR Register (5.w), Select (3.w)
  def CSR_INDEX_ADDR    = "b00000_000".U(8.W) // 0,0
  def CSR_RANDOM_ADDR   = "b00001_000".U(8.W) // 1,0
  def CSR_ENTRYLO0_ADDR = "b00010_000".U(8.W) // 2,0
  def CSR_ENTRYLO1_ADDR = "b00011_000".U(8.W) // 3,0
  def CSR_CONTEXT_ADDR  = "b00100_000".U(8.W) // 4,0
  // def CSR_CONTEXT_CONFIG_ADDR = "b00100_001".U(8.W) // 4,1
  // def CSR_USER_LOCAL_ADDR     = "b00100_010".U(8.W) // 4,2
  def CSR_PAGE_MASK_ADDR = "b00101_000".U(8.W) // 5,0
  // def CSR_PAGE_GRAIN_ADDR     = "b00101_001".U(8.W) // 5,1
  def CSR_WIRED_ADDR = "b00110_000".U(8.W) // 6,0
  // def CSR_HWRENA_ADDR         = "b00111_000".U(8.W) // 7,0
  def CSR_BADV_ADDR    = "b01000_000".U(8.W) // 8,0
  def CSR_COUNT_ADDR   = "b01001_000".U(8.W) // 9,0  (sel保留 6or7)
  def CSR_ENTRYHI_ADDR = "b01010_000".U(8.W) // 10,0
  def CSR_COMPARE_ADDR = "b01011_000".U(8.W) // 11,0 (sel保留 6or7)
  def CSR_STATUS_ADDR  = "b01100_000".U(8.W) // 12,0
  // def CSR_INTCTL_ADDR         = "b01100_001".U(8.W) // 12,1
  // def CSR_SRSCTL_ADDR         = "b01100_010".U(8.W) // 12,2
  // def CSR_SRSMAP_ADDR         = "b01100_011".U(8.W) // 12,3
  def CSR_CAUSE_ADDR = "b01101_000".U(8.W) // 13,0
  def CSR_EPC_ADDR   = "b01110_000".U(8.W) // 14,0
  def CSR_PRID_ADDR  = "b01111_000".U(8.W) // 15,0
  def CSR_EBASE_ADDR = "b01111_001".U(8.W) // 15,1
  // def CSR_CDMMBASE_ADDR    = "b01111_010".U(8.W) // 15,2
  // def CSR_CMGCRBASE_ADDR   = "b01111_011".U(8.W) // 15,3
  def CSR_CONFIG_ADDR  = "b10000_000".U(8.W) // 16,0
  def CSR_CONFIG1_ADDR = "b10000_001".U(8.W) // 16,1
  // def CSR_CONFIG2_ADDR     = "b10000_010".U(8.W) // 16,2
  // def CSR_CONFIG3_ADDR     = "b10000_011".U(8.W) // 16,3
  // def CSR_CONFIG4_ADDR     = "b10000_100".U(8.W) // 16,4 (sel保留 6or7)
  // def CSR_LOAD_LINKED_ADDR = "b10001_000".U(8.W) // 17,0
  def CSR_TAGLO_ADDR     = "b11100_000".U(8.W) // 28,0
  def CSR_TAGHI_ADDR     = "b11101_000".U(8.W) // 29,0
  def CSR_ERROR_EPC_ADDR = "b11110_000".U(8.W) // 30,0

  def CSR_ADDR_WID = 8

  def PTEBASE_WID = 9
}

trait AXIConst {
  // AXI
  def BURST_FIXED    = 0
  def BURST_INCR     = 1
  def BURST_WRAP     = 2
  def BURST_RESERVED = 3

  def RESP_OKEY   = 0
  def RESP_EXOKEY = 1
  def RESP_SLVERR = 2
  def RESP_DECERR = 3
}
object Const extends Constants with AXIConst

object Instructions extends HasInstrType with CoreParameter {
  def NOP           = 0x00000013.U
  val DecodeDefault = List(InstrN, FuType.csr, CSROpType.jmp)
  def DecodeTable = RVIInstr.table ++ (if (config.hasMExtension) RVMInstr.table else Array.empty) ++
    Priviledged.table
}
