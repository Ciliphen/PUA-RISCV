package cpu.pipeline.writeback

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.decoder.RegWrite
import cpu.CpuConfig

class WriteBackUnit(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl           = new WriteBackCtrl()
    val writeBackStage = Input(new MemoryUnitWriteBackUnit())
    val regfile        = Output(Vec(cpuConfig.commitNum, new RegWrite()))
    val debug          = new DEBUG()
  })

  io.regfile(0).wen :=
    io.writeBackStage.inst0.info.valid &&
      io.writeBackStage.inst0.info.reg_wen &&
      io.ctrl.allow_to_go &&
      !(HasExcInt(io.writeBackStage.inst0.ex))
  io.regfile(0).waddr := io.writeBackStage.inst0.info.reg_waddr
  io.regfile(0).wdata := io.writeBackStage.inst0.rd_info.wdata(io.writeBackStage.inst0.info.fusel)

  io.regfile(1).wen :=
    io.writeBackStage.inst1.info.valid &&
      io.writeBackStage.inst1.info.reg_wen &&
      io.ctrl.allow_to_go &&
      !(HasExcInt(io.writeBackStage.inst0.ex)) &&
      !(HasExcInt(io.writeBackStage.inst1.ex))
  io.regfile(1).waddr := io.writeBackStage.inst1.info.reg_waddr
  io.regfile(1).wdata := io.writeBackStage.inst1.rd_info.wdata(io.writeBackStage.inst1.info.fusel)

  if (cpuConfig.hasCommitBuffer) {
    val buffer = Module(new CommitBuffer()).io
    buffer.enq(0).wb_pc       := io.writeBackStage.inst0.pc
    buffer.enq(0).wb_rf_wen   := io.writeBackStage.inst0.info.valid && io.ctrl.allow_to_go
    buffer.enq(0).wb_rf_wnum  := io.regfile(0).waddr
    buffer.enq(0).wb_rf_wdata := io.regfile(0).wdata
    buffer.enq(1).wb_pc       := io.writeBackStage.inst1.pc
    buffer.enq(1).wb_rf_wen   := io.writeBackStage.inst1.info.valid && io.ctrl.allow_to_go
    buffer.enq(1).wb_rf_wnum  := io.regfile(1).waddr
    buffer.enq(1).wb_rf_wdata := io.regfile(1).wdata
    buffer.flush              := io.ctrl.do_flush

    io.debug.wb_pc       := buffer.deq.wb_pc
    io.debug.wb_rf_wen   := buffer.deq.wb_rf_wen
    io.debug.wb_rf_wnum  := buffer.deq.wb_rf_wnum
    io.debug.wb_rf_wdata := buffer.deq.wb_rf_wdata
  } else {
    io.debug.wb_pc := Mux(
      clock.asBool,
      io.writeBackStage.inst0.pc,
      Mux(
        HasExcInt(io.writeBackStage.inst0.ex),
        0.U,
        io.writeBackStage.inst1.pc
      )
    )
    io.debug.wb_rf_wen := Mux(
      clock.asBool,
      Fill(4, io.regfile(0).wen),
      Fill(4, io.regfile(1).wen)
    )
    io.debug.wb_rf_wnum := Mux(
      clock.asBool,
      io.regfile(0).waddr,
      io.regfile(1).waddr
    )
    io.debug.wb_rf_wdata := Mux(
      clock.asBool,
      io.regfile(0).wdata,
      io.regfile(1).wdata
    )
  }
}
