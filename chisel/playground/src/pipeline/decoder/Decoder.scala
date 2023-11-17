package cpu.pipeline.decoder

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Decoder extends Module {
  val io = IO(new Bundle {
    // inputs
    val in = Input(new Bundle {
      val inst = UInt(INST_WID.W)
    })
    // outputs
    val out = Output(new InstInfo())
  })
  
  val inst = io.in.inst

  val instrType :: fuType :: fuOpType :: Nil = // insert Instructions.DecodeDefault when interrupt comes
    Instructions.DecodeDefault.zip(decodeList).map{case (inst, dec) => Mux(hasIntr || io.in.bits.exceptionVec(instrPageFault) || io.out.bits.cf.exceptionVec(instrAccessFault), inst, dec)}


  val rt    = inst(20, 16)
  val rd    = inst(15, 11)
  val sa    = inst(10, 6)
  val rs    = inst(25, 21)
  val imm16 = inst(15, 0)

  io.out.inst_valid := inst_valid
  io.out.reg1_ren   := reg1_ren
  io.out.reg1_raddr := rs
  io.out.reg2_ren   := reg2_ren
  io.out.reg2_raddr := rt
  io.out.fusel      := fusel
  io.out.op         := op
  io.out.reg_wen    := reg_wen
  io.out.reg_waddr := MuxLookup(reg_waddr_type, AREG_31)( // 取"b11111", 即31号寄存器
    Seq(
      WRA_T1 -> rd, // 取inst(15,11)
      WRA_T2 -> rt // 取inst(20,16)
    )
  )
  io.out.imm32 := MuxLookup(imm_type, Util.zeroExtend(sa))( // default IMM_SHT
    Seq(
      IMM_LSE -> Util.signedExtend(imm16),
      IMM_LZE -> Util.zeroExtend(imm16),
      IMM_HZE -> Cat(imm16, Fill(16, 0.U))
    )
  )
  io.out.csr_addr    := Cat(inst(15, 11), inst(2, 0))
  io.out.dual_issue  := dual_issue
  io.out.whilo       := VecInit(FU_MUL, FU_DIV, FU_MTHILO).contains(fusel) && op =/= EXE_MUL // MUL不写HILO
  io.out.inst        := inst
  io.out.wmem        := fusel === FU_MEM && (!reg_wen.orR || op === EXE_SC)
  io.out.rmem        := fusel === FU_MEM && reg_wen.orR
  io.out.mul         := fusel === FU_MUL
  io.out.div         := fusel === FU_DIV
  io.out.ifence      := inst(16) === 0.U && op === EXE_CACHE
  io.out.dfence      := inst(16) === 1.U && op === EXE_CACHE
  io.out.branch_link := VecInit(EXE_JAL, EXE_JALR, EXE_BGEZAL, EXE_BLTZAL).contains(op)
  io.out.mem_addr    := DontCare
  io.out.mem_wreg    := VecInit(EXE_LB, EXE_LBU, EXE_LH, EXE_LHU, EXE_LW, EXE_LL, EXE_LWL, EXE_LWR).contains(op)
}
