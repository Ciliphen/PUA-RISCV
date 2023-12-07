package cpu.ctrl

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Ctrl(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cacheCtrl     = Flipped(new CacheCtrl())
    val fetchUnit     = Flipped(new FetchUnitCtrl())
    val instFifo      = Flipped(new InstFifoCtrl())
    val decoderUnit   = Flipped(new DecoderUnitCtrl())
    val executeUnit   = Flipped(new ExecuteCtrl())
    val memoryUnit    = Flipped(new MemoryCtrl())
    val writeBackUnit = Flipped(new WriteBackCtrl())
  })

  val inst0_lw_stall = (io.executeUnit.inst(0).mem_wreg) && io.executeUnit.inst(0).reg_waddr.orR &&
    (io.decoderUnit.inst0.src1.ren && io.decoderUnit.inst0.src1.raddr === io.executeUnit.inst(0).reg_waddr ||
      io.decoderUnit.inst0.src2.ren && io.decoderUnit.inst0.src2.raddr === io.executeUnit.inst(0).reg_waddr)
  val inst1_lw_stall = (io.executeUnit.inst(1).mem_wreg) && io.executeUnit.inst(1).reg_waddr.orR &&
    (io.decoderUnit.inst0.src1.ren && io.decoderUnit.inst0.src1.raddr === io.executeUnit.inst(1).reg_waddr ||
      io.decoderUnit.inst0.src2.ren && io.decoderUnit.inst0.src2.raddr === io.executeUnit.inst(1).reg_waddr)
  val lw_stall = inst0_lw_stall || inst1_lw_stall
  // TODO: 这里的stall信号可能不对
  val longest_stall =
    io.executeUnit.fu_stall || io.cacheCtrl.iCache_stall || io.memoryUnit.mem_stall

  io.fetchUnit.allow_to_go     := !io.cacheCtrl.iCache_stall
  io.decoderUnit.allow_to_go   := !(lw_stall || longest_stall)
  io.executeUnit.allow_to_go   := !longest_stall
  io.memoryUnit.allow_to_go    := !longest_stall
  io.writeBackUnit.allow_to_go := !longest_stall || io.memoryUnit.flush

  io.fetchUnit.do_flush     := false.B
  io.decoderUnit.do_flush   := io.memoryUnit.flush || io.executeUnit.flush || io.decoderUnit.branch
  io.executeUnit.do_flush   := io.memoryUnit.flush || io.executeUnit.flush
  io.memoryUnit.do_flush    := io.memoryUnit.flush
  io.writeBackUnit.do_flush := false.B

  io.executeUnit.fu.allow_to_go := io.memoryUnit.allow_to_go
}
