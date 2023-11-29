package cpu

import chisel3._
import chisel3.util._
import chisel3.internal.DontCareBinding

import defines._
import defines.Const._
import pipeline.fetch._
import pipeline.decoder._
import pipeline.execute._
import pipeline.memory._
import pipeline.writeback._
import ctrl._
import mmu._
import chisel3.util.experimental.decode.decoder
import cpu.pipeline.fetch.InstFifo

class Core(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ext_int = Input(new ExtInterrupt())
    val inst    = new Cache_ICache()
    val data    = new Cache_DCache()
    val debug   = new DEBUG()
  })

  val ctrl           = Module(new Ctrl()).io
  val fetchUnit      = Module(new FetchUnit()).io
  val bpu            = Module(new BranchPredictorUnit()).io
  val instFifo       = Module(new InstFifo()).io
  val decoderUnit    = Module(new DecoderUnit()).io
  val regfile        = Module(new ARegFile()).io
  val executeStage   = Module(new ExecuteStage()).io
  val executeUnit    = Module(new ExecuteUnit()).io
  val csr            = Module(new Csr()).io
  val memoryStage    = Module(new MemoryStage()).io
  val memoryUnit     = Module(new MemoryUnit()).io
  val writeBackStage = Module(new WriteBackStage()).io
  val writeBackUnit  = Module(new WriteBackUnit()).io

  ctrl.instFifo.has2insts := !(instFifo.empty || instFifo.almost_empty)
  ctrl.decoderUnit <> decoderUnit.ctrl
  ctrl.executeUnit <> executeUnit.ctrl
  ctrl.memoryUnit <> memoryUnit.ctrl
  ctrl.writeBackUnit <> writeBackUnit.ctrl
  ctrl.cacheCtrl.iCache_stall := io.inst.icache_stall
  ctrl.cacheCtrl.dCache_stall := io.data.dcache_stall

  fetchUnit.memory <> memoryUnit.fetchUnit
  fetchUnit.execute <> executeUnit.fetchUnit
  fetchUnit.decoder <> decoderUnit.fetchUnit
  fetchUnit.instFifo.full     := instFifo.full
  fetchUnit.iCache.inst_valid := io.inst.inst_valid
  io.inst.addr(0)             := fetchUnit.iCache.pc
  io.inst.addr(1)             := fetchUnit.iCache.pc_next
  for (i <- 2 until config.instFetchNum) {
    io.inst.addr(i) := fetchUnit.iCache.pc_next + ((i - 1) * 4).U
  }

  bpu.decoder.ena                  := ctrl.decoderUnit.allow_to_go
  bpu.decoder.op                   := decoderUnit.bpu.decoded_inst0.op
  bpu.decoder.fusel                := decoderUnit.bpu.decoded_inst0.fusel
  bpu.decoder.inst                 := decoderUnit.bpu.decoded_inst0.inst
  bpu.decoder.rs1                  := decoderUnit.bpu.decoded_inst0.reg1_raddr
  bpu.decoder.rs2                  := decoderUnit.bpu.decoded_inst0.reg2_raddr
  bpu.decoder.pc                   := decoderUnit.bpu.pc
  bpu.decoder.pht_index            := decoderUnit.bpu.pht_index
  decoderUnit.bpu.update_pht_index := bpu.decoder.update_pht_index
  bpu.execute <> executeUnit.bpu
  decoderUnit.bpu.branch_inst   := bpu.decoder.branch_inst
  decoderUnit.bpu.pred_branch   := bpu.decoder.pred_branch
  decoderUnit.bpu.branch_target := bpu.decoder.branch_target

  instFifo.do_flush := ctrl.decoderUnit.do_flush
  instFifo.ren <> decoderUnit.instFifo.allow_to_go
  decoderUnit.instFifo.inst <> instFifo.read

  for (i <- 0 until config.instFetchNum) {
    instFifo.write(i).pht_index := bpu.instBuffer.pht_index(i)
    bpu.instBuffer.pc(i)        := instFifo.write(i).pc
    instFifo.wen(i)             := io.inst.inst_valid(i)
    instFifo.write(i).pc        := io.inst.addr(0) + (i * 4).U
    instFifo.write(i).inst      := io.inst.inst(i)
    instFifo.write(i).acc_err   := io.inst.acc_err
    instFifo.write(i).addr_err  := io.inst.addr_err
  }

  decoderUnit.instFifo.info.empty        := instFifo.empty
  decoderUnit.instFifo.info.almost_empty := instFifo.almost_empty
  decoderUnit.regfile <> regfile.read
  for (i <- 0 until (config.fuNum)) {
    decoderUnit.forward(i).exe      := executeUnit.decoderUnit.forward(i).exe
    decoderUnit.forward(i).mem_wreg := executeUnit.decoderUnit.forward(i).exe_mem_wreg
    decoderUnit.forward(i).mem      := memoryUnit.decoderUnit(i)
  }
  decoderUnit.csr <> csr.decoderUnit
  decoderUnit.executeStage <> executeStage.decoderUnit

  executeStage.ctrl.clear(0) := ctrl.memoryUnit.flush_req ||
    ctrl.executeUnit.do_flush && ctrl.executeUnit.allow_to_go ||
    !ctrl.decoderUnit.allow_to_go && ctrl.executeUnit.allow_to_go
  executeStage.ctrl.clear(1) := ctrl.memoryUnit.flush_req ||
    (ctrl.executeUnit.do_flush && decoderUnit.executeStage.inst1.allow_to_go) ||
    (ctrl.executeUnit.allow_to_go && !decoderUnit.executeStage.inst1.allow_to_go)
  executeStage.ctrl.inst0_allow_to_go := ctrl.executeUnit.allow_to_go

  executeUnit.executeStage <> executeStage.executeUnit
  executeUnit.csr <> csr.executeUnit
  executeUnit.memoryStage <> memoryStage.executeUnit

  memoryStage.ctrl.allow_to_go := ctrl.memoryUnit.allow_to_go
  memoryStage.ctrl.clear       := ctrl.memoryUnit.do_flush

  memoryUnit.memoryStage <> memoryStage.memoryUnit
  memoryUnit.csr <> csr.memoryUnit
  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  csr.ctrl.exe_stall := !ctrl.executeUnit.allow_to_go
  csr.ctrl.mem_stall := !ctrl.memoryUnit.allow_to_go
  csr.ext_int        := io.ext_int

  memoryUnit.dataMemory.in.rdata   := io.data.rdata
  memoryUnit.dataMemory.in.acc_err := io.data.acc_err
  io.data.en                       := memoryUnit.dataMemory.out.en
  io.data.size                     := memoryUnit.dataMemory.out.rlen
  io.data.wen                      := memoryUnit.dataMemory.out.wen
  io.data.wdata                    := memoryUnit.dataMemory.out.wdata
  io.data.addr                     := memoryUnit.dataMemory.out.addr
  io.data.wstrb                    := memoryUnit.dataMemory.out.wstrb

  writeBackStage.memoryUnit <> memoryUnit.writeBackStage
  writeBackStage.ctrl.allow_to_go := ctrl.writeBackUnit.allow_to_go
  writeBackStage.ctrl.clear       := ctrl.writeBackUnit.do_flush

  writeBackUnit.writeBackStage <> writeBackStage.writeBackUnit
  writeBackUnit.ctrl <> ctrl.writeBackUnit
  regfile.write <> writeBackUnit.regfile

  io.debug <> writeBackUnit.debug

  io.inst.fence_i := executeUnit.executeStage.inst0.inst_info.fusel === FuType.mou &&
    executeUnit.executeStage.inst0.inst_info.op === MOUOpType.fencei
  io.data.fence_i := memoryUnit.memoryStage.inst0.inst_info.fusel === FuType.mou &&
    memoryUnit.memoryStage.inst0.inst_info.op === MOUOpType.fencei
  io.inst.req       := !instFifo.full && !reset.asBool
  io.inst.cpu_ready := ctrl.fetchUnit.allow_to_go
  io.data.cpu_ready := ctrl.memoryUnit.allow_to_go
}
