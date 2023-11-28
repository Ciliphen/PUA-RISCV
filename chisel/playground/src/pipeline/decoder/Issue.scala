package cpu.pipeline.decoder

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.Instructions._
import cpu.CpuConfig

class Issue(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    // 输入
    val allow_to_go = Input(Bool())
    val instFifo = Input(new Bundle {
      val empty        = Bool()
      val almost_empty = Bool()
    })
    val decodeInst = Input(Vec(config.decoderNum, new InstInfo()))
    val execute    = Input(Vec(config.fuNum, new MemRead()))
    // 输出
    val inst1 = Output(new Bundle {
      val allow_to_go = Bool()
    })
  })

  if (config.decoderNum == 2) {
    val inst0 = io.decodeInst(0)
    val inst1 = io.decodeInst(1)

    // inst buffer是否存有至少2条指令
    val instFifo_invalid = io.instFifo.empty || io.instFifo.almost_empty

    // 结构冲突
    val mem_conflict    = inst0.fusel === FuType.lsu && inst1.fusel === FuType.lsu
    val mul_conflict    = inst0.fusel === FuType.mdu && inst1.fusel === FuType.mdu
    val div_conflict    = inst0.fusel === FuType.mdu && inst1.fusel === FuType.mdu
    val csr_conflict    = inst0.fusel === FuType.csr && inst1.fusel === FuType.csr
    val struct_conflict = mem_conflict || mul_conflict || div_conflict || csr_conflict

    // 写后读冲突
    val load_stall =
      io.execute(0).mem_wreg && (inst1.reg1_ren && inst1.reg1_raddr === io.execute(0).reg_waddr ||
        inst1.reg2_ren && inst1.reg2_raddr === io.execute(0).reg_waddr) ||
        io.execute(1).mem_wreg && (inst1.reg1_ren && inst1.reg1_raddr === io.execute(1).reg_waddr ||
          inst1.reg2_ren && inst1.reg2_raddr === io.execute(1).reg_waddr)
    val raw_reg =
      inst0.reg_wen && (inst0.reg_waddr === inst1.reg1_raddr && inst1.reg1_ren || inst0.reg_waddr === inst1.reg2_raddr && inst1.reg2_ren)
    val data_conflict = raw_reg || load_stall

    // 指令0为bru指令
    val inst0_is_bru_inst = ((inst0.fusel === FuType.bru && FuType.bru =/= FuType.alu) ||
      (inst0.fusel === FuType.alu && ALUOpType.isBru(io.decodeInst(0).op)))

    // 指令1是否允许执行
    io.inst1.allow_to_go :=
      io.allow_to_go && // 指令0允许执行
      !instFifo_invalid && // inst buffer存有至少2条指令
      !struct_conflict && // 无结构冲突
      !data_conflict && // 无写后读冲突
      !VecInit(FuType.mou).contains(io.decodeInst(1).fusel) && // 指令1不是mou指令
      !inst0_is_bru_inst // 指令0不是bru指令
  } else {
    io.inst1.allow_to_go := false.B
  }
}
