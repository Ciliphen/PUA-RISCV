package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import icache.mmu.{Tlb_DCache, Tlb_ICache}
import cpu.pipeline.memory.Mou

class ExceptionInfo extends Bundle {
  val exception = Vec(EXC_WID, Bool())
  val interrupt = Vec(INT_WID, Bool())
  val tval      = Vec(EXC_WID, UInt(XLEN.W))
}

class ExtInterrupt extends Bundle {
  val ei = Bool()
  val ti = Bool()
  val si = Bool()
}

class SrcInfo extends Bundle {
  val src1_data = UInt(XLEN.W)
  val src2_data = UInt(XLEN.W)
}

class RdInfo extends Bundle {
  val wdata = Vec(FuType.num, UInt(XLEN.W))
}

class Info extends Bundle {
  val valid      = Bool()
  val inst_legal = Bool()
  val src1_ren   = Bool()
  val src1_raddr = UInt(REG_ADDR_WID.W)
  val src2_ren   = Bool()
  val src2_raddr = UInt(REG_ADDR_WID.W)
  val fusel      = FuType()
  val op         = FuOpType()
  val reg_wen    = Bool()
  val reg_waddr  = UInt(REG_ADDR_WID.W)
  val imm        = UInt(XLEN.W)
  val inst       = UInt(XLEN.W)
  val ret        = Vec(RetType.num, Bool())
}

class MemRead extends Bundle {
  val is_load   = Bool()
  val reg_waddr = UInt(REG_ADDR_WID.W)
}

class SrcReadSignal extends Bundle {
  val ren   = Bool()
  val raddr = UInt(REG_ADDR_WID.W)
}

class CacheCtrl extends Bundle {
  val iCache_stall = Output(Bool())
}

class FetchUnitCtrl extends Bundle {
  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

class DecodeUnitCtrl extends Bundle {
  val inst0 = Output(new Bundle {
    val src1 = new SrcReadSignal()
    val src2 = new SrcReadSignal()
  })
  val branch = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

class ExecuteFuCtrl extends Bundle {
  val allow_to_go = Input(Bool())
  val stall       = Output(Bool())
}

class ExecuteCtrl(implicit val cpuConfig: CpuConfig) extends Bundle {
  val inst  = Output(Vec(cpuConfig.commitNum, new MemRead()))
  val flush = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())

  val fu = new ExecuteFuCtrl()
}

class MouTlb extends Bundle {
  val valid    = Bool()
  val src_info = new SrcInfo()
}

class MemoryCtrl extends Bundle {
  val flush     = Output(Bool())
  val mem_stall = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())

  // to cache
  val fence_i                 = Output(Bool())
  val complete_single_request = Output(Bool()) // to dcache
  // to tlb
  val sfence_vma = Output(new MouTlb())
}

class WriteBackCtrl extends Bundle {
  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

// cpu to icache
class Cache_ICache(implicit val cpuConfig: CpuConfig) extends Bundle {
  // read inst request from cpu
  val req                     = Output(Bool())
  val complete_single_request = Output(Bool())
  val addr                    = Output(Vec(cpuConfig.instFetchNum, UInt(XLEN.W))) // virtual address and next virtual address
  val fence_i                 = Output(Bool())
  val dcache_stall            = Output(Bool())

  // read inst result
  val inst         = Input(Vec(cpuConfig.instFetchNum, UInt(XLEN.W)))
  val inst_valid   = Input(Vec(cpuConfig.instFetchNum, Bool()))
  val access_fault = Input(Bool())
  val page_fault   = Input(Bool())
  val icache_stall = Input(Bool()) // icache_stall

  // tlb
  val tlb = new Tlb_ICache()
}

// cpu to dcache
class Cache_DCache extends Bundle {
  val exe_addr                = Output(UInt(XLEN.W))
  val addr                    = Output(UInt(XLEN.W))
  val rlen                    = Output(UInt(AXI_LEN_WID.W))
  val en                      = Output(Bool())
  val wen                     = Output(Bool())
  val wdata                   = Output(UInt(XLEN.W))
  val complete_single_request = Output(Bool())
  val fence_i                 = Output(Bool())
  val wstrb                   = Output(UInt(AXI_STRB_WID.W))

  val rdata        = Input(UInt(XLEN.W))
  val access_fault = Input(Bool())
  val page_fault   = Input(Bool())
  val dcache_ready = Input(Bool())

  val tlb = new Tlb_DCache()
}

// axi
// master -> slave

class AR extends Bundle {
  val id    = UInt(AXI_ID_WID.W)
  val addr  = UInt(AXI_ADDR_WID.W)
  val len   = UInt(AXI_LEN_WID.W)
  val size  = UInt(AXI_SIZE_WID.W)
  val burst = UInt(AXI_BURST_WID.W)
  val lock  = UInt(AXI_LOCK_WID.W)
  val cache = UInt(AXI_CACHE_WID.W)
  val prot  = UInt(AXI_PROT_WID.W)
}

class R extends Bundle {
  val id   = UInt(AXI_ID_WID.W)
  val data = UInt(AXI_DATA_WID.W)
  val resp = UInt(AXI_RESP_WID.W)
  val last = Bool()
}

class AW extends Bundle {
  val id    = UInt(AXI_ID_WID.W)
  val addr  = UInt(AXI_ADDR_WID.W)
  val len   = UInt(AXI_LEN_WID.W)
  val size  = UInt(AXI_SIZE_WID.W)
  val burst = UInt(AXI_BURST_WID.W)
  val lock  = UInt(AXI_LOCK_WID.W)
  val cache = UInt(AXI_CACHE_WID.W)
  val prot  = UInt(AXI_PROT_WID.W)
}

class W extends Bundle {
  val id   = UInt(AXI_ID_WID.W)
  val data = UInt(AXI_DATA_WID.W)
  val strb = UInt(AXI_STRB_WID.W)
  val last = Bool()
}

class B extends Bundle {
  val id   = UInt(AXI_ID_WID.W)
  val resp = UInt(AXI_RESP_WID.W)
}

class ICache_AXIInterface extends Bundle {
  val ar = Decoupled(new AR())
  val r  = Flipped(Decoupled(new R()))
}

class DCache_AXIInterface extends ICache_AXIInterface {
  val aw = Decoupled(new AW())
  val w  = Decoupled(new W())
  val b  = Flipped(Decoupled(new B()))
}

class Cache_AXIInterface extends Bundle {
  // axi read channel
  val icache = new ICache_AXIInterface()
  val dcache = new DCache_AXIInterface()
}

// AXI interface
class AXI extends Bundle {
  val ar = Decoupled(new AR()) // read address channel
  val r  = Flipped(Decoupled(new R())) // read data channel
  val aw = Decoupled(new AW()) // write address channel
  val w  = Decoupled(new W()) // write data channel
  val b  = Flipped(Decoupled(new B())) // write response channel
}

class DEBUG extends Bundle {
  val wb_pc       = Output(UInt(XLEN.W))
  val wb_rf_wen   = Output(Bool())
  val wb_rf_wnum  = Output(UInt(REG_ADDR_WID.W))
  val wb_rf_wdata = Output(UInt(XLEN.W))
}
