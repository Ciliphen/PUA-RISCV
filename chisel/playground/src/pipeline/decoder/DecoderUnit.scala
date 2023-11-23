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

  if (config.decoderNum == 2) {
    val issue = Module(new Issue()).io
    issue.allow_to_go          := io.ctrl.allow_to_go
    issue.instFifo             := io.instFifo.info
    io.instFifo.allow_to_go(1) := issue.inst1.allow_to_go
    for (i <- 0 until (config.decoderNum)) {
      decoder(i).io.in.inst      := inst(i)
      issue.decodeInst(i)        := inst_info(i)
      issue.execute(i).mem_wreg  := io.forward(i).mem_wreg
      issue.execute(i).reg_waddr := io.forward(i).exe.waddr
    }
    io.executeStage.inst1.allow_to_go := issue.inst1.allow_to_go
  }
  val decoder     = Seq.fill(config.decoderNum)(Module(new Decoder()))
  val jumpCtrl    = Module(new JumpCtrl()).io
  val forwardCtrl = Module(new ForwardCtrl()).io

  io.regfile(0).src1.raddr := decoder(0).io.out.inst_info.reg1_raddr
  io.regfile(0).src2.raddr := decoder(0).io.out.inst_info.reg2_raddr
  if (config.decoderNum == 2) {
    io.regfile(1).src1.raddr := decoder(1).io.out.inst_info.reg1_raddr
    io.regfile(1).src2.raddr := decoder(1).io.out.inst_info.reg2_raddr
  }
  forwardCtrl.in.forward    := io.forward
  forwardCtrl.in.regfile    := io.regfile // TODO:这里的连接可能有问题
  jumpCtrl.in.allow_to_go   := io.ctrl.allow_to_go
  jumpCtrl.in.decoded_inst0 := decoder(0).io.out.inst_info
  jumpCtrl.in.forward       := io.forward
  jumpCtrl.in.pc            := io.instFifo.inst(0).pc
  jumpCtrl.in.reg1_data     := io.regfile(0).src1.rdata

  val inst0_branch = jumpCtrl.out.jump || io.bpu.pred_branch

  io.fetchUnit.branch := inst0_branch
  io.fetchUnit.target := Mux(io.bpu.pred_branch, io.bpu.branch_target, jumpCtrl.out.jump_target)

  io.instFifo.allow_to_go(0) := io.ctrl.allow_to_go
  io.bpu.id_allow_to_go      := io.ctrl.allow_to_go
  io.bpu.pc                  := io.instFifo.inst(0).pc
  io.bpu.decoded_inst0       := decoder(0).io.out.inst_info
  io.bpu.pht_index           := io.instFifo.inst(0).pht_index

  io.ctrl.inst0.src1.ren   := decoder(0).io.out.inst_info.reg1_ren
  io.ctrl.inst0.src1.raddr := decoder(0).io.out.inst_info.reg1_raddr
  io.ctrl.inst0.src2.ren   := decoder(0).io.out.inst_info.reg2_ren
  io.ctrl.inst0.src2.raddr := decoder(0).io.out.inst_info.reg2_raddr
  io.ctrl.branch           := inst0_branch

  val pc        = io.instFifo.inst.map(_.pc)
  val inst      = io.instFifo.inst.map(_.inst)
  val inst_info = decoder.map(_.io.out.inst_info)
  val priv_mode = io.csr.priv_mode

  for (i <- 0 until (config.decoderNum)) {
    decoder(i).io.in.inst := inst(i)
  }

  val int = WireInit(0.U(INT_WID.W))
  BoringUtils.addSink(int, "intDecoderUnit")
  io.executeStage.inst0.ex.interrupt.zip(int.asBools).map { case (x, y) => x := y }

  io.executeStage.inst0.valid     := !io.instFifo.info.empty
  io.executeStage.inst0.pc        := pc(0)
  io.executeStage.inst0.inst_info := inst_info(0)
  io.executeStage.inst0.src_info.src1_data := Mux(
    inst_info(0).reg1_ren,
    forwardCtrl.out.inst(0).src1.rdata,
    SignedExtend(pc(0), INST_ADDR_WID)
  )
  io.executeStage.inst0.src_info.src2_data := Mux(
    inst_info(0).reg2_ren,
    forwardCtrl.out.inst(0).src2.rdata,
    decoder(0).io.out.inst_info.imm
  )
  io.executeStage.inst0.ex.exception.map(_                := false.B)
  io.executeStage.inst0.ex.exception(illegalInstr)        := !inst_info(0).inst_valid
  io.executeStage.inst0.ex.exception(instrAccessFault)    := io.instFifo.inst(0).acc_err
  io.executeStage.inst0.ex.exception(instrAddrMisaligned) := io.instFifo.inst(0).addr_err
  io.executeStage.inst0.ex.exception(breakPoint) := inst_info(0).inst(31, 20) === privEbreak &&
    inst_info(0).op === CSROpType.jmp
  io.executeStage.inst0.ex.exception(ecallM) := inst_info(0).inst(31, 20) === privEcall &&
    inst_info(0).op === CSROpType.jmp && priv_mode === ModeM
  io.executeStage.inst0.ex.exception(ecallS) := inst_info(0).inst(31, 20) === privEcall &&
    inst_info(0).op === CSROpType.jmp && priv_mode === ModeS
  io.executeStage.inst0.ex.exception(ecallU) := inst_info(0).inst(31, 20) === privEcall &&
    inst_info(0).op === CSROpType.jmp && priv_mode === ModeU

  io.executeStage.inst0.ex.tval := Mux(
    io.executeStage.inst0.ex.exception(instrAccessFault) || io.executeStage.inst0.ex.exception(instrAddrMisaligned),
    io.instFifo.inst(0).pc,
    0.U
  )

  io.executeStage.inst0.jb_info.jump_regiser     := jumpCtrl.out.jump_register
  io.executeStage.inst0.jb_info.branch_inst      := io.bpu.branch_inst
  io.executeStage.inst0.jb_info.pred_branch      := io.bpu.pred_branch
  io.executeStage.inst0.jb_info.branch_target    := io.bpu.branch_target
  io.executeStage.inst0.jb_info.update_pht_index := io.bpu.update_pht_index
  if (config.decoderNum == 2) {
    io.executeStage.inst1.valid     := !io.instFifo.info.almost_empty
    io.executeStage.inst1.pc        := pc(1)
    io.executeStage.inst1.inst_info := inst_info(1)
    io.executeStage.inst1.src_info.src1_data := Mux(
      inst_info(1).reg1_ren,
      forwardCtrl.out.inst(1).src1.rdata,
      SignedExtend(pc(1), INST_ADDR_WID)
    )
    io.executeStage.inst1.src_info.src2_data := Mux(
      inst_info(1).reg2_ren,
      forwardCtrl.out.inst(1).src2.rdata,
      decoder(1).io.out.inst_info.imm
    )
    io.executeStage.inst1.ex.exception.map(_                := false.B)
    io.executeStage.inst1.ex.exception(illegalInstr)        := !inst_info(1).inst_valid
    io.executeStage.inst1.ex.exception(instrAccessFault)    := io.instFifo.inst(1).acc_err
    io.executeStage.inst1.ex.exception(instrAddrMisaligned) := io.instFifo.inst(1).addr_err
    io.executeStage.inst1.ex.exception(breakPoint) := inst_info(1).inst(31, 20) === privEbreak &&
    inst_info(1).op === CSROpType.jmp
    io.executeStage.inst1.ex.exception(ecallM) := inst_info(1).inst(31, 20) === privEcall &&
    inst_info(1).op === CSROpType.jmp && priv_mode === ModeM
    io.executeStage.inst1.ex.exception(ecallS) := inst_info(1).inst(31, 20) === privEcall &&
    inst_info(1).op === CSROpType.jmp && priv_mode === ModeS
    io.executeStage.inst1.ex.exception(ecallU) := inst_info(1).inst(31, 20) === privEcall &&
    inst_info(1).op === CSROpType.jmp && priv_mode === ModeU

    io.executeStage.inst1.ex.tval := Mux(
      io.executeStage.inst1.ex.exception(instrAccessFault) || io.executeStage.inst1.ex.exception(instrAddrMisaligned),
      io.instFifo.inst(1).pc,
      0.U
    )
  } else {
    io.executeStage.inst1 := DontCare
  }
}
