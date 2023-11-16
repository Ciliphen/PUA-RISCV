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
  def INST_defID    = false.B
  def INST_INdefID  = true.B
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

object FuType {
  def num     = 5
  def alu     = "b000".U
  def lsu     = "b001".U
  def mdu     = "b010".U
  def csr     = "b011".U
  def mou     = "b100".U
  def bru     = alu
  def apply() = UInt(log2Up(num).W)
}

object BTBtype {
  def B = "b00".U // branch
  def J = "b01".U // jump
  def I = "b10".U // indirect
  def R = "b11".U // return

  def apply() = UInt(2.W)
}

object ALUOpType {
  def add  = "b1000000".U
  def sll  = "b0000001".U
  def slt  = "b0000010".U
  def sltu = "b0000011".U
  def xor  = "b0000100".U
  def srl  = "b0000101".U
  def or   = "b0000110".U
  def and  = "b0000111".U
  def sub  = "b0001000".U
  def sra  = "b0001101".U

  def addw = "b1100000".U
  def subw = "b0101000".U
  def sllw = "b0100001".U
  def srlw = "b0100101".U
  def sraw = "b0101101".U

  def isWordOp(func: UInt) = func(5)

  def jal  = "b1011000".U
  def jalr = "b1011010".U
  def beq  = "b0010000".U
  def bne  = "b0010001".U
  def blt  = "b0010100".U
  def bge  = "b0010101".U
  def bltu = "b0010110".U
  def bgeu = "b0010111".U

  // for RAS
  def call = "b1011100".U
  def ret  = "b1011110".U

  def isAdd(func:          UInt) = func(6)
  def pcPlus2(func:        UInt) = func(5)
  def isBru(func:          UInt) = func(4)
  def isBranch(func:       UInt) = !func(3)
  def isJump(func:         UInt) = isBru(func) && !isBranch(func)
  def getBranchType(func:  UInt) = func(2, 1)
  def isBranchInvert(func: UInt) = func(0)
}

object LSUOpType { //TODO: refactor LSU fuop
  def lb  = "b0000000".U
  def lh  = "b0000001".U
  def lw  = "b0000010".U
  def ld  = "b0000011".U
  def lbu = "b0000100".U
  def lhu = "b0000101".U
  def lwu = "b0000110".U
  def sb  = "b0001000".U
  def sh  = "b0001001".U
  def sw  = "b0001010".U
  def sd  = "b0001011".U

  def lr      = "b0100000".U
  def sc      = "b0100001".U
  def amoswap = "b0100010".U
  def amoadd  = "b1100011".U
  def amoxor  = "b0100100".U
  def amoand  = "b0100101".U
  def amoor   = "b0100110".U
  def amomin  = "b0110111".U
  def amomax  = "b0110000".U
  def amominu = "b0110001".U
  def amomaxu = "b0110010".U

  def isAdd(func:   UInt) = func(6)
  def isAtom(func:  UInt): Bool = func(5)
  def isStore(func: UInt): Bool = func(3)
  def isLoad(func:  UInt): Bool = !isStore(func) & !isAtom(func)
  def isLR(func:    UInt): Bool = func === lr
  def isSC(func:    UInt): Bool = func === sc
  def isAMO(func:   UInt): Bool = isAtom(func) && !isLR(func) && !isSC(func)

  def needMemRead(func:  UInt): Bool = isLoad(func) || isAMO(func) || isLR(func)
  def needMemWrite(func: UInt): Bool = isStore(func) || isAMO(func) || isSC(func)

  def atomW = "010".U
  def atomD = "011".U
}

object MDUOpType {
  def mul    = "b0000".U
  def mulh   = "b0001".U
  def mulhsu = "b0010".U
  def mulhu  = "b0011".U
  def div    = "b0100".U
  def divu   = "b0101".U
  def rem    = "b0110".U
  def remu   = "b0111".U

  def mulw  = "b1000".U
  def divw  = "b1100".U
  def divuw = "b1101".U
  def remw  = "b1110".U
  def remuw = "b1111".U

  def isDiv(op:     UInt) = op(2)
  def isDivSign(op: UInt) = isDiv(op) && !op(0)
  def isW(op:       UInt) = op(3)
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
