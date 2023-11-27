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
  val reg1_ren   = Bool()
  val reg1_raddr = UInt(REG_ADDR_WID.W)
  val reg2_ren   = Bool()
  val reg2_raddr = UInt(REG_ADDR_WID.W)
  val fusel      = FuType()
  val op         = FuOpType()
  val reg_wen    = Bool()
  val reg_waddr  = UInt(REG_ADDR_WID.W)
  val imm        = UInt(XLEN.W)
  val inst       = UInt(INST_WID.W)
  val mem_wreg   = Bool()
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
  val dCache_stall = Output(Bool())
}

class FetchUnitCtrl extends Bundle {
  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())
}

class InstFifoCtrl extends Bundle {
  val has2insts = Output(Bool())
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
  val branch   = Output(Bool())

  val allow_to_go = Input(Bool())
  val do_flush    = Input(Bool())

  val fu = new ExecuteFuCtrl()
}

class MemoryCtrl extends Bundle {
  val flush_req = Output(Bool())

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
  val fence_i   = Output(Bool())

  // read inst result
  val inst         = Input(Vec(config.instFetchNum, UInt(INST_WID.W)))
  val inst_valid   = Input(Vec(config.instFetchNum, Bool()))
  val acc_err      = Input(Bool())
  val addr_err     = Input(Bool())
  val icache_stall = Input(Bool()) // icache_stall
}

// cpu to dcache
class Cache_DCache extends Bundle {
  val addr      = Output(UInt(DATA_ADDR_WID.W))
  val size      = Output(UInt(2.W))
  val en        = Output(Bool())
  val wen       = Output(Bool())
  val wdata     = Output(UInt(XLEN.W))
  val cpu_ready = Output(Bool())
  val fence_i   = Output(Bool())

  val rdata        = Input(UInt(XLEN.W))
  val valid        = Input(Bool())
  val acc_err      = Input(Bool())
  val dcache_stall = Input(Bool())
}

// axi
// master -> slave

class AR extends Bundle {
  val id    = Output(UInt(4.W))
  val addr  = Output(UInt(32.W))
  val len   = Output(UInt(8.W))
  val size  = Output(UInt(3.W))
  val burst = Output(UInt(2.W))
  val lock  = Output(UInt(2.W))
  val cache = Output(UInt(4.W))
  val prot  = Output(UInt(3.W))
  val valid = Output(Bool())

  val ready = Input(Bool())
}

class R extends Bundle {
  val ready = Output(Bool())

  val id    = Input(UInt(4.W))
  val data  = Input(UInt(XLEN.W))
  val resp  = Input(UInt(2.W))
  val last  = Input(Bool())
  val valid = Input(Bool())
}

class AW extends Bundle {
  val id    = Output(UInt(4.W))
  val addr  = Output(UInt(32.W))
  val len   = Output(UInt(8.W))
  val size  = Output(UInt(3.W))
  val burst = Output(UInt(2.W))
  val lock  = Output(UInt(2.W))
  val cache = Output(UInt(4.W))
  val prot  = Output(UInt(3.W))
  val valid = Output(Bool())

  val ready = Input(Bool())
}

class W extends Bundle {
  val id    = Output(UInt(4.W))
  val data  = Output(UInt(XLEN.W))
  val strb  = Output(UInt(4.W))
  val last  = Output(Bool())
  val valid = Output(Bool())

  val ready = Input(Bool())
}

class B extends Bundle {
  val ready = Output(Bool())

  val id    = Input(UInt(4.W))
  val resp  = Input(UInt(2.W))
  val valid = Input(Bool())
}

class ICache_AXIInterface extends Bundle {
  val ar = new AR()
  val r  = new R()
}

class DCache_AXIInterface extends Bundle {
  val aw = new AW()
  val w  = new W()
  val b  = new B()
  val ar = new AR()
  val r  = new R()
}

class Cache_AXIInterface extends Bundle {
  // axi read channel
  val icache = new ICache_AXIInterface()
  val dcache = new DCache_AXIInterface()
}

// AXI interface
class AXI extends Bundle {
  val ar = new AR() // read address channel
  val r  = new R() // read data channel
  val aw = new AW() // write address channel
  val w  = new W() // write data channel
  val b  = new B() // write response channel
}

class DEBUG extends Bundle {
  val wb_pc       = Output(UInt(32.W))
  val wb_rf_wen   = Output(Bool())
  val wb_rf_wnum  = Output(UInt(5.W))
  val wb_rf_wdata = Output(UInt(32.W))
}

class Ctrl_Sram extends Bundle {
  val do_flush = Output(Bool())
}

class Ctrl_Stage extends Bundle {
  val do_flush = Output(Bool())
  val after_ex = Output(Bool())
}

class Sram_Ctrl extends Bundle {
  val sram_discard = Output(UInt(2.W))
}

class Pipeline_Ctrl extends Bundle {
  val ex = Output(Bool())
}
