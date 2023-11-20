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
  def InstrA  = "b1110".U
  def InstrSA = "b1111".U // Atom Inst: SC

  def isrfWen(instrType: UInt): Bool = instrType(2)
}

object SrcType {
  def reg     = "b0".U
  def pc      = "b1".U
  def imm     = "b1".U
  def apply() = UInt(1.W)
}

object FuType {
  def num     = 5
  def alu     = "b000".U // arithmetic logic unit
  def lsu     = "b001".U // load store unit
  def mdu     = "b010".U // mul div unit
  def csr     = "b011".U // control status register
  def mou     = "b100".U // memory order unit
  def bru     = alu
  def apply() = UInt(log2Up(num).W)
}

object FuOpType {
  def apply() = UInt(7.W)
}

// BTB
object BTBtype {
  def B = "b00".U // branch
  def J = "b01".U // jump
  def I = "b10".U // indirect
  def R = "b11".U // return

  def apply() = UInt(2.W)
}

// ALU
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
  def isBranch(func:       UInt) = isBru(func) && !func(3)
  def isJump(func:         UInt) = isBru(func) && !isBranch(func)
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

  def needMemRead(func:  UInt): Bool = isLoad(func) || isAMO(func) || isLR(func)
  def needMemWrite(func: UInt): Bool = isStore(func) || isAMO(func) || isSC(func)

  def atomW = "010".U
  def atomD = "011".U
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
  def isW(op:       UInt) = op(3)
}

// csr unit
object CSROpType {
  def jmp  = "b000".U
  def wrt  = "b001".U
  def set  = "b010".U
  def clr  = "b011".U
  def wrti = "b101".U
  def seti = "b110".U
  def clri = "b111".U
}

trait HasExceptionNO {
  def instrAddrMisaligned = 0
  def instrAccessFault    = 1
  def illegalInstr        = 2
  def breakPoint          = 3
  def loadAddrMisaligned  = 4
  def loadAccessFault     = 5
  def storeAddrMisaligned = 6
  def storeAccessFault    = 7
  def ecallU              = 8
  def ecallS              = 9
  def ecallM              = 11
  def instrPageFault      = 12
  def loadPageFault       = 13
  def storePageFault      = 15

  val ExcPriority = Seq(
    breakPoint, // TODO: different BP has different priority
    instrPageFault,
    instrAccessFault,
    illegalInstr,
    instrAddrMisaligned,
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

object Causes {
  val misaligned_fetch         = 0x0
  val fetch_access             = 0x1
  val illegal_instruction      = 0x2
  val breakpoint               = 0x3
  val misaligned_load          = 0x4
  val load_access              = 0x5
  val misaligned_store         = 0x6
  val store_access             = 0x7
  val user_ecall               = 0x8
  val supervisor_ecall         = 0x9
  val virtual_supervisor_ecall = 0xa
  val machine_ecall            = 0xb
  val fetch_page_fault         = 0xc
  val load_page_fault          = 0xd
  val store_page_fault         = 0xf
  val fetch_guest_page_fault   = 0x14
  val load_guest_page_fault    = 0x15
  val virtual_instruction      = 0x16
  val store_guest_page_fault   = 0x17
  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
    res += misaligned_fetch
    res += fetch_access
    res += illegal_instruction
    res += breakpoint
    res += misaligned_load
    res += load_access
    res += misaligned_store
    res += store_access
    res += user_ecall
    res += supervisor_ecall
    res += virtual_supervisor_ecall
    res += machine_ecall
    res += fetch_page_fault
    res += load_page_fault
    res += store_page_fault
    res += fetch_guest_page_fault
    res += load_guest_page_fault
    res += virtual_instruction
    res += store_guest_page_fault
    res.toArray
  }
}
object CSRs {
  val fflags         = 0x1
  val frm            = 0x2
  val fcsr           = 0x3
  val vstart         = 0x8
  val vxsat          = 0x9
  val vxrm           = 0xa
  val vcsr           = 0xf
  val seed           = 0x15
  val jvt            = 0x17
  val cycle          = 0xc00
  val time           = 0xc01
  val instret        = 0xc02
  val hpmcounter3    = 0xc03
  val hpmcounter4    = 0xc04
  val hpmcounter5    = 0xc05
  val hpmcounter6    = 0xc06
  val hpmcounter7    = 0xc07
  val hpmcounter8    = 0xc08
  val hpmcounter9    = 0xc09
  val hpmcounter10   = 0xc0a
  val hpmcounter11   = 0xc0b
  val hpmcounter12   = 0xc0c
  val hpmcounter13   = 0xc0d
  val hpmcounter14   = 0xc0e
  val hpmcounter15   = 0xc0f
  val hpmcounter16   = 0xc10
  val hpmcounter17   = 0xc11
  val hpmcounter18   = 0xc12
  val hpmcounter19   = 0xc13
  val hpmcounter20   = 0xc14
  val hpmcounter21   = 0xc15
  val hpmcounter22   = 0xc16
  val hpmcounter23   = 0xc17
  val hpmcounter24   = 0xc18
  val hpmcounter25   = 0xc19
  val hpmcounter26   = 0xc1a
  val hpmcounter27   = 0xc1b
  val hpmcounter28   = 0xc1c
  val hpmcounter29   = 0xc1d
  val hpmcounter30   = 0xc1e
  val hpmcounter31   = 0xc1f
  val vl             = 0xc20
  val vtype          = 0xc21
  val vlenb          = 0xc22
  val sstatus        = 0x100
  val sedeleg        = 0x102
  val sideleg        = 0x103
  val sie            = 0x104
  val stvec          = 0x105
  val scounteren     = 0x106
  val senvcfg        = 0x10a
  val sstateen0      = 0x10c
  val sstateen1      = 0x10d
  val sstateen2      = 0x10e
  val sstateen3      = 0x10f
  val sscratch       = 0x140
  val sepc           = 0x141
  val scause         = 0x142
  val stval          = 0x143
  val sip            = 0x144
  val stimecmp       = 0x14d
  val siselect       = 0x150
  val sireg          = 0x151
  val stopei         = 0x15c
  val satp           = 0x180
  val scontext       = 0x5a8
  val vsstatus       = 0x200
  val vsie           = 0x204
  val vstvec         = 0x205
  val vsscratch      = 0x240
  val vsepc          = 0x241
  val vscause        = 0x242
  val vstval         = 0x243
  val vsip           = 0x244
  val vstimecmp      = 0x24d
  val vsiselect      = 0x250
  val vsireg         = 0x251
  val vstopei        = 0x25c
  val vsatp          = 0x280
  val hstatus        = 0x600
  val hedeleg        = 0x602
  val hideleg        = 0x603
  val hie            = 0x604
  val htimedelta     = 0x605
  val hcounteren     = 0x606
  val hgeie          = 0x607
  val hvien          = 0x608
  val hvictl         = 0x609
  val henvcfg        = 0x60a
  val hstateen0      = 0x60c
  val hstateen1      = 0x60d
  val hstateen2      = 0x60e
  val hstateen3      = 0x60f
  val htval          = 0x643
  val hip            = 0x644
  val hvip           = 0x645
  val hviprio1       = 0x646
  val hviprio2       = 0x647
  val htinst         = 0x64a
  val hgatp          = 0x680
  val hcontext       = 0x6a8
  val hgeip          = 0xe12
  val vstopi         = 0xeb0
  val scountovf      = 0xda0
  val stopi          = 0xdb0
  val utvt           = 0x7
  val unxti          = 0x45
  val uintstatus     = 0x46
  val uscratchcsw    = 0x48
  val uscratchcswl   = 0x49
  val stvt           = 0x107
  val snxti          = 0x145
  val sintstatus     = 0x146
  val sscratchcsw    = 0x148
  val sscratchcswl   = 0x149
  val mtvt           = 0x307
  val mnxti          = 0x345
  val mintstatus     = 0x346
  val mscratchcsw    = 0x348
  val mscratchcswl   = 0x349
  val mstatus        = 0x300
  val misa           = 0x301
  val medeleg        = 0x302
  val mideleg        = 0x303
  val mie            = 0x304
  val mtvec          = 0x305
  val mcounteren     = 0x306
  val mvien          = 0x308
  val mvip           = 0x309
  val menvcfg        = 0x30a
  val mstateen0      = 0x30c
  val mstateen1      = 0x30d
  val mstateen2      = 0x30e
  val mstateen3      = 0x30f
  val mcountinhibit  = 0x320
  val mscratch       = 0x340
  val mepc           = 0x341
  val mcause         = 0x342
  val mtval          = 0x343
  val mip            = 0x344
  val mtinst         = 0x34a
  val mtval2         = 0x34b
  val miselect       = 0x350
  val mireg          = 0x351
  val mtopei         = 0x35c
  val pmpcfg0        = 0x3a0
  val pmpcfg1        = 0x3a1
  val pmpcfg2        = 0x3a2
  val pmpcfg3        = 0x3a3
  val pmpcfg4        = 0x3a4
  val pmpcfg5        = 0x3a5
  val pmpcfg6        = 0x3a6
  val pmpcfg7        = 0x3a7
  val pmpcfg8        = 0x3a8
  val pmpcfg9        = 0x3a9
  val pmpcfg10       = 0x3aa
  val pmpcfg11       = 0x3ab
  val pmpcfg12       = 0x3ac
  val pmpcfg13       = 0x3ad
  val pmpcfg14       = 0x3ae
  val pmpcfg15       = 0x3af
  val pmpaddr0       = 0x3b0
  val pmpaddr1       = 0x3b1
  val pmpaddr2       = 0x3b2
  val pmpaddr3       = 0x3b3
  val pmpaddr4       = 0x3b4
  val pmpaddr5       = 0x3b5
  val pmpaddr6       = 0x3b6
  val pmpaddr7       = 0x3b7
  val pmpaddr8       = 0x3b8
  val pmpaddr9       = 0x3b9
  val pmpaddr10      = 0x3ba
  val pmpaddr11      = 0x3bb
  val pmpaddr12      = 0x3bc
  val pmpaddr13      = 0x3bd
  val pmpaddr14      = 0x3be
  val pmpaddr15      = 0x3bf
  val pmpaddr16      = 0x3c0
  val pmpaddr17      = 0x3c1
  val pmpaddr18      = 0x3c2
  val pmpaddr19      = 0x3c3
  val pmpaddr20      = 0x3c4
  val pmpaddr21      = 0x3c5
  val pmpaddr22      = 0x3c6
  val pmpaddr23      = 0x3c7
  val pmpaddr24      = 0x3c8
  val pmpaddr25      = 0x3c9
  val pmpaddr26      = 0x3ca
  val pmpaddr27      = 0x3cb
  val pmpaddr28      = 0x3cc
  val pmpaddr29      = 0x3cd
  val pmpaddr30      = 0x3ce
  val pmpaddr31      = 0x3cf
  val pmpaddr32      = 0x3d0
  val pmpaddr33      = 0x3d1
  val pmpaddr34      = 0x3d2
  val pmpaddr35      = 0x3d3
  val pmpaddr36      = 0x3d4
  val pmpaddr37      = 0x3d5
  val pmpaddr38      = 0x3d6
  val pmpaddr39      = 0x3d7
  val pmpaddr40      = 0x3d8
  val pmpaddr41      = 0x3d9
  val pmpaddr42      = 0x3da
  val pmpaddr43      = 0x3db
  val pmpaddr44      = 0x3dc
  val pmpaddr45      = 0x3dd
  val pmpaddr46      = 0x3de
  val pmpaddr47      = 0x3df
  val pmpaddr48      = 0x3e0
  val pmpaddr49      = 0x3e1
  val pmpaddr50      = 0x3e2
  val pmpaddr51      = 0x3e3
  val pmpaddr52      = 0x3e4
  val pmpaddr53      = 0x3e5
  val pmpaddr54      = 0x3e6
  val pmpaddr55      = 0x3e7
  val pmpaddr56      = 0x3e8
  val pmpaddr57      = 0x3e9
  val pmpaddr58      = 0x3ea
  val pmpaddr59      = 0x3eb
  val pmpaddr60      = 0x3ec
  val pmpaddr61      = 0x3ed
  val pmpaddr62      = 0x3ee
  val pmpaddr63      = 0x3ef
  val mseccfg        = 0x747
  val tselect        = 0x7a0
  val tdata1         = 0x7a1
  val tdata2         = 0x7a2
  val tdata3         = 0x7a3
  val tinfo          = 0x7a4
  val tcontrol       = 0x7a5
  val mcontext       = 0x7a8
  val mscontext      = 0x7aa
  val dcsr           = 0x7b0
  val dpc            = 0x7b1
  val dscratch0      = 0x7b2
  val dscratch1      = 0x7b3
  val mcycle         = 0xb00
  val minstret       = 0xb02
  val mhpmcounter3   = 0xb03
  val mhpmcounter4   = 0xb04
  val mhpmcounter5   = 0xb05
  val mhpmcounter6   = 0xb06
  val mhpmcounter7   = 0xb07
  val mhpmcounter8   = 0xb08
  val mhpmcounter9   = 0xb09
  val mhpmcounter10  = 0xb0a
  val mhpmcounter11  = 0xb0b
  val mhpmcounter12  = 0xb0c
  val mhpmcounter13  = 0xb0d
  val mhpmcounter14  = 0xb0e
  val mhpmcounter15  = 0xb0f
  val mhpmcounter16  = 0xb10
  val mhpmcounter17  = 0xb11
  val mhpmcounter18  = 0xb12
  val mhpmcounter19  = 0xb13
  val mhpmcounter20  = 0xb14
  val mhpmcounter21  = 0xb15
  val mhpmcounter22  = 0xb16
  val mhpmcounter23  = 0xb17
  val mhpmcounter24  = 0xb18
  val mhpmcounter25  = 0xb19
  val mhpmcounter26  = 0xb1a
  val mhpmcounter27  = 0xb1b
  val mhpmcounter28  = 0xb1c
  val mhpmcounter29  = 0xb1d
  val mhpmcounter30  = 0xb1e
  val mhpmcounter31  = 0xb1f
  val mhpmevent3     = 0x323
  val mhpmevent4     = 0x324
  val mhpmevent5     = 0x325
  val mhpmevent6     = 0x326
  val mhpmevent7     = 0x327
  val mhpmevent8     = 0x328
  val mhpmevent9     = 0x329
  val mhpmevent10    = 0x32a
  val mhpmevent11    = 0x32b
  val mhpmevent12    = 0x32c
  val mhpmevent13    = 0x32d
  val mhpmevent14    = 0x32e
  val mhpmevent15    = 0x32f
  val mhpmevent16    = 0x330
  val mhpmevent17    = 0x331
  val mhpmevent18    = 0x332
  val mhpmevent19    = 0x333
  val mhpmevent20    = 0x334
  val mhpmevent21    = 0x335
  val mhpmevent22    = 0x336
  val mhpmevent23    = 0x337
  val mhpmevent24    = 0x338
  val mhpmevent25    = 0x339
  val mhpmevent26    = 0x33a
  val mhpmevent27    = 0x33b
  val mhpmevent28    = 0x33c
  val mhpmevent29    = 0x33d
  val mhpmevent30    = 0x33e
  val mhpmevent31    = 0x33f
  val mvendorid      = 0xf11
  val marchid        = 0xf12
  val mimpid         = 0xf13
  val mhartid        = 0xf14
  val mconfigptr     = 0xf15
  val mtopi          = 0xfb0
  val sieh           = 0x114
  val siph           = 0x154
  val stimecmph      = 0x15d
  val vsieh          = 0x214
  val vsiph          = 0x254
  val vstimecmph     = 0x25d
  val htimedeltah    = 0x615
  val hidelegh       = 0x613
  val hvienh         = 0x618
  val henvcfgh       = 0x61a
  val hviph          = 0x655
  val hviprio1h      = 0x656
  val hviprio2h      = 0x657
  val hstateen0h     = 0x61c
  val hstateen1h     = 0x61d
  val hstateen2h     = 0x61e
  val hstateen3h     = 0x61f
  val cycleh         = 0xc80
  val timeh          = 0xc81
  val instreth       = 0xc82
  val hpmcounter3h   = 0xc83
  val hpmcounter4h   = 0xc84
  val hpmcounter5h   = 0xc85
  val hpmcounter6h   = 0xc86
  val hpmcounter7h   = 0xc87
  val hpmcounter8h   = 0xc88
  val hpmcounter9h   = 0xc89
  val hpmcounter10h  = 0xc8a
  val hpmcounter11h  = 0xc8b
  val hpmcounter12h  = 0xc8c
  val hpmcounter13h  = 0xc8d
  val hpmcounter14h  = 0xc8e
  val hpmcounter15h  = 0xc8f
  val hpmcounter16h  = 0xc90
  val hpmcounter17h  = 0xc91
  val hpmcounter18h  = 0xc92
  val hpmcounter19h  = 0xc93
  val hpmcounter20h  = 0xc94
  val hpmcounter21h  = 0xc95
  val hpmcounter22h  = 0xc96
  val hpmcounter23h  = 0xc97
  val hpmcounter24h  = 0xc98
  val hpmcounter25h  = 0xc99
  val hpmcounter26h  = 0xc9a
  val hpmcounter27h  = 0xc9b
  val hpmcounter28h  = 0xc9c
  val hpmcounter29h  = 0xc9d
  val hpmcounter30h  = 0xc9e
  val hpmcounter31h  = 0xc9f
  val mstatush       = 0x310
  val midelegh       = 0x313
  val mieh           = 0x314
  val mvienh         = 0x318
  val mviph          = 0x319
  val menvcfgh       = 0x31a
  val mstateen0h     = 0x31c
  val mstateen1h     = 0x31d
  val mstateen2h     = 0x31e
  val mstateen3h     = 0x31f
  val miph           = 0x354
  val mhpmevent3h    = 0x723
  val mhpmevent4h    = 0x724
  val mhpmevent5h    = 0x725
  val mhpmevent6h    = 0x726
  val mhpmevent7h    = 0x727
  val mhpmevent8h    = 0x728
  val mhpmevent9h    = 0x729
  val mhpmevent10h   = 0x72a
  val mhpmevent11h   = 0x72b
  val mhpmevent12h   = 0x72c
  val mhpmevent13h   = 0x72d
  val mhpmevent14h   = 0x72e
  val mhpmevent15h   = 0x72f
  val mhpmevent16h   = 0x730
  val mhpmevent17h   = 0x731
  val mhpmevent18h   = 0x732
  val mhpmevent19h   = 0x733
  val mhpmevent20h   = 0x734
  val mhpmevent21h   = 0x735
  val mhpmevent22h   = 0x736
  val mhpmevent23h   = 0x737
  val mhpmevent24h   = 0x738
  val mhpmevent25h   = 0x739
  val mhpmevent26h   = 0x73a
  val mhpmevent27h   = 0x73b
  val mhpmevent28h   = 0x73c
  val mhpmevent29h   = 0x73d
  val mhpmevent30h   = 0x73e
  val mhpmevent31h   = 0x73f
  val mnscratch      = 0x740
  val mnepc          = 0x741
  val mncause        = 0x742
  val mnstatus       = 0x744
  val mseccfgh       = 0x757
  val mcycleh        = 0xb80
  val minstreth      = 0xb82
  val mhpmcounter3h  = 0xb83
  val mhpmcounter4h  = 0xb84
  val mhpmcounter5h  = 0xb85
  val mhpmcounter6h  = 0xb86
  val mhpmcounter7h  = 0xb87
  val mhpmcounter8h  = 0xb88
  val mhpmcounter9h  = 0xb89
  val mhpmcounter10h = 0xb8a
  val mhpmcounter11h = 0xb8b
  val mhpmcounter12h = 0xb8c
  val mhpmcounter13h = 0xb8d
  val mhpmcounter14h = 0xb8e
  val mhpmcounter15h = 0xb8f
  val mhpmcounter16h = 0xb90
  val mhpmcounter17h = 0xb91
  val mhpmcounter18h = 0xb92
  val mhpmcounter19h = 0xb93
  val mhpmcounter20h = 0xb94
  val mhpmcounter21h = 0xb95
  val mhpmcounter22h = 0xb96
  val mhpmcounter23h = 0xb97
  val mhpmcounter24h = 0xb98
  val mhpmcounter25h = 0xb99
  val mhpmcounter26h = 0xb9a
  val mhpmcounter27h = 0xb9b
  val mhpmcounter28h = 0xb9c
  val mhpmcounter29h = 0xb9d
  val mhpmcounter30h = 0xb9e
  val mhpmcounter31h = 0xb9f
  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
    res += fflags
    res += frm
    res += fcsr
    res += vstart
    res += vxsat
    res += vxrm
    res += vcsr
    res += seed
    res += jvt
    res += cycle
    res += time
    res += instret
    res += hpmcounter3
    res += hpmcounter4
    res += hpmcounter5
    res += hpmcounter6
    res += hpmcounter7
    res += hpmcounter8
    res += hpmcounter9
    res += hpmcounter10
    res += hpmcounter11
    res += hpmcounter12
    res += hpmcounter13
    res += hpmcounter14
    res += hpmcounter15
    res += hpmcounter16
    res += hpmcounter17
    res += hpmcounter18
    res += hpmcounter19
    res += hpmcounter20
    res += hpmcounter21
    res += hpmcounter22
    res += hpmcounter23
    res += hpmcounter24
    res += hpmcounter25
    res += hpmcounter26
    res += hpmcounter27
    res += hpmcounter28
    res += hpmcounter29
    res += hpmcounter30
    res += hpmcounter31
    res += vl
    res += vtype
    res += vlenb
    res += sstatus
    res += sedeleg
    res += sideleg
    res += sie
    res += stvec
    res += scounteren
    res += senvcfg
    res += sstateen0
    res += sstateen1
    res += sstateen2
    res += sstateen3
    res += sscratch
    res += sepc
    res += scause
    res += stval
    res += sip
    res += stimecmp
    res += siselect
    res += sireg
    res += stopei
    res += satp
    res += scontext
    res += vsstatus
    res += vsie
    res += vstvec
    res += vsscratch
    res += vsepc
    res += vscause
    res += vstval
    res += vsip
    res += vstimecmp
    res += vsiselect
    res += vsireg
    res += vstopei
    res += vsatp
    res += hstatus
    res += hedeleg
    res += hideleg
    res += hie
    res += htimedelta
    res += hcounteren
    res += hgeie
    res += hvien
    res += hvictl
    res += henvcfg
    res += hstateen0
    res += hstateen1
    res += hstateen2
    res += hstateen3
    res += htval
    res += hip
    res += hvip
    res += hviprio1
    res += hviprio2
    res += htinst
    res += hgatp
    res += hcontext
    res += hgeip
    res += vstopi
    res += scountovf
    res += stopi
    res += utvt
    res += unxti
    res += uintstatus
    res += uscratchcsw
    res += uscratchcswl
    res += stvt
    res += snxti
    res += sintstatus
    res += sscratchcsw
    res += sscratchcswl
    res += mtvt
    res += mnxti
    res += mintstatus
    res += mscratchcsw
    res += mscratchcswl
    res += mstatus
    res += misa
    res += medeleg
    res += mideleg
    res += mie
    res += mtvec
    res += mcounteren
    res += mvien
    res += mvip
    res += menvcfg
    res += mstateen0
    res += mstateen1
    res += mstateen2
    res += mstateen3
    res += mcountinhibit
    res += mscratch
    res += mepc
    res += mcause
    res += mtval
    res += mip
    res += mtinst
    res += mtval2
    res += miselect
    res += mireg
    res += mtopei
    res += pmpcfg0
    res += pmpcfg1
    res += pmpcfg2
    res += pmpcfg3
    res += pmpcfg4
    res += pmpcfg5
    res += pmpcfg6
    res += pmpcfg7
    res += pmpcfg8
    res += pmpcfg9
    res += pmpcfg10
    res += pmpcfg11
    res += pmpcfg12
    res += pmpcfg13
    res += pmpcfg14
    res += pmpcfg15
    res += pmpaddr0
    res += pmpaddr1
    res += pmpaddr2
    res += pmpaddr3
    res += pmpaddr4
    res += pmpaddr5
    res += pmpaddr6
    res += pmpaddr7
    res += pmpaddr8
    res += pmpaddr9
    res += pmpaddr10
    res += pmpaddr11
    res += pmpaddr12
    res += pmpaddr13
    res += pmpaddr14
    res += pmpaddr15
    res += pmpaddr16
    res += pmpaddr17
    res += pmpaddr18
    res += pmpaddr19
    res += pmpaddr20
    res += pmpaddr21
    res += pmpaddr22
    res += pmpaddr23
    res += pmpaddr24
    res += pmpaddr25
    res += pmpaddr26
    res += pmpaddr27
    res += pmpaddr28
    res += pmpaddr29
    res += pmpaddr30
    res += pmpaddr31
    res += pmpaddr32
    res += pmpaddr33
    res += pmpaddr34
    res += pmpaddr35
    res += pmpaddr36
    res += pmpaddr37
    res += pmpaddr38
    res += pmpaddr39
    res += pmpaddr40
    res += pmpaddr41
    res += pmpaddr42
    res += pmpaddr43
    res += pmpaddr44
    res += pmpaddr45
    res += pmpaddr46
    res += pmpaddr47
    res += pmpaddr48
    res += pmpaddr49
    res += pmpaddr50
    res += pmpaddr51
    res += pmpaddr52
    res += pmpaddr53
    res += pmpaddr54
    res += pmpaddr55
    res += pmpaddr56
    res += pmpaddr57
    res += pmpaddr58
    res += pmpaddr59
    res += pmpaddr60
    res += pmpaddr61
    res += pmpaddr62
    res += pmpaddr63
    res += mseccfg
    res += tselect
    res += tdata1
    res += tdata2
    res += tdata3
    res += tinfo
    res += tcontrol
    res += mcontext
    res += mscontext
    res += dcsr
    res += dpc
    res += dscratch0
    res += dscratch1
    res += mcycle
    res += minstret
    res += mhpmcounter3
    res += mhpmcounter4
    res += mhpmcounter5
    res += mhpmcounter6
    res += mhpmcounter7
    res += mhpmcounter8
    res += mhpmcounter9
    res += mhpmcounter10
    res += mhpmcounter11
    res += mhpmcounter12
    res += mhpmcounter13
    res += mhpmcounter14
    res += mhpmcounter15
    res += mhpmcounter16
    res += mhpmcounter17
    res += mhpmcounter18
    res += mhpmcounter19
    res += mhpmcounter20
    res += mhpmcounter21
    res += mhpmcounter22
    res += mhpmcounter23
    res += mhpmcounter24
    res += mhpmcounter25
    res += mhpmcounter26
    res += mhpmcounter27
    res += mhpmcounter28
    res += mhpmcounter29
    res += mhpmcounter30
    res += mhpmcounter31
    res += mhpmevent3
    res += mhpmevent4
    res += mhpmevent5
    res += mhpmevent6
    res += mhpmevent7
    res += mhpmevent8
    res += mhpmevent9
    res += mhpmevent10
    res += mhpmevent11
    res += mhpmevent12
    res += mhpmevent13
    res += mhpmevent14
    res += mhpmevent15
    res += mhpmevent16
    res += mhpmevent17
    res += mhpmevent18
    res += mhpmevent19
    res += mhpmevent20
    res += mhpmevent21
    res += mhpmevent22
    res += mhpmevent23
    res += mhpmevent24
    res += mhpmevent25
    res += mhpmevent26
    res += mhpmevent27
    res += mhpmevent28
    res += mhpmevent29
    res += mhpmevent30
    res += mhpmevent31
    res += mvendorid
    res += marchid
    res += mimpid
    res += mhartid
    res += mconfigptr
    res += mtopi
    res.toArray
  }
  val all32 = {
    val res = collection.mutable.ArrayBuffer(all: _*)
    res += sieh
    res += siph
    res += stimecmph
    res += vsieh
    res += vsiph
    res += vstimecmph
    res += htimedeltah
    res += hidelegh
    res += hvienh
    res += henvcfgh
    res += hviph
    res += hviprio1h
    res += hviprio2h
    res += hstateen0h
    res += hstateen1h
    res += hstateen2h
    res += hstateen3h
    res += cycleh
    res += timeh
    res += instreth
    res += hpmcounter3h
    res += hpmcounter4h
    res += hpmcounter5h
    res += hpmcounter6h
    res += hpmcounter7h
    res += hpmcounter8h
    res += hpmcounter9h
    res += hpmcounter10h
    res += hpmcounter11h
    res += hpmcounter12h
    res += hpmcounter13h
    res += hpmcounter14h
    res += hpmcounter15h
    res += hpmcounter16h
    res += hpmcounter17h
    res += hpmcounter18h
    res += hpmcounter19h
    res += hpmcounter20h
    res += hpmcounter21h
    res += hpmcounter22h
    res += hpmcounter23h
    res += hpmcounter24h
    res += hpmcounter25h
    res += hpmcounter26h
    res += hpmcounter27h
    res += hpmcounter28h
    res += hpmcounter29h
    res += hpmcounter30h
    res += hpmcounter31h
    res += mstatush
    res += midelegh
    res += mieh
    res += mvienh
    res += mviph
    res += menvcfgh
    res += mstateen0h
    res += mstateen1h
    res += mstateen2h
    res += mstateen3h
    res += miph
    res += mhpmevent3h
    res += mhpmevent4h
    res += mhpmevent5h
    res += mhpmevent6h
    res += mhpmevent7h
    res += mhpmevent8h
    res += mhpmevent9h
    res += mhpmevent10h
    res += mhpmevent11h
    res += mhpmevent12h
    res += mhpmevent13h
    res += mhpmevent14h
    res += mhpmevent15h
    res += mhpmevent16h
    res += mhpmevent17h
    res += mhpmevent18h
    res += mhpmevent19h
    res += mhpmevent20h
    res += mhpmevent21h
    res += mhpmevent22h
    res += mhpmevent23h
    res += mhpmevent24h
    res += mhpmevent25h
    res += mhpmevent26h
    res += mhpmevent27h
    res += mhpmevent28h
    res += mhpmevent29h
    res += mhpmevent30h
    res += mhpmevent31h
    res += mnscratch
    res += mnepc
    res += mncause
    res += mnstatus
    res += mseccfgh
    res += mcycleh
    res += minstreth
    res += mhpmcounter3h
    res += mhpmcounter4h
    res += mhpmcounter5h
    res += mhpmcounter6h
    res += mhpmcounter7h
    res += mhpmcounter8h
    res += mhpmcounter9h
    res += mhpmcounter10h
    res += mhpmcounter11h
    res += mhpmcounter12h
    res += mhpmcounter13h
    res += mhpmcounter14h
    res += mhpmcounter15h
    res += mhpmcounter16h
    res += mhpmcounter17h
    res += mhpmcounter18h
    res += mhpmcounter19h
    res += mhpmcounter20h
    res += mhpmcounter21h
    res += mhpmcounter22h
    res += mhpmcounter23h
    res += mhpmcounter24h
    res += mhpmcounter25h
    res += mhpmcounter26h
    res += mhpmcounter27h
    res += mhpmcounter28h
    res += mhpmcounter29h
    res += mhpmcounter30h
    res += mhpmcounter31h
    res.toArray
  }
}
