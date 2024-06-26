package cpu.ctrl

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Ctrl(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cacheCtrl     = Flipped(new CacheCtrl())
    val fetchUnit     = Flipped(new FetchUnitCtrl())
    val decodeUnit    = Flipped(new DecodeUnitCtrl())
    val executeUnit   = Flipped(new ExecuteCtrl())
    val memoryUnit    = Flipped(new MemoryCtrl())
    val writeBackUnit = Flipped(new WriteBackCtrl())
  })

  val inst0_lw_stall = (io.executeUnit.inst(0).is_load) && io.executeUnit.inst(0).reg_waddr.orR &&
    (io.decodeUnit.inst0.src1.ren && io.decodeUnit.inst0.src1.raddr === io.executeUnit.inst(0).reg_waddr ||
      io.decodeUnit.inst0.src2.ren && io.decodeUnit.inst0.src2.raddr === io.executeUnit.inst(0).reg_waddr)
  val inst1_lw_stall = (io.executeUnit.inst(1).is_load) && io.executeUnit.inst(1).reg_waddr.orR &&
    (io.decodeUnit.inst0.src1.ren && io.decodeUnit.inst0.src1.raddr === io.executeUnit.inst(1).reg_waddr ||
      io.decodeUnit.inst0.src2.ren && io.decodeUnit.inst0.src2.raddr === io.executeUnit.inst(1).reg_waddr)
  val lw_stall = inst0_lw_stall || inst1_lw_stall
  val longest_stall =
    io.executeUnit.fu.stall || io.cacheCtrl.iCache_stall || io.memoryUnit.mem_stall

  io.fetchUnit.ctrlSignal.allow_to_go     := !io.cacheCtrl.iCache_stall
  io.decodeUnit.ctrlSignal.allow_to_go    := !(lw_stall || longest_stall)
  io.executeUnit.ctrlSignal.allow_to_go   := !longest_stall
  io.memoryUnit.ctrlSignal.allow_to_go    := !longest_stall
  io.writeBackUnit.ctrlSignal.allow_to_go := !longest_stall

  io.fetchUnit.ctrlSignal.do_flush     := io.memoryUnit.flush || io.executeUnit.flush || io.decodeUnit.branch
  io.decodeUnit.ctrlSignal.do_flush    := io.memoryUnit.flush || io.executeUnit.flush
  io.executeUnit.ctrlSignal.do_flush   := io.memoryUnit.flush
  io.memoryUnit.ctrlSignal.do_flush    := false.B
  io.writeBackUnit.ctrlSignal.do_flush := false.B

  io.executeUnit.fu.allow_to_go := io.memoryUnit.ctrlSignal.allow_to_go
}
