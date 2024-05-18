package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class WriteBackUnit(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl           = new WriteBackCtrl()
    val writeBackStage = Input(new MemoryUnitWriteBackUnit())
    val regfile        = Output(Vec(cpuConfig.commitNum, new RegWrite()))
    val debug          = new DEBUG()
  })

  // 用于csr中minstret寄存器的更新
  val commit_num = Wire(UInt(2.W))
  commit_num := PopCount(io.writeBackStage.inst.map(_.info.valid && io.ctrl.allow_to_go))
  BoringUtils.addSource(commit_num.asUInt, "commit")

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
      buffer.enq(i).pc       := io.writeBackStage.inst(i).pc
      buffer.enq(i).commit   := io.writeBackStage.inst(i).info.valid && io.ctrl.allow_to_go
      buffer.enq(i).rf_wnum  := io.regfile(i).waddr
      buffer.enq(i).rf_wdata := io.regfile(i).wdata
    }
    buffer.flush      := io.ctrl.do_flush
    io.debug.pc       := buffer.deq.pc
    io.debug.commit   := buffer.deq.commit
    io.debug.rf_wnum  := buffer.deq.rf_wnum
    io.debug.rf_wdata := buffer.deq.rf_wdata
  } else {
    io.debug.pc := Mux(
      clock.asBool,
      io.writeBackStage.inst(0).pc,
      Mux(
        !(io.writeBackStage.inst(1).info.valid && io.ctrl.allow_to_go),
        0.U,
        io.writeBackStage.inst(1).pc
      )
    )
    io.debug.commit := Mux(
      clock.asBool,
      io.writeBackStage.inst(0).info.valid && io.ctrl.allow_to_go,
      io.writeBackStage.inst(1).info.valid && io.ctrl.allow_to_go
    )
    io.debug.rf_wnum := Mux(
      clock.asBool,
      io.regfile(0).waddr,
      io.regfile(1).waddr
    )
    io.debug.rf_wdata := Mux(
      clock.asBool,
      io.regfile(0).wdata,
      io.regfile(1).wdata
    )
  }

  io.debug.csr := io.writeBackStage.debug
  io.debug.csr.interrupt := io.writeBackStage.inst(0).ex.interrupt.asUInt.orR
  BoringUtils.addSink(io.debug.perf.icache_req, "icache_req")
  BoringUtils.addSink(io.debug.perf.dcache_req, "dcache_req")
  BoringUtils.addSink(io.debug.perf.icache_hit, "icache_hit")
  BoringUtils.addSink(io.debug.perf.dcache_hit, "dcache_hit")
  BoringUtils.addSink(io.debug.perf.bru_pred_branch, "bru_pred_branch")
  BoringUtils.addSink(io.debug.perf.bru_pred_fail, "bru_pred_fail")
}
