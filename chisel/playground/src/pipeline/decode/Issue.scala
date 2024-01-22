package cpu.pipeline.decode

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.Instructions._
import cpu.CpuConfig

class Issue(implicit val cpuConfig: CpuConfig) extends Module with HasCSRConst {
  val io = IO(new Bundle {
    // 输入
    val allow_to_go = Input(Bool())
    val instFifo = Input(new Bundle {
      val empty        = Bool()
      val almost_empty = Bool()
    })
    val decodeInst = Input(Vec(cpuConfig.decoderNum, new InstInfo()))
    val execute    = Input(Vec(cpuConfig.commitNum, new MemRead()))
    // 输出
    val inst1 = Output(new Bundle {
      val allow_to_go = Bool()
    })
  })

  if (cpuConfig.decoderNum == 2) {
    val inst = io.decodeInst

    // inst buffer是否存有至少2条指令
    val instFifo_invalid = io.instFifo.empty || io.instFifo.almost_empty

    // 结构冲突
    val lsu_conflict    = inst.map(_.fusel === FuType.lsu).reduce(_ && _) // 访存单元最大支持1条指令的load和store
    val mdu_conflict    = inst.map(_.fusel === FuType.mdu).reduce(_ && _) // 乘除单元最大支持1条指令的乘除法
    val csr_conflict    = inst.map(_.fusel === FuType.csr).reduce(_ && _) // csr单元最大支持1条指令的读写
    val struct_conflict = lsu_conflict || mdu_conflict || csr_conflict

    // 写后读冲突
    val load_stall = // inst1的源操作数需要经过load得到，但load指令还在exe级未访存
      io.execute(0).is_load && io.execute(0).reg_waddr.orR &&
        (inst(1).src1_ren && inst(1).src1_raddr === io.execute(0).reg_waddr ||
          inst(1).src2_ren && inst(1).src2_raddr === io.execute(0).reg_waddr) ||
        io.execute(1).is_load && io.execute(1).reg_waddr.orR &&
          (inst(1).src1_ren && inst(1).src1_raddr === io.execute(1).reg_waddr ||
            inst(1).src2_ren && inst(1).src2_raddr === io.execute(1).reg_waddr)
    val raw_reg = // inst1的源操作数是inst0的目的操作数
      inst(0).reg_wen && inst(0).reg_waddr.orR &&
        (inst(0).reg_waddr === inst(1).src1_raddr && inst(1).src1_ren ||
          inst(0).reg_waddr === inst(1).src2_raddr && inst(1).src2_ren)
    val data_conflict = raw_reg || load_stall

    // bru指令只能在inst0执行
    val is_bru = inst.map(_.fusel === FuType.bru).reduce(_ || _)

    // mou指令会导致流水线清空
    val is_mou = inst.map(_.fusel === FuType.mou).reduce(_ || _)

    // 写satp指令会导致流水线清空
    val write_satp = VecInit(
      Seq.tabulate(cpuConfig.commitNum)(i =>
        inst(i).fusel === FuType.csr && inst(i).op =/= CSROpType.jmp && inst(i).inst(31, 20) === Satp.U
      )
    ).asUInt.orR

    // uret、sret、mret指令会导致流水线清空
    val ret = inst(0).ret.asUInt.orR || inst(1).ret.asUInt.orR

    // 这些csr相关指令会导致流水线清空
    val is_some_csr_inst = write_satp || ret

    // 下面的情况只进行单发射
    val single_issue = is_mou || is_bru || is_some_csr_inst

    // 指令1是否允许执行
    io.inst1.allow_to_go :=
      io.allow_to_go && // 指令0允许执行
      !instFifo_invalid && // inst buffer存有至少2条指令
      !struct_conflict && // 无结构冲突
      !data_conflict && // 无写后读冲突
      !single_issue // 非单发射指令
  } else {
    io.inst1.allow_to_go := false.B
  }
}
