package cpu.pipeline.decoder

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Util._
import cpu.defines.Const._

class Decoder extends Module with HasInstrType {
  val io = IO(new Bundle {
    // inputs
    val in = Input(new Bundle {
      val inst = UInt(INST_WID.W)
    })
    // outputs
    val out = Output(new InstInfo())
  })

  val inst = io.in.inst
  val instrType :: fuType :: fuOpType :: Nil =
    ListLookup(inst, Instructions.DecodeDefault, Instructions.DecodeTable)

  val SrcTypeTable = Seq(
    InstrI  -> (SrcType.reg, SrcType.imm),
    InstrR  -> (SrcType.reg, SrcType.reg),
    InstrS  -> (SrcType.reg, SrcType.reg),
    InstrSA -> (SrcType.reg, SrcType.reg),
    InstrB  -> (SrcType.reg, SrcType.reg),
    InstrU  -> (SrcType.pc, SrcType.imm),
    InstrJ  -> (SrcType.pc, SrcType.imm),
    InstrN  -> (SrcType.pc, SrcType.imm)
  )
  val src1Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val src2Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rs, rt, rd) = (inst(19, 15), inst(24, 20), inst(11, 7))

  io.out.inst_valid := instrType === InstrN
  io.out.reg1_ren   := src1Type === SrcType.reg
  io.out.reg1_raddr := rs
  io.out.reg2_ren   := src2Type === SrcType.reg
  io.out.reg2_raddr := rt
  io.out.fusel      := fuType
  io.out.op         := fuOpType
  when(fuType === FuType.bru) {
    def isLink(reg: UInt) = (reg === 1.U || reg === 5.U)
    when(isLink(rd) && fuOpType === ALUOpType.jal) { io.out.op := ALUOpType.call }
    when(fuOpType === ALUOpType.jalr) {
      when(isLink(rs)) { io.out.op := ALUOpType.ret }
      when(isLink(rd)) { io.out.op := ALUOpType.call }
    }
  }
  io.out.reg_wen   := isrfWen(instrType)
  io.out.reg_waddr := Mux(isrfWen(instrType), rd, 0.U)
  io.out.imm := LookupTree(
    instrType,
    Seq(
      InstrI  -> signedExtend(inst(31, 20), XLEN),
      InstrS  -> signedExtend(Cat(inst(31, 25), inst(11, 7)), XLEN),
      InstrSA -> signedExtend(Cat(inst(31, 25), inst(11, 7)), XLEN),
      InstrB  -> signedExtend(Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)), XLEN),
      InstrU  -> signedExtend(Cat(inst(31, 12), 0.U(12.W)), XLEN),
      InstrJ  -> signedExtend(Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W)), XLEN)
    )
  )
  // io.out.csr_addr    := Cat(inst(15, 11), inst(2, 0))
  io.out.dual_issue  := false.B
  io.out.inst        := inst
  io.out.branch_link := VecInit(ALUOpType.jal, ALUOpType.jalr).contains(fuOpType)
  io.out.mem_addr    := DontCare
}
