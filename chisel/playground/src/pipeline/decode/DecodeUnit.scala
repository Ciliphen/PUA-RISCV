package cpu.pipeline.decode

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.pipeline.execute.DecodeUnitExecuteUnit
import cpu.pipeline.fetch.IfIdData
import cpu.pipeline.execute

class DecodeUnitInstFifo(implicit val cpuConfig: CpuConfig) extends Bundle {
  val allow_to_go = Output(Vec(cpuConfig.decoderNum, Bool()))
  val inst        = Input(Vec(cpuConfig.decoderNum, new IfIdData()))
  val info = Input(new Bundle {
    val empty        = Bool()
    val almost_empty = Bool()
  })
}

class DataForwardToDecodeUnit extends Bundle {
  val exe     = new RegWrite()
  val is_load = Bool()
  val mem     = new RegWrite()
}

class DecoderBranchPredictorUnit extends Bundle {
  val bpuConfig = new BranchPredictorConfig()
  val pc        = Output(UInt(XLEN.W))
  val info      = Output(new Info())
  val pht_index = Output(UInt(bpuConfig.phtDepth.W))

  val branch_inst      = Input(Bool())
  val branch           = Input(Bool())
  val target           = Input(UInt(XLEN.W))
  val update_pht_index = Input(UInt(bpuConfig.phtDepth.W))
}

class DecodeUnit(implicit val cpuConfig: CpuConfig) extends Module with HasExceptionNO with HasCSRConst {
  val io = IO(new Bundle {
    // 输入
    val instFifo = new DecodeUnitInstFifo()
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
  val info = Wire(Vec(cpuConfig.decoderNum, new Info()))
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
    issue.execute(i).is_load   := io.forward(i).is_load
    issue.execute(i).reg_waddr := io.forward(i).exe.waddr
    io.regfile(i).src1.raddr   := info(i).src1_raddr
    io.regfile(i).src2.raddr   := info(i).src2_raddr
  }

  forwardCtrl.in.forward := io.forward
  forwardCtrl.in.regfile := io.regfile
  jumpCtrl.in.info       := info(0)
  jumpCtrl.in.forward    := io.forward
  jumpCtrl.in.pc         := pc(0)
  jumpCtrl.in.src_info   := io.executeStage.inst(0).src_info

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

  io.executeStage.jump_branch_info.jump_regiser     := jumpCtrl.out.jump_register
  io.executeStage.jump_branch_info.branch_inst      := io.bpu.branch_inst
  io.executeStage.jump_branch_info.pred_branch      := io.bpu.branch
  io.executeStage.jump_branch_info.branch_target    := io.bpu.target
  io.executeStage.jump_branch_info.update_pht_index := io.bpu.update_pht_index

  for (i <- 0 until (cpuConfig.commitNum)) {
    io.executeStage.inst(i).pc   := pc(i)
    io.executeStage.inst(i).info := info(i)
    io.executeStage.inst(i).src_info.src1_data := MuxCase(
      SignedExtend(pc(i), XLEN),
      Seq(
        info(i).src1_ren                      -> forwardCtrl.out.inst(i).src1.rdata,
        (info(i).inst(6, 0) === "b0110111".U) -> 0.U
      )
    )
    io.executeStage.inst(i).src_info.src2_data := Mux(
      info(i).src2_ren,
      forwardCtrl.out.inst(i).src2.rdata,
      info(i).imm
    )
    (0 until (INT_WID)).foreach(j => io.executeStage.inst(i).ex.interrupt(j) := io.csr.interrupt(j))
    io.executeStage.inst(i).ex.exception.map(_             := false.B)
    io.executeStage.inst(i).ex.exception(illegalInstr)     := !info(i).inst_legal
    io.executeStage.inst(i).ex.exception(instrAccessFault) := io.instFifo.inst(i).access_fault
    io.executeStage.inst(i).ex.exception(instrPageFault)   := io.instFifo.inst(i).page_fault
    io.executeStage.inst(i).ex.exception(instrAddrMisaligned) := pc(i)(log2Ceil(INST_WID / 8) - 1, 0).orR ||
    io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch
    io.executeStage.inst(i).ex.exception(breakPoint) := info(i).inst(31, 20) === privEbreak &&
    info(i).op === CSROpType.jmp && info(i).fusel === FuType.csr
    io.executeStage.inst(i).ex.exception(ecallM) := info(i).inst(31, 20) === privEcall &&
    info(i).op === CSROpType.jmp && mode === ModeM && info(i).fusel === FuType.csr
    io.executeStage.inst(i).ex.exception(ecallS) := info(i).inst(31, 20) === privEcall &&
    info(i).op === CSROpType.jmp && mode === ModeS && info(i).fusel === FuType.csr
    io.executeStage.inst(i).ex.exception(ecallU) := info(i).inst(31, 20) === privEcall &&
    info(i).op === CSROpType.jmp && mode === ModeU && info(i).fusel === FuType.csr
    io.executeStage.inst(i).ex.tval.map(_             := DontCare)
    io.executeStage.inst(i).ex.tval(instrPageFault)   := pc(i)
    io.executeStage.inst(i).ex.tval(instrAccessFault) := pc(i)
    io.executeStage.inst(i).ex.tval(illegalInstr)     := info(i).inst
    io.executeStage.inst(i).ex.tval(instrAddrMisaligned) := Mux(
      io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR && io.fetchUnit.branch,
      io.fetchUnit.target,
      pc(i)
    )
  }
}
