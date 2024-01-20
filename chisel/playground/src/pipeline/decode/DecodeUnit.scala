package cpu.pipeline.decode

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.pipeline.execute.DecodeUnitExecuteUnit
import cpu.pipeline.fetch.BufferUnit
import cpu.pipeline.execute

class InstFifoDecodeUnit(implicit val cpuConfig: CpuConfig) extends Bundle {
  val allow_to_go = Output(Vec(cpuConfig.decoderNum, Bool()))
  val inst        = Input(Vec(cpuConfig.decoderNum, new BufferUnit()))
  val info = Input(new Bundle {
    val empty        = Bool()
    val almost_empty = Bool()
  })
}

class DataForwardToDecodeUnit extends Bundle {
  val exe      = new RegWrite()
  val mem_wreg = Bool()
  val mem      = new RegWrite()
}

class DecoderBranchPredictorUnit extends Bundle {
  val bpuConfig = new BranchPredictorConfig()
  val pc        = Output(UInt(XLEN.W))
  val info      = Output(new InstInfo())
  val pht_index = Output(UInt(bpuConfig.phtDepth.W))

  val branch_inst      = Input(Bool())
  val branch           = Input(Bool())
  val target           = Input(UInt(XLEN.W))
  val update_pht_index = Input(UInt(bpuConfig.phtDepth.W))
}

class DecodeUnit(implicit val cpuConfig: CpuConfig) extends Module with HasExceptionNO with HasCSRConst {
  val io = IO(new Bundle {
    // 输入
    val instFifo = new InstFifoDecodeUnit()
    val regfile  = Vec(cpuConfig.decoderNum, new Src12Read())
    val forward  = Input(Vec(cpuConfig.commitNum, new DataForwardToDecodeUnit()))
    val csr      = Input(new execute.CsrDecodeUnit())
    // 输出
    val fetchUnit = new Bundle {
      val branch = Output(Bool())
      val target = Output(UInt(XLEN.W))
    }
    val bpu          = new DecoderBranchPredictorUnit()
    val executeStage = Output(new DecodeUnitExecuteUnit())
    val ctrl         = new DecodeUnitCtrl()
  })

  val decoder     = Seq.fill(cpuConfig.decoderNum)(Module(new Decoder()))
  val jumpCtrl    = Module(new JumpCtrl()).io
  val forwardCtrl = Module(new ForwardCtrl()).io
  val issue       = Module(new Issue()).io

  val pc   = io.instFifo.inst.map(_.pc)
  val inst = io.instFifo.inst.map(_.inst)
  val info = Wire(Vec(cpuConfig.decoderNum, new InstInfo()))
  val mode = io.csr.mode

  info          := decoder.map(_.io.out.info)
  info(0).valid := !io.instFifo.info.empty
  info(1).valid := !io.instFifo.info.almost_empty && !io.instFifo.info.empty

  issue.allow_to_go          := io.ctrl.allow_to_go
  issue.instFifo             := io.instFifo.info
  io.instFifo.allow_to_go(1) := issue.inst1.allow_to_go
  for (i <- 0 until (cpuConfig.decoderNum)) {
    decoder(i).io.in.inst      := inst(i)
    issue.decodeInst(i)        := info(i)
    issue.execute(i).mem_wreg  := io.forward(i).mem_wreg
    issue.execute(i).reg_waddr := io.forward(i).exe.waddr
  }

  io.regfile(0).src1.raddr := info(0).src1_raddr
  io.regfile(0).src2.raddr := info(0).src2_raddr
  io.regfile(1).src1.raddr := info(1).src1_raddr
  io.regfile(1).src2.raddr := info(1).src2_raddr
  forwardCtrl.in.forward   := io.forward
  forwardCtrl.in.regfile   := io.regfile
  jumpCtrl.in.info         := info(0)
  jumpCtrl.in.forward      := io.forward
  jumpCtrl.in.pc           := pc(0)
  jumpCtrl.in.src_info     := io.executeStage.inst0.src_info

  val inst0_branch = jumpCtrl.out.jump || io.bpu.branch

  io.fetchUnit.branch := inst0_branch && io.ctrl.allow_to_go
  io.fetchUnit.target := Mux(io.bpu.branch, io.bpu.target, jumpCtrl.out.jump_target)

  io.instFifo.allow_to_go(0) := io.ctrl.allow_to_go
  io.bpu.pc                  := pc(0)
  io.bpu.info                := info(0)
  io.bpu.pht_index           := io.instFifo.inst(0).pht_index

  io.ctrl.inst0.src1.ren   := info(0).src1_ren
  io.ctrl.inst0.src1.raddr := info(0).src1_raddr
  io.ctrl.inst0.src2.ren   := info(0).src2_ren
  io.ctrl.inst0.src2.raddr := info(0).src2_raddr
  io.ctrl.branch           := io.fetchUnit.branch

  io.executeStage.inst0.pc   := pc(0)
  io.executeStage.inst0.info := info(0)
  io.executeStage.inst0.src_info.src1_data := MuxCase(
    SignedExtend(pc(0), XLEN),
    Seq(
      info(0).src1_ren                      -> forwardCtrl.out.inst(0).src1.rdata,
      (info(0).inst(6, 0) === "b0110111".U) -> 0.U
    )
  )
  io.executeStage.inst0.src_info.src2_data := Mux(
    info(0).src2_ren,
    forwardCtrl.out.inst(0).src2.rdata,
    info(0).imm
  )
  (0 until (INT_WID)).foreach(i => io.executeStage.inst0.ex.interrupt(i) := io.csr.interrupt(i))
  io.executeStage.inst0.ex.exception.map(_             := false.B)
  io.executeStage.inst0.ex.exception(illegalInstr)     := !info(0).inst_legal
  io.executeStage.inst0.ex.exception(instrAccessFault) := io.instFifo.inst(0).access_fault
  io.executeStage.inst0.ex.exception(instrPageFault)   := io.instFifo.inst(0).page_fault
  io.executeStage.inst0.ex.exception(instrAddrMisaligned) := pc(0)(log2Ceil(INST_WID / 8) - 1, 0).orR ||
    io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch
  io.executeStage.inst0.ex.exception(breakPoint) := info(0).inst(31, 20) === privEbreak &&
    info(0).op === CSROpType.jmp && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallM) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && mode === ModeM && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallS) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && mode === ModeS && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallU) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && mode === ModeU && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.tval.map(_             := DontCare)
  io.executeStage.inst0.ex.tval(instrPageFault)   := pc(0)
  io.executeStage.inst0.ex.tval(instrAccessFault) := pc(0)
  io.executeStage.inst0.ex.tval(illegalInstr)     := info(0).inst
  io.executeStage.inst0.ex.tval(instrAddrMisaligned) := Mux(
    io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch,
    io.fetchUnit.target,
    pc(0)
  )

  io.executeStage.inst0.jb_info.jump_regiser     := jumpCtrl.out.jump_register
  io.executeStage.inst0.jb_info.branch_inst      := io.bpu.branch_inst
  io.executeStage.inst0.jb_info.pred_branch      := io.bpu.branch
  io.executeStage.inst0.jb_info.branch_target    := io.bpu.target
  io.executeStage.inst0.jb_info.update_pht_index := io.bpu.update_pht_index

  io.executeStage.inst1.pc   := pc(1)
  io.executeStage.inst1.info := info(1)
  io.executeStage.inst1.src_info.src1_data := MuxCase(
    SignedExtend(pc(1), XLEN),
    Seq(
      info(1).src1_ren                      -> forwardCtrl.out.inst(1).src1.rdata,
      (info(1).inst(6, 0) === "b0110111".U) -> 0.U
    )
  )
  io.executeStage.inst1.src_info.src2_data := Mux(
    info(1).src2_ren,
    forwardCtrl.out.inst(1).src2.rdata,
    info(1).imm
  )
  (0 until (INT_WID)).foreach(i => io.executeStage.inst1.ex.interrupt(i) := io.csr.interrupt(i))
  io.executeStage.inst1.ex.exception.map(_             := false.B)
  io.executeStage.inst1.ex.exception(illegalInstr)     := !info(1).inst_legal
  io.executeStage.inst1.ex.exception(instrAccessFault) := io.instFifo.inst(1).access_fault
  io.executeStage.inst1.ex.exception(instrPageFault)   := io.instFifo.inst(1).page_fault
  io.executeStage.inst1.ex.exception(instrAddrMisaligned) := pc(1)(log2Ceil(INST_WID / 8) - 1, 0).orR ||
    io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch
  io.executeStage.inst1.ex.exception(breakPoint) := info(1).inst(31, 20) === privEbreak &&
    info(1).op === CSROpType.jmp && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallM) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && mode === ModeM && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallS) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && mode === ModeS && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallU) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && mode === ModeU && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.tval.map(_             := DontCare)
  io.executeStage.inst1.ex.tval(instrPageFault)   := pc(1)
  io.executeStage.inst1.ex.tval(instrAccessFault) := pc(1)
  io.executeStage.inst1.ex.tval(illegalInstr)     := info(1).inst
  io.executeStage.inst1.ex.tval(instrAddrMisaligned) := Mux(
    io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch,
    io.fetchUnit.target,
    pc(1)
  )

}
