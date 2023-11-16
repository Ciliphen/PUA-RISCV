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

  val table: Array[(BitPat, List[BitPat])] = Array(
    BNE->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SNE,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BEQ->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SEQ,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BLT->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BLTU->      List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BGE->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SGE,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BGEU->      List(Y,N,N,Y,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SGEU,  N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),

    JAL->       List(Y,N,N,N,Y,N,N,N,N,N,N,A2_SIZE,A1_PC,  IMM_UJ,DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    JALR->      List(Y,N,N,N,N,Y,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AUIPC->     List(Y,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_PC,  IMM_U, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),

    LB->        List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LH->        List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LW->        List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LBU->       List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LHU->       List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SB->        List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    SH->        List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    SW->        List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),

    LUI->       List(Y,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_U, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ADDI->      List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTI ->     List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTIU->     List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ANDI->      List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_AND,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ORI->       List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_OR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XORI->      List(Y,N,N,N,N,N,N,Y,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_XOR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ADD->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SUB->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SUB,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLT->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTU->      List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AND->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_AND,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    OR->        List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_OR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XOR->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_XOR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLL->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRL->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRA->       List(Y,N,N,N,N,N,Y,Y,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SRA,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),

    FENCE->     List(Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.N,N,Y,N,N),

    ECALL->     List(Y,N,N,N,N,N,N,X,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    EBREAK->    List(Y,N,N,N,N,N,N,X,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    MRET->      List(Y,N,N,N,N,N,N,X,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    WFI->       List(Y,N,N,N,N,N,N,X,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    CSRRW->     List(Y,N,N,N,N,N,N,Y,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.W,N,N,N,N),
    CSRRS->     List(Y,N,N,N,N,N,N,Y,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.S,N,N,N,N),
    CSRRC->     List(Y,N,N,N,N,N,N,Y,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.C,N,N,N,N),
    CSRRWI->    List(Y,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.W,N,N,N,N),
    CSRRSI->    List(Y,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.S,N,N,N,N),
    CSRRCI->    List(Y,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.C,N,N,N,N))


  // val signals: List[UInt] = ListLookup(
  //   //@formatter:off
  //   inst,
  //   List(INST_INVALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_NOP, WRITE_DISABLE, WRA_X, IMM_N, DUAL_ISSUE),
  //   Array( /* inst_valid | reg1_ren | reg2_ren | fusel | op | reg_wen | reg_waddr | imm_type | dual_issue */
  //     // NOP
  //     NOP -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_NOP, WRITE_DISABLE, WRA_X, IMM_N, DUAL_ISSUE),
  //     // 位操作
  //     OR  -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_OR,  WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     AND -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_AND, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     XOR -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_XOR, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     NOR -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_NOR, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     // 移位
  //     SLLV -> List(INST_VALID, READ_ENABLE,  READ_ENABLE, FU_ALU, EXE_SLL, WRITE_ENABLE, WRA_T1, IMM_N,   DUAL_ISSUE),
  //     SRLV -> List(INST_VALID, READ_ENABLE,  READ_ENABLE, FU_ALU, EXE_SRL, WRITE_ENABLE, WRA_T1, IMM_N,   DUAL_ISSUE),
  //     SRAV -> List(INST_VALID, READ_ENABLE,  READ_ENABLE, FU_ALU, EXE_SRA, WRITE_ENABLE, WRA_T1, IMM_N,   DUAL_ISSUE),
  //     SLL  -> List(INST_VALID, READ_DISABLE, READ_ENABLE, FU_ALU, EXE_SLL, WRITE_ENABLE, WRA_T1, IMM_SHT, DUAL_ISSUE),
  //     SRL  -> List(INST_VALID, READ_DISABLE, READ_ENABLE, FU_ALU, EXE_SRL, WRITE_ENABLE, WRA_T1, IMM_SHT, DUAL_ISSUE),
  //     SRA  -> List(INST_VALID, READ_DISABLE, READ_ENABLE, FU_ALU, EXE_SRA, WRITE_ENABLE, WRA_T1, IMM_SHT, DUAL_ISSUE),
  //     // 立即数
  //     ORI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_OR,  WRITE_ENABLE, WRA_T2, IMM_LZE, DUAL_ISSUE),
  //     ANDI -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_AND, WRITE_ENABLE, WRA_T2, IMM_LZE, DUAL_ISSUE),
  //     XORI -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_XOR, WRITE_ENABLE, WRA_T2, IMM_LZE, DUAL_ISSUE),
  //     LUI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_OR,  WRITE_ENABLE, WRA_T2, IMM_HZE, DUAL_ISSUE),

  //     // Move
  //     MOVN -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_MOVN, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     MOVZ -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_MOVZ, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),

  //     // HI，LO的Move指令
  //     MFHI -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_MFHILO, EXE_MFHI, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     MFLO -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_MFHILO, EXE_MFLO, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     MTHI -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MTHILO, EXE_MTHI, WRITE_DISABLE, WRA_X, IMM_N, DUAL_ISSUE),
  //     MTLO -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MTHILO, EXE_MTLO, WRITE_DISABLE, WRA_X, IMM_N, DUAL_ISSUE),

  //     // C0的Move指令
  //     MFC0 -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_MFC0, WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     MTC0 -> List(INST_VALID, READ_DISABLE, READ_ENABLE,  FU_ALU, EXE_MTC0, WRITE_DISABLE, WRA_X,  IMM_N, SINGLE_ISSUE),

  //     // 比较指令
  //     SLT  -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_SLT,  WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     SLTU -> List(INST_VALID, READ_ENABLE, READ_ENABLE, FU_ALU, EXE_SLTU, WRITE_ENABLE, WRA_T1, IMM_N, DUAL_ISSUE),
  //     // 立即数
  //     SLTI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_SLT,  WRITE_ENABLE, WRA_T2, IMM_LSE, DUAL_ISSUE),
  //     SLTIU -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_SLTU, WRITE_ENABLE, WRA_T2, IMM_LSE, DUAL_ISSUE),

  //     // Trap
  //     TEQ   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TEQ,  WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TEQI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TEQ,  WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),
  //     TGE   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TGE,  WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TGEI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TGE,  WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),
  //     TGEIU -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TGEU, WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),
  //     TGEU  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TGEU, WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TLT   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TLT,  WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TLTI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TLT,  WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),
  //     TLTU  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TLTU, WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TLTIU -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TLTU, WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),
  //     TNE   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_EX, EXE_TNE,  WRITE_DISABLE, WRA_X, IMM_N,   DUAL_ISSUE),
  //     TNEI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_EX, EXE_TNE,  WRITE_DISABLE, WRA_X, IMM_LSE, DUAL_ISSUE),

  //     // 算术指令 
  //     ADD   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_ALU, EXE_ADD,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     ADDU  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_ALU, EXE_ADDU,  WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     SUB   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_ALU, EXE_SUB,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     SUBU  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_ALU, EXE_SUBU,  WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     MUL   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MUL,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     MULT  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MULT,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     MULTU -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MULTU, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     MADD  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MADD,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     MADDU -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MADDU, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     MSUB  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MSUB,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     MSUBU -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_MUL, EXE_MSUBU, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     DIV   -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_DIV, EXE_DIV,   WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     DIVU  -> List(INST_VALID, READ_ENABLE, READ_ENABLE,  FU_DIV, EXE_DIVU,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     CLO   -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_CLO,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     CLZ   -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_CLZ,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     // 立即数
  //     ADDI  -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_ADD,  WRITE_ENABLE, WRA_T2, IMM_LSE, DUAL_ISSUE),
  //     ADDIU -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_ADDU, WRITE_ENABLE, WRA_T2, IMM_LSE, DUAL_ISSUE),
  //     // 跳转指令
  //     J      -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_BR, EXE_J,      WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     JAL    -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_BR, EXE_JAL,    WRITE_ENABLE,  WRA_T3, IMM_N, DUAL_ISSUE),
  //     JR     -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_JR,     WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     JALR   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_JALR,   WRITE_ENABLE,  WRA_T1, IMM_N, DUAL_ISSUE),
  //     BEQ    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_BR, EXE_BEQ,    WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BNE    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_BR, EXE_BNE,    WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BGTZ   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BGTZ,   WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BLEZ   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BLEZ,   WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BGEZ   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BGEZ,   WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BGEZAL -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BGEZAL, WRITE_ENABLE,  WRA_T3, IMM_N, DUAL_ISSUE),
  //     BLTZ   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BLTZ,   WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     BLTZAL -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_BR, EXE_BLTZAL, WRITE_ENABLE,  WRA_T3, IMM_N, DUAL_ISSUE),

  //     // TLB
  //     TLBP  -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_TLBP,  WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     TLBR  -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_TLBR,  WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     TLBWI -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_TLBWI, WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     TLBWR -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_TLBWR, WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),

  //     // 例外指令
  //     SYSCALL -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_EX,  EXE_SYSCALL, WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     BREAK   -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_EX,  EXE_BREAK,   WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     ERET    -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_EX,  EXE_ERET,    WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //     WAIT    -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_NOP,     WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),

  //     // 访存指令
  //     LB    -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LB,  WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     LBU   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LBU, WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     LH    -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LH,  WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     LHU   -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LHU, WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     LW    -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LW,  WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     SB    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SB,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     SH    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SH,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     SW    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SW,  WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     LWL   -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_LWL, WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     LWR   -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_LWR, WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     SWL   -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SWL, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     SWR   -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SWR, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
      
  //     LL    -> List(INST_VALID, READ_ENABLE,  READ_DISABLE, FU_MEM, EXE_LL,  WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
  //     SC    -> List(INST_VALID, READ_ENABLE,  READ_ENABLE,  FU_MEM, EXE_SC,  WRITE_ENABLE,  WRA_T2, IMM_N, DUAL_ISSUE),
      
  //     SYNC  -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_EX,  EXE_NOP, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),
  //     PREF  -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_NOP, WRITE_ENABLE,  WRA_X,  IMM_N, DUAL_ISSUE),
  //     PREFX -> List(INST_VALID, READ_DISABLE, READ_DISABLE, FU_ALU, EXE_NOP, WRITE_DISABLE, WRA_X,  IMM_N, DUAL_ISSUE),

  //     // Cache
  //     CACHE -> List(INST_VALID, READ_ENABLE, READ_DISABLE, FU_ALU, EXE_CACHE, WRITE_DISABLE, WRA_X, IMM_N, SINGLE_ISSUE),
  //   ),
  //   // @formatter:on
  // )
  val inst_valid :: reg1_ren :: reg2_ren :: fusel :: op :: reg_wen :: reg_waddr_type :: imm_type :: dual_issue :: Nil =
    signals

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
