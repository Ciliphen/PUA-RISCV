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

  instFifo.ctrl <> ctrl.fetchUnit.ctrlSignal
  executeStage.ctrl.zip(decodeUnit.instFifo.allow_to_go).foreach { case (a, b) => a.allow_to_go := b }
  // 执行级缓存清刷逻辑为：
  // 1.译码级需要清刷
  // 2.译码级数据未准备好，此时需要插入气泡
  for (i <- 0 until (cpuConfig.commitNum)) {
    executeStage.ctrl(i).do_flush := ctrl.decodeUnit.ctrlSignal.do_flush ||
    !executeStage.ctrl(i).allow_to_go && ctrl.executeUnit.ctrlSignal.allow_to_go
  }
  memoryStage.ctrl <> ctrl.executeUnit.ctrlSignal
  writeBackStage.ctrl <> ctrl.memoryUnit.ctrlSignal

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

  instFifo.decoderUint <> decodeUnit.instFifo

  for (i <- 0 until cpuConfig.instFetchNum) {
    val pc = io.inst.addr(0) + (i * 4).U
    instFifo.write(i).pht_index := bpu.instBuffer.pht_index(i)
    bpu.instBuffer.pc(i)        := instFifo.write(i).pc
    instFifo.wen(i)             := io.inst.inst_valid(i)
    instFifo.write(i).pc        := pc
    instFifo.write(i).inst      := io.inst.inst(i)
    instFifo.write(i).access_fault := io.inst.access_fault || pc(XLEN - 1, VADDR_WID).asBools
      .map(_ =/= pc(VADDR_WID - 1))
      .reduce(_ || _)
    instFifo.write(i).page_fault      := io.inst.page_fault
    instFifo.write(i).addr_misaligned := pc(log2Ceil(INST_WID / 8) - 1, 0).orR
  }

  decodeUnit.regfile <> regfile.read
  for (i <- 0 until (cpuConfig.commitNum)) {
    decodeUnit.forward(i).exe     := executeUnit.decodeUnit.forward(i).exe
    decodeUnit.forward(i).is_load := executeUnit.decodeUnit.forward(i).is_load // exe级是load指令
    decodeUnit.forward(i).mem     := memoryUnit.decodeUnit(i)
  }
  decodeUnit.executeStage <> executeStage.decodeUnit

  executeUnit.executeStage <> executeStage.executeUnit
  executeUnit.csr <> csr.executeUnit
  executeUnit.memoryStage <> memoryStage.executeUnit

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

  writeBackUnit.writeBackStage <> writeBackStage.writeBackUnit
  regfile.write <> writeBackUnit.regfile

  io.debug <> writeBackUnit.debug

  // 解决fence_i
  io.inst.fence_i      := memoryUnit.ctrl.fence_i
  io.data.fence_i      := memoryUnit.ctrl.fence_i
  io.inst.dcache_stall := !io.data.dcache_ready

  io.inst.req := !instFifo.full && !reset.asBool

  io.inst.complete_single_request := ctrl.fetchUnit.ctrlSignal.allow_to_go
  io.data.complete_single_request := ctrl.memoryUnit.ctrlSignal.allow_to_go || ctrl.memoryUnit.complete_single_request
}
