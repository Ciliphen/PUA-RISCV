package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.CpuConfig

class ExceptionInfo extends Bundle {
  val flush_req = Bool()
  val eret      = Bool()
  val badvaddr  = UInt(PC_WID.W)
  val bd        = Bool()
  val excode    = UInt(EXCODE_WID.W)
}

class SrcInfo extends Bundle {
  val src1_data = UInt(DATA_WID.W)
  val src2_data = UInt(DATA_WID.W)
}

class RdInfo extends Bundle {
  val wdata = UInt(DATA_WID.W)
}

class InstInfo extends Bundle {
  val inst_valid  = Bool()
  val reg1_ren    = Bool()
  val reg1_raddr  = UInt(REG_ADDR_WID.W)
  val reg2_ren    = Bool()
  val reg2_raddr  = UInt(REG_ADDR_WID.W)
  val fusel       = UInt(FU_SEL_WID.W)
  val op          = UInt(OP_WID.W)
  val reg_wen     = Bool()
  val reg_waddr   = UInt(REG_ADDR_WID.W)
  val imm32       = UInt(DATA_WID.W)
  val cp0_addr    = UInt(CP0_ADDR_WID.W)
  val dual_issue  = Bool()
  val whilo       = Bool()
  val rmem        = Bool()
  val wmem        = Bool()
  val mul         = Bool()
  val div         = Bool()
  val branch_link = Bool()
  val ifence      = Bool()
  val dfence      = Bool()
  val mem_addr    = UInt(DATA_ADDR_WID.W)
  val mem_wreg    = Bool()
  val inst        = UInt(INST_WID.W)
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
  val do_flush    = Input(Bool())
  val eret        = Input(Bool())
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
  val eret      = Output(Bool())

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
  val en    = Output(Bool())
  val ready = Output(Bool())
  val addr  = Output(UInt(INST_ADDR_WID.W)) // virtual address and next virtual address
  val fence = Output(Bool())

  // read inst result
  val rdata   = Input(UInt(INST_WID.W))
  val valid   = Input(Bool())
  val acc_err = Input(Bool())
}

// cpu to dcache
class Cache_DCache extends Bundle {
  val cpu_stall    = Output(Bool())
  val dcache_stall = Input(Bool())

  val execute_addr = Output(UInt(32.W))
  // 连接 mem unit
  val rdata = Input(UInt(32.W))
  val en    = Output(Bool())
  val wen   = Output(UInt(4.W))
  val rlen  = Output(UInt(2.W))
  val wdata = Output(UInt(32.W))
  val addr  = Output(UInt(32.W))

  val fence      = Output(Bool())
  val fence_addr = Output(UInt(32.W))
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
  val data  = Input(UInt(32.W))
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
  val data  = Output(UInt(32.W))
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

class DCache_AXIInterface extends ICache_AXIInterface {
  val aw = Decoupled(new AW())

  val w = Decoupled(new W())

  val b = Flipped(Decoupled())
}

class Cache_AXIInterface extends Bundle {
  // axi read channel
  val icache = new ICache_AXIInterface()
  val dcache = new DCache_AXIInterface()
}

// AXI read address channel
class AXI_AR extends Bundle {
  val id    = UInt(4.W) // transaction ID
  val addr  = UInt(32.W) // address
  val len   = UInt(8.W) // burst length
  val size  = UInt(3.W) // transfer size
  val burst = UInt(2.W) // burst type
  val lock  = UInt(2.W) // lock type
  val cache = UInt(4.W) // cache type
  val prot  = UInt(3.W) // protection type
}

// AXI read data channel
class AXI_R extends Bundle {
  val id   = UInt(4.W) // transaction ID
  val data = UInt(32.W) // read data
  val resp = UInt(2.W) // response type
  val last = Bool() // last beat of burst
}

// AXI write address channel
class AXI_AW extends Bundle {
  val id    = UInt(4.W) // transaction ID
  val addr  = UInt(32.W) // address
  val len   = UInt(8.W) // burst length
  val size  = UInt(3.W) // transfer size
  val burst = UInt(2.W) // burst type
  val lock  = UInt(2.W) // lock type
  val cache = UInt(4.W) // cache type
  val prot  = UInt(3.W) // protection type
}

// AXI write data channel
class AXI_W extends Bundle {
  val id   = UInt(4.W) // transaction ID
  val data = UInt(32.W) // write data
  val strb = UInt(4.W) // byte enable
  val last = Bool() // last beat of burst
}

// AXI write response channel
class AXI_B extends Bundle {
  val id   = UInt(4.W) // transaction ID
  val resp = UInt(2.W) // response type
}

// AXI interface
class AXI extends Bundle {
  val ar = Decoupled(new AXI_AR()) // read address channel
  val r  = Flipped(Decoupled(new AXI_R())) // read data channel
  val aw = Decoupled(new AXI_AW()) // write address channel
  val w  = Decoupled(new AXI_W()) // write data channel
  val b  = Flipped(Decoupled(new AXI_B())) // write response channel
}

class DEBUG(implicit config: CpuConfig) extends Bundle {
  val wb_pc       = Output(UInt(32.W))
  val wb_rf_wen   = Output(UInt(4.W))
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
