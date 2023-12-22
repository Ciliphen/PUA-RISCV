package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class ExceptionInfo extends Bundle {
  val exception = Vec(EXC_WID, Bool())
  val interrupt = Vec(INT_WID, Bool())
  val tval      = UInt(XLEN.W)
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

class InstInfo extends Bundle {
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
  val inst       = UInt(INST_WID.W)
}

class MemRead extends Bundle {
  val mem_wreg  = Bool()
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

class DecoderUnitCtrl extends Bundle {
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
}

class ExecuteCtrl(implicit val config: CpuConfig) extends Bundle {
  val inst     = Output(Vec(config.fuNum, new MemRead()))
  val fu_stall = Output(Bool())
  val flush    = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())

  val fu = new ExecuteFuCtrl()
}

class MemoryCtrl extends Bundle {
  val flush     = Output(Bool())
  val mem_stall = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

class WriteBackCtrl extends Bundle {
  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

// cpu to icache
class Cache_ICache(implicit val config: CpuConfig) extends Bundle {
  // read inst request from cpu
  val req       = Output(Bool())
  val cpu_ready = Output(Bool()) // !cpu_stall
  val addr      = Output(Vec(config.instFetchNum, UInt(INST_ADDR_WID.W))) // virtual address and next virtual address
  val fence     = Output(Bool())

  // read inst result
  val inst         = Input(Vec(config.instFetchNum, UInt(INST_WID.W)))
  val inst_valid   = Input(Vec(config.instFetchNum, Bool()))
  val acc_err      = Input(Bool())
  val icache_stall = Input(Bool()) // icache_stall

  // tlb
  val tlb = new Tlb_ICache()
}

// cpu to dcache
class Cache_DCache extends Bundle {
  val addr      = Output(UInt(DATA_ADDR_WID.W))
  val rlen      = Output(UInt(2.W))
  val en        = Output(Bool())
  val wen       = Output(Bool())
  val wdata     = Output(UInt(XLEN.W))
  val cpu_ready = Output(Bool())
  val fence     = Output(Bool())
  val wstrb     = Output(UInt(AXI_STRB_WID.W))

  val rdata        = Input(UInt(XLEN.W))
  val acc_err      = Input(Bool())
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
  val wb_pc       = Output(UInt(PC_WID.W))
  val wb_rf_wen   = Output(Bool())
  val wb_rf_wnum  = Output(UInt(REG_ADDR_WID.W))
  val wb_rf_wdata = Output(UInt(DATA_WID.W))
}
