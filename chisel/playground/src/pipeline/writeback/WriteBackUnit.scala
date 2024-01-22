package cpu.pipeline.writeback

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.decode.RegWrite
import cpu.CpuConfig

class WriteBackUnit(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl           = new WriteBackCtrl()
    val writeBackStage = Input(new MemoryUnitWriteBackUnit())
    val regfile        = Output(Vec(cpuConfig.commitNum, new RegWrite()))
    val debug          = new DEBUG()
  })

  io.regfile(0).wen :=
    io.writeBackStage.inst(0).info.valid &&
      io.writeBackStage.inst(0).info.reg_wen &&
      io.ctrl.allow_to_go &&
      !(HasExcInt(io.writeBackStage.inst(0).ex))

  io.regfile(1).wen :=
    io.writeBackStage.inst(1).info.valid &&
      io.writeBackStage.inst(1).info.reg_wen &&
      io.ctrl.allow_to_go &&
      !(HasExcInt(io.writeBackStage.inst(0).ex)) &&
      !(HasExcInt(io.writeBackStage.inst(1).ex))

  for (i <- 0 until (cpuConfig.commitNum)) {
    io.regfile(i).waddr := io.writeBackStage.inst(i).info.reg_waddr
    io.regfile(i).wdata := io.writeBackStage.inst(i).rd_info.wdata(io.writeBackStage.inst(i).info.fusel)
  }

  if (cpuConfig.hasCommitBuffer) {
    val buffer = Module(new CommitBuffer()).io
    for (i <- 0 until (cpuConfig.commitNum)) {
      buffer.enq(i).wb_pc       := io.writeBackStage.inst(i).pc
      buffer.enq(i).wb_rf_wen   := io.writeBackStage.inst(i).info.valid && io.ctrl.allow_to_go
      buffer.enq(i).wb_rf_wnum  := io.regfile(i).waddr
      buffer.enq(i).wb_rf_wdata := io.regfile(i).wdata
    }
    buffer.flush         := io.ctrl.do_flush
    io.debug.wb_pc       := buffer.deq.wb_pc
    io.debug.wb_rf_wen   := buffer.deq.wb_rf_wen
    io.debug.wb_rf_wnum  := buffer.deq.wb_rf_wnum
    io.debug.wb_rf_wdata := buffer.deq.wb_rf_wdata
  } else {
    io.debug.wb_pc := Mux(
      clock.asBool,
      io.writeBackStage.inst(0).pc,
      Mux(
        !(io.writeBackStage.inst(1).info.valid && io.ctrl.allow_to_go),
        0.U,
        io.writeBackStage.inst(1).pc
      )
    )
    io.debug.wb_rf_wen := Mux(
      clock.asBool,
      io.writeBackStage.inst(0).info.valid && io.ctrl.allow_to_go,
      io.writeBackStage.inst(1).info.valid && io.ctrl.allow_to_go
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
