package cpu.defines

import chisel3._
import chisel3.util._

trait HasInstrType {
  def InstrN  = "b0000".U
  def InstrI  = "b0100".U
  def InstrR  = "b0101".U
  def InstrS  = "b0010".U
  def InstrB  = "b0001".U
  def InstrU  = "b0110".U
  def InstrJ  = "b0111".U
  def InstrSA = "b1111".U // Atom Inst: SC

  def isRegWen(instrType: UInt): Bool = instrType(2)
}

object SrcType {
  def reg     = "b0".U
  def pc      = "b1".U
  def imm     = "b1".U
  def apply() = UInt(1.W)
}

object FuType {
  def num     = 6
  def alu     = "b000".U // arithmetic logic unit
  def lsu     = "b001".U // load store unit
  def mdu     = "b010".U // multiplication division unit
  def csr     = "b011".U // control status register
  def mou     = "b100".U // memory order unit
  def bru     = "b101".U // branch unit
  def apply() = UInt(log2Up(num).W)
}

object FuOpType {
  def apply() = UInt(7.W)
}

// ALU
object ALUOpType {
  def add  = "b100000".U
  def sll  = "b000001".U
  def slt  = "b000010".U
  def sltu = "b000011".U
  def xor  = "b000100".U
  def srl  = "b000101".U
  def or   = "b000110".U
  def and  = "b000111".U
  def sub  = "b001000".U
  def sra  = "b001101".U

  def addw = "b110000".U
  def subw = "b011000".U
  def sllw = "b010001".U
  def srlw = "b010101".U
  def sraw = "b011101".U

  def isWordOp(func: UInt) = func(4)
  def isAdd(func:    UInt) = func(5)
}

object BRUOpType {
  def jal  = "b1000".U
  def jalr = "b1010".U
  def beq  = "b0000".U
  def bne  = "b0001".U
  def blt  = "b0100".U
  def bge  = "b0101".U
  def bltu = "b0110".U
  def bgeu = "b0111".U

  def isBranch(func:       UInt) = !func(3)
  def isJump(func:         UInt) = !isBranch(func)
  def isAdd(func:          UInt) = isJump(func)
  def getBranchType(func:  UInt) = func(2, 1)
  def isBranchInvert(func: UInt) = func(0)
}

// load store unit
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
}

// memory order unit
object MOUOpType {
  def fence      = "b00".U
  def fencei     = "b01".U
  def sfence_vma = "b10".U
}

// mul div unit
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
  def isWordOp(op:  UInt) = op(3)
}

// csr unit
object CSROpType {
  def csrrw  = "b0001".U
  def csrrs  = "b0010".U
  def csrrc  = "b0011".U
  def csrrwi = "b0101".U
  def csrrsi = "b0110".U
  def csrrci = "b0111".U

  def ecall  = "b1000".U
  def ebreak = "b1001".U
  def mret   = "b1010".U
  def sret   = "b1011".U

  def isCSROp(op: UInt) = !op(3)
}

trait HasCSRConst {
  // User Trap Setup
  val Ustatus = 0x000
  val Uie     = 0x004
  val Utvec   = 0x005

  // User Trap Handling
  val Uscratch = 0x040
  val Uepc     = 0x041
  val Ucause   = 0x042
  val Utval    = 0x043
  val Uip      = 0x044

  // User Floating-Point CSRs (not implemented)
  val Fflags = 0x001
  val Frm    = 0x002
  val Fcsr   = 0x003

  // User Counter/Timers
  val Cycle   = 0xc00
  val Time    = 0xc01
  val Instret = 0xc02

  // Supervisor Trap Setup
  val Sstatus    = 0x100
  val Sedeleg    = 0x102
  val Sideleg    = 0x103
  val Sie        = 0x104
  val Stvec      = 0x105
  val Scounteren = 0x106

  // Supervisor Trap Handling
  val Sscratch = 0x140
  val Sepc     = 0x141
  val Scause   = 0x142
  val Stval    = 0x143
  val Sip      = 0x144

  // Supervisor Protection and Translation
  val Satp = 0x180

  // Machine Information Registers
  val Mvendorid = 0xf11
  val Marchid   = 0xf12
  val Mimpid    = 0xf13
  val Mhartid   = 0xf14

  // Machine Trap Setup
  val Mstatus    = 0x300
  val Misa       = 0x301
  val Medeleg    = 0x302
  val Mideleg    = 0x303
  val Mie        = 0x304
  val Mtvec      = 0x305
  val Mcounteren = 0x306

  // Machine Trap Handling
  val Mscratch = 0x340
  val Mepc     = 0x341
  val Mcause   = 0x342
  val Mtval    = 0x343
  val Mip      = 0x344

  // Machine Memory Protection
  // TBD
  val Pmpcfg0     = 0x3a0
  val Pmpcfg1     = 0x3a1
  val Pmpcfg2     = 0x3a2
  val Pmpcfg3     = 0x3a3
  val PmpaddrBase = 0x3b0

  // Machine Counter/Timers
  // Currently, NutCore uses perfcnt csr set instead of standard Machine Counter/Timers
  // 0xB80 - 0x89F are also used as perfcnt csr

  // Machine Counter Setup (not implemented)
  // Debug/Trace Registers (shared with Debug Mode) (simply implemented)
  val Tselect = 0x7a0
  val Tdata1  = 0x7a1
  // Debug Mode Registers (not implemented)

  def ModeM = 0x3.U
  def ModeH = 0x2.U
  def ModeS = 0x1.U
  def ModeU = 0x0.U

  def IRQ_UEIP = 0
  def IRQ_SEIP = 1
  def IRQ_MEIP = 3

  def IRQ_UTIP = 4
  def IRQ_STIP = 5
  def IRQ_MTIP = 7

  def IRQ_USIP = 8
  def IRQ_SSIP = 9
  def IRQ_MSIP = 11

  val IntPriority = Seq(
    IRQ_MEIP,
    IRQ_MSIP,
    IRQ_MTIP,
    IRQ_SEIP,
    IRQ_SSIP,
    IRQ_STIP,
    IRQ_UEIP,
    IRQ_USIP,
    IRQ_UTIP
  )
}

trait HasExceptionNO {
  def instAddrMisaligned  = 0
  def instAccessFault     = 1
  def illegalInst         = 2
  def breakPoint          = 3
  def loadAddrMisaligned  = 4
  def loadAccessFault     = 5
  def storeAddrMisaligned = 6
  def storeAccessFault    = 7
  def ecallU              = 8
  def ecallS              = 9
  def ecallM              = 11
  def instPageFault       = 12
  def loadPageFault       = 13
  def storePageFault      = 15

  val ExcPriority = Seq(
    breakPoint, // TODO: different BP has different priority
    instPageFault,
    instAccessFault,
    illegalInst,
    instAddrMisaligned,
    ecallM,
    ecallS,
    ecallU,
    storeAddrMisaligned,
    loadAddrMisaligned,
    storePageFault,
    loadPageFault,
    storeAccessFault,
    loadAccessFault
  )
}
