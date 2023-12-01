package cpu.pipeline.decoder

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import cpu.defines._
import cpu.defines.Const._
import cpu.{BranchPredictorConfig, CpuConfig}
import cpu.pipeline.execute.DecoderUnitExecuteUnit
import cpu.pipeline.fetch.BufferUnit
import cpu.pipeline.execute

class InstFifoDecoderUnit(implicit val config: CpuConfig) extends Bundle {
  val allow_to_go = Output(Vec(config.decoderNum, Bool()))
  val inst        = Input(Vec(config.decoderNum, new BufferUnit()))
  val info = Input(new Bundle {
    val empty        = Bool()
    val almost_empty = Bool()
  })
}

class DataForwardToDecoderUnit extends Bundle {
  val exe      = new RegWrite()
  val mem_wreg = Bool()
  val mem      = new RegWrite()
}

class DecoderUnit(implicit val config: CpuConfig) extends Module with HasExceptionNO with HasCSRConst {
  val io = IO(new Bundle {
    // 输入
    val instFifo = new InstFifoDecoderUnit()
    val regfile  = Vec(config.decoderNum, new Src12Read())
    val forward  = Input(Vec(config.fuNum, new DataForwardToDecoderUnit()))
    val csr      = Input(new execute.CsrDecoderUnit())
    // 输出
    val fetchUnit = new Bundle {
      val branch = Output(Bool())
      val target = Output(UInt(PC_WID.W))
    }
    val bpu = new Bundle {
      val bpuConfig      = new BranchPredictorConfig()
      val pc             = Output(UInt(PC_WID.W))
      val decoded_inst0  = Output(new InstInfo())
      val id_allow_to_go = Output(Bool())
      val pht_index      = Output(UInt(bpuConfig.phtDepth.W))

      val branch_inst      = Input(Bool())
      val pred_branch      = Input(Bool())
      val branch_target    = Input(UInt(PC_WID.W))
      val update_pht_index = Input(UInt(bpuConfig.phtDepth.W))
    }
    val executeStage = Output(new DecoderUnitExecuteUnit())
    val ctrl         = new DecoderUnitCtrl()
  })

  val decoder     = Seq.fill(config.decoderNum)(Module(new Decoder()))
  val jumpCtrl    = Module(new JumpCtrl()).io
  val forwardCtrl = Module(new ForwardCtrl()).io
  val issue       = Module(new Issue()).io

  val pc        = io.instFifo.inst.map(_.pc)
  val inst      = io.instFifo.inst.map(_.inst)
  val info      = decoder.map(_.io.out.info)
  val priv_mode = io.csr.priv_mode

  issue.allow_to_go          := io.ctrl.allow_to_go
  issue.instFifo             := io.instFifo.info
  io.instFifo.allow_to_go(1) := issue.inst1.allow_to_go
  for (i <- 0 until (config.decoderNum)) {
    decoder(i).io.in.inst      := inst(i)
    issue.decodeInst(i)        := info(i)
    issue.execute(i).mem_wreg  := io.forward(i).mem_wreg
    issue.execute(i).reg_waddr := io.forward(i).exe.waddr
  }
  io.executeStage.inst1.allow_to_go := issue.inst1.allow_to_go

  io.regfile(0).src1.raddr := decoder(0).io.out.info.reg1_raddr
  io.regfile(0).src2.raddr := decoder(0).io.out.info.reg2_raddr
  io.regfile(1).src1.raddr := decoder(1).io.out.info.reg1_raddr
  io.regfile(1).src2.raddr := decoder(1).io.out.info.reg2_raddr
  forwardCtrl.in.forward   := io.forward
  forwardCtrl.in.regfile   := io.regfile // TODO:这里的连接可能有问题
  jumpCtrl.in.allow_to_go  := io.ctrl.allow_to_go
  jumpCtrl.in.info         := decoder(0).io.out.info
  jumpCtrl.in.forward      := io.forward
  jumpCtrl.in.pc           := io.instFifo.inst(0).pc
  jumpCtrl.in.src_info     := io.executeStage.inst0.src_info

  val inst0_branch = jumpCtrl.out.jump || io.bpu.pred_branch

  io.fetchUnit.branch := inst0_branch
  io.fetchUnit.target := Mux(io.bpu.pred_branch, io.bpu.branch_target, jumpCtrl.out.jump_target)

  io.instFifo.allow_to_go(0) := io.ctrl.allow_to_go
  io.bpu.id_allow_to_go      := io.ctrl.allow_to_go
  io.bpu.pc                  := io.instFifo.inst(0).pc
  io.bpu.decoded_inst0       := decoder(0).io.out.info
  io.bpu.pht_index           := io.instFifo.inst(0).pht_index

  io.ctrl.inst0.src1.ren   := decoder(0).io.out.info.reg1_ren
  io.ctrl.inst0.src1.raddr := decoder(0).io.out.info.reg1_raddr
  io.ctrl.inst0.src2.ren   := decoder(0).io.out.info.reg2_ren
  io.ctrl.inst0.src2.raddr := decoder(0).io.out.info.reg2_raddr
  io.ctrl.branch           := inst0_branch

  io.executeStage.inst0.pc         := pc(0)
  io.executeStage.inst0.info       := info(0)
  io.executeStage.inst0.info.valid := !io.instFifo.info.empty
  io.executeStage.inst0.src_info.src1_data := MuxCase(
    SignedExtend(pc(0), INST_ADDR_WID),
    Seq(
      info(0).reg1_ren                      -> forwardCtrl.out.inst(0).src1.rdata,
      (info(0).inst(6, 0) === "b0110111".U) -> 0.U
    )
  )
  io.executeStage.inst0.src_info.src2_data := Mux(
    info(0).reg2_ren,
    forwardCtrl.out.inst(0).src2.rdata,
    decoder(0).io.out.info.imm
  )
  (0 until (INT_WID)).foreach(i => io.executeStage.inst0.ex.interrupt(i) := io.csr.interrupt(i))
  io.executeStage.inst0.ex.exception.map(_             := false.B)
  io.executeStage.inst0.ex.exception(illegalInstr)     := !info(0).inst_legal
  io.executeStage.inst0.ex.exception(instrAccessFault) := io.instFifo.inst(0).acc_err
  io.executeStage.inst0.ex.exception(instrAddrMisaligned) := pc(0)(1, 0).orR ||
    io.fetchUnit.target(1, 0).orR && io.fetchUnit.branch
  io.executeStage.inst0.ex.exception(breakPoint) := info(0).inst(31, 20) === privEbreak &&
    info(0).op === CSROpType.jmp && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallM) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && priv_mode === ModeM && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallS) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && priv_mode === ModeS && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.exception(ecallU) := info(0).inst(31, 20) === privEcall &&
    info(0).op === CSROpType.jmp && priv_mode === ModeU && info(0).fusel === FuType.csr
  io.executeStage.inst0.ex.tval := MuxCase(
    0.U,
    Seq(
      pc(0)(1, 0).orR                                        -> pc(0),
      (io.fetchUnit.target(1, 0).orR && io.fetchUnit.branch) -> io.fetchUnit.target
    )
  )

  io.executeStage.inst0.jb_info.jump_regiser     := jumpCtrl.out.jump_register
  io.executeStage.inst0.jb_info.branch_inst      := io.bpu.branch_inst
  io.executeStage.inst0.jb_info.pred_branch      := io.bpu.pred_branch
  io.executeStage.inst0.jb_info.branch_target    := io.bpu.branch_target
  io.executeStage.inst0.jb_info.update_pht_index := io.bpu.update_pht_index

  io.executeStage.inst1.pc         := pc(1)
  io.executeStage.inst1.info       := info(1)
  io.executeStage.inst1.info.valid := !io.instFifo.info.almost_empty && !io.instFifo.info.empty
  io.executeStage.inst1.src_info.src1_data := MuxCase(
    SignedExtend(pc(1), INST_ADDR_WID),
    Seq(
      info(1).reg1_ren                      -> forwardCtrl.out.inst(1).src1.rdata,
      (info(1).inst(6, 0) === "b0110111".U) -> 0.U
    )
  )
  io.executeStage.inst1.src_info.src2_data := Mux(
    info(1).reg2_ren,
    forwardCtrl.out.inst(1).src2.rdata,
    decoder(1).io.out.info.imm
  )
  (0 until (INT_WID)).foreach(i => io.executeStage.inst1.ex.interrupt(i) := io.csr.interrupt(i))
  io.executeStage.inst1.ex.exception.map(_             := false.B)
  io.executeStage.inst1.ex.exception(illegalInstr)     := !info(1).inst_legal
  io.executeStage.inst1.ex.exception(instrAccessFault) := io.instFifo.inst(1).acc_err
  io.executeStage.inst1.ex.exception(instrAddrMisaligned) := pc(1)(1, 0).orR ||
    io.fetchUnit.target(1, 0).orR && io.fetchUnit.branch
  io.executeStage.inst1.ex.exception(breakPoint) := info(1).inst(31, 20) === privEbreak &&
    info(1).op === CSROpType.jmp && info(0).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallM) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && priv_mode === ModeM && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallS) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && priv_mode === ModeS && info(1).fusel === FuType.csr
  io.executeStage.inst1.ex.exception(ecallU) := info(1).inst(31, 20) === privEcall &&
    info(1).op === CSROpType.jmp && priv_mode === ModeU && info(1).fusel === FuType.csr

  io.executeStage.inst1.ex.tval := MuxCase(
    0.U,
    Seq(
      pc(1)(1, 0).orR                                        -> pc(1),
      (io.fetchUnit.target(1, 0).orR && io.fetchUnit.branch) -> io.fetchUnit.target
    )
  )

}
