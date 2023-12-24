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
import cache.mmu._

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
  val itlbL1         = Module(new ITlbL1()).io
  val dtlbL1         = Module(new DTlbL1()).io

  itlbL1.addr := fetchUnit.iCache.pc
  itlbL1.cache <> io.inst.tlb

  dtlbL1.addr := memoryUnit.dataMemory.out.addr
  dtlbL1.cache <> io.data.tlb

  ctrl.decoderUnit <> decoderUnit.ctrl
  ctrl.executeUnit <> executeUnit.ctrl
  ctrl.memoryUnit <> memoryUnit.ctrl
  ctrl.writeBackUnit <> writeBackUnit.ctrl
  ctrl.cacheCtrl.iCache_stall := io.inst.icache_stall

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

  bpu.decoder <> decoderUnit.bpu
  bpu.execute <> executeUnit.bpu

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
  }

  decoderUnit.instFifo.info.empty        := instFifo.empty
  decoderUnit.instFifo.info.almost_empty := instFifo.almost_empty
  decoderUnit.regfile <> regfile.read
  for (i <- 0 until (config.commitNum)) {
    decoderUnit.forward(i).exe      := executeUnit.decoderUnit.forward(i).exe
    decoderUnit.forward(i).mem_wreg := executeUnit.decoderUnit.forward(i).exe_mem_wreg
    decoderUnit.forward(i).mem      := memoryUnit.decoderUnit(i)
  }
  decoderUnit.csr <> csr.decoderUnit
  decoderUnit.executeStage <> executeStage.decoderUnit

  executeStage.ctrl.clear(0) := ctrl.memoryUnit.flush ||
    ctrl.executeUnit.do_flush && ctrl.executeUnit.allow_to_go ||
    !ctrl.decoderUnit.allow_to_go && ctrl.executeUnit.allow_to_go
  executeStage.ctrl.clear(1) := ctrl.memoryUnit.flush ||
    (ctrl.executeUnit.do_flush && decoderUnit.instFifo.allow_to_go(1)) ||
    (ctrl.executeUnit.allow_to_go && !decoderUnit.instFifo.allow_to_go(1))
  executeStage.ctrl.allow_to_go(0) := ctrl.executeUnit.allow_to_go
  executeStage.ctrl.allow_to_go(1) := decoderUnit.instFifo.allow_to_go(1)

  executeUnit.executeStage <> executeStage.executeUnit
  executeUnit.csr <> csr.executeUnit
  executeUnit.memoryStage <> memoryStage.executeUnit

  memoryStage.ctrl.allow_to_go := ctrl.memoryUnit.allow_to_go
  memoryStage.ctrl.clear       := ctrl.memoryUnit.do_flush

  memoryUnit.memoryStage <> memoryStage.memoryUnit
  memoryUnit.csr <> csr.memoryUnit
  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  csr.ext_int := io.ext_int

  memoryUnit.dataMemory.in.rdata   := io.data.rdata
  memoryUnit.dataMemory.in.acc_err := io.data.acc_err
  memoryUnit.dataMemory.in.ready   := io.data.dcache_ready
  io.data.en                       := memoryUnit.dataMemory.out.en
  io.data.rlen                     := memoryUnit.dataMemory.out.rlen
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

  io.inst.fence     := false.B
  io.data.fence     := false.B
  io.inst.req       := !instFifo.full && !reset.asBool
  io.inst.cpu_ready := ctrl.fetchUnit.allow_to_go
  io.data.cpu_ready := ctrl.memoryUnit.allow_to_go
}
