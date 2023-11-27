package cpu.defines

import chisel3._
import chisel3.util._
import cpu.CpuConfig

trait CoreParameter {
  def config = new CpuConfig
  val XLEN   = if (config.isRV32) 32 else 64
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

  // CSR寄存器
  // CSR Register (5.w), Select (3.w)
  val CSR_INDEX_ADDR    = "b00000_000".U(8.W) // 0,0
  val CSR_RANDOM_ADDR   = "b00001_000".U(8.W) // 1,0
  val CSR_ENTRYLO0_ADDR = "b00010_000".U(8.W) // 2,0
  val CSR_ENTRYLO1_ADDR = "b00011_000".U(8.W) // 3,0
  val CSR_CONTEXT_ADDR  = "b00100_000".U(8.W) // 4,0
  // val CSR_CONTEXT_CONFIG_ADDR = "b00100_001".U(8.W) // 4,1
  // val CSR_USER_LOCAL_ADDR     = "b00100_010".U(8.W) // 4,2
  val CSR_PAGE_MASK_ADDR = "b00101_000".U(8.W) // 5,0
  // val CSR_PAGE_GRAIN_ADDR     = "b00101_001".U(8.W) // 5,1
  val CSR_WIRED_ADDR = "b00110_000".U(8.W) // 6,0
  // val CSR_HWRENA_ADDR         = "b00111_000".U(8.W) // 7,0
  val CSR_BADV_ADDR    = "b01000_000".U(8.W) // 8,0
  val CSR_COUNT_ADDR   = "b01001_000".U(8.W) // 9,0  (sel保留 6or7)
  val CSR_ENTRYHI_ADDR = "b01010_000".U(8.W) // 10,0
  val CSR_COMPARE_ADDR = "b01011_000".U(8.W) // 11,0 (sel保留 6or7)
  val CSR_STATUS_ADDR  = "b01100_000".U(8.W) // 12,0
  // val CSR_INTCTL_ADDR         = "b01100_001".U(8.W) // 12,1
  // val CSR_SRSCTL_ADDR         = "b01100_010".U(8.W) // 12,2
  // val CSR_SRSMAP_ADDR         = "b01100_011".U(8.W) // 12,3
  val CSR_CAUSE_ADDR = "b01101_000".U(8.W) // 13,0
  val CSR_EPC_ADDR   = "b01110_000".U(8.W) // 14,0
  val CSR_PRID_ADDR  = "b01111_000".U(8.W) // 15,0
  val CSR_EBASE_ADDR = "b01111_001".U(8.W) // 15,1
  // val CSR_CDMMBASE_ADDR    = "b01111_010".U(8.W) // 15,2
  // val CSR_CMGCRBASE_ADDR   = "b01111_011".U(8.W) // 15,3
  val CSR_CONFIG_ADDR  = "b10000_000".U(8.W) // 16,0
  val CSR_CONFIG1_ADDR = "b10000_001".U(8.W) // 16,1
  // val CSR_CONFIG2_ADDR     = "b10000_010".U(8.W) // 16,2
  // val CSR_CONFIG3_ADDR     = "b10000_011".U(8.W) // 16,3
  // val CSR_CONFIG4_ADDR     = "b10000_100".U(8.W) // 16,4 (sel保留 6or7)
  // val CSR_LOAD_LINKED_ADDR = "b10001_000".U(8.W) // 17,0
  val CSR_TAGLO_ADDR     = "b11100_000".U(8.W) // 28,0
  val CSR_TAGHI_ADDR     = "b11101_000".U(8.W) // 29,0
  val CSR_ERROR_EPC_ADDR = "b11110_000".U(8.W) // 30,0

  val CSR_ADDR_WID = 8

  val PTEBASE_WID = 9
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
