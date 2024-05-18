package cpu

import chisel3._
import chisel3.util._
import chisel3.internal.DontCareBinding

import defines._
import defines.Const._
import pipeline._
import ctrl._
import icache.mmu.Tlb
import chisel3.util.experimental.BoringUtils

class Core(implicit val cpuConfig: CpuConfig) extends Module {
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
  val decodeUnit     = Module(new DecodeUnit()).io
  val regfile        = Module(new ARegFile()).io
  val executeStage   = Module(new ExecuteStage()).io
  val executeUnit    = Module(new ExecuteUnit()).io
  val csr            = Module(new Csr()).io
  val memoryStage    = Module(new MemoryStage()).io
  val memoryUnit     = Module(new MemoryUnit()).io
  val writeBackStage = Module(new WriteBackStage()).io
  val writeBackUnit  = Module(new WriteBackUnit()).io
  val tlb            = Module(new Tlb()).io

  tlb.icache <> io.inst.tlb
  tlb.dcache <> io.data.tlb
  tlb.csr <> csr.tlb
  tlb.sfence_vma <> memoryUnit.ctrl.sfence_vma

  ctrl.decodeUnit <> decodeUnit.ctrl
  ctrl.executeUnit <> executeUnit.ctrl
  ctrl.memoryUnit <> memoryUnit.ctrl
  ctrl.writeBackUnit <> writeBackUnit.ctrl
  ctrl.cacheCtrl.iCache_stall := io.inst.icache_stall

  fetchUnit.memory <> memoryUnit.fetchUnit
  fetchUnit.execute <> executeUnit.fetchUnit
  fetchUnit.decode <> decodeUnit.fetchUnit
  fetchUnit.instFifo.full     := instFifo.full
  fetchUnit.iCache.inst_valid := io.inst.inst_valid
  io.inst.addr(0)             := fetchUnit.iCache.pc
  io.inst.addr(1)             := fetchUnit.iCache.pc_next
  for (i <- 2 until cpuConfig.instFetchNum) {
    io.inst.addr(i) := fetchUnit.iCache.pc_next + ((i - 1) * 4).U
  }

  bpu.decode <> decodeUnit.bpu
  bpu.execute <> executeUnit.bpu

  instFifo.do_flush := ctrl.decodeUnit.do_flush
  instFifo.decoderUint <> decodeUnit.instFifo

  for (i <- 0 until cpuConfig.instFetchNum) {
    instFifo.write(i).pht_index       := bpu.instBuffer.pht_index(i)
    bpu.instBuffer.pc(i)              := instFifo.write(i).pc
    instFifo.wen(i)                   := io.inst.inst_valid(i)
    instFifo.write(i).pc              := io.inst.addr(0) + (i * 4).U
    instFifo.write(i).inst            := io.inst.inst(i)
    instFifo.write(i).access_fault    := io.inst.access_fault
    instFifo.write(i).page_fault      := io.inst.page_fault
    instFifo.write(i).addr_misaligned := io.inst.addr_misaligned
  }

  decodeUnit.regfile <> regfile.read
  for (i <- 0 until (cpuConfig.commitNum)) {
    decodeUnit.forward(i).exe     := executeUnit.decodeUnit.forward(i).exe
    decodeUnit.forward(i).is_load := executeUnit.decodeUnit.forward(i).is_load // exe级是load指令
    decodeUnit.forward(i).mem     := memoryUnit.decodeUnit(i)
  }
  decodeUnit.executeStage <> executeStage.decodeUnit

  for (i <- 0 until (cpuConfig.commitNum)) {
    // 流水线清空或者暂停时，需要清空缓存级的数据，也就是插入气泡
    executeStage.ctrl.clear(i) :=
      ctrl.memoryUnit.do_flush || ctrl.executeUnit.do_flush ||
      !decodeUnit.instFifo.allow_to_go(i) && ctrl.executeUnit.allow_to_go

    executeStage.ctrl.allow_to_go(i) := decodeUnit.instFifo.allow_to_go(i)
  }
  executeUnit.executeStage <> executeStage.executeUnit
  executeUnit.csr <> csr.executeUnit
  executeUnit.memoryStage <> memoryStage.executeUnit

  memoryStage.ctrl.allow_to_go := ctrl.memoryUnit.allow_to_go
  memoryStage.ctrl.clear       := ctrl.memoryUnit.do_flush

  memoryUnit.memoryStage <> memoryStage.memoryUnit
  memoryUnit.csr <> csr.memoryUnit
  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  csr.ext_int := io.ext_int

  memoryUnit.dataMemory.in.rdata        := io.data.rdata
  memoryUnit.dataMemory.in.access_fault := io.data.access_fault
  memoryUnit.dataMemory.in.page_fault   := io.data.page_fault
  memoryUnit.dataMemory.in.ready        := io.data.dcache_ready
  io.data.en                            := memoryUnit.dataMemory.out.en
  io.data.rlen                          := memoryUnit.dataMemory.out.rlen
  io.data.wen                           := memoryUnit.dataMemory.out.wen
  io.data.wdata                         := memoryUnit.dataMemory.out.wdata
  io.data.addr                          := memoryUnit.dataMemory.out.addr
  io.data.wstrb                         := memoryUnit.dataMemory.out.wstrb
  io.data.exe_addr                      := executeUnit.dataMemory.addr

  writeBackStage.memoryUnit <> memoryUnit.writeBackStage
  writeBackStage.ctrl.allow_to_go := ctrl.writeBackUnit.allow_to_go
  writeBackStage.ctrl.clear       := ctrl.writeBackUnit.do_flush

  writeBackUnit.writeBackStage <> writeBackStage.writeBackUnit
  writeBackUnit.ctrl <> ctrl.writeBackUnit
  regfile.write <> writeBackUnit.regfile

  io.debug <> writeBackUnit.debug

  // 解决fence_i
  io.inst.fence_i      := memoryUnit.ctrl.fence_i
  io.data.fence_i      := memoryUnit.ctrl.fence_i
  io.inst.dcache_stall := !io.data.dcache_ready

  io.inst.req := !instFifo.full && !reset.asBool

  io.inst.complete_single_request := ctrl.fetchUnit.allow_to_go
  io.data.complete_single_request := ctrl.memoryUnit.allow_to_go || ctrl.memoryUnit.complete_single_request
}
