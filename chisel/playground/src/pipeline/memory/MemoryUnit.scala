package cpu.pipeline.memory

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.pipeline.decoder.RegWrite
import cpu.pipeline.execute.CsrMemoryUnit
import cpu.pipeline.writeback.MemoryUnitWriteBackUnit

class MemoryUnit(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl        = new MemoryCtrl()
    val memoryStage = Input(new ExecuteUnitMemoryUnit())
    val fetchUnit = Output(new Bundle {
      val flush  = Bool()
      val target = UInt(PC_WID.W)
    })
    val decoderUnit    = Output(Vec(config.fuNum, new RegWrite()))
    val csr            = Flipped(new CsrMemoryUnit())
    val writeBackStage = Output(new MemoryUnitWriteBackUnit())
    val dataMemory     = new DataMemoryAccess_DataMemory()
  })

  val dataMemoryAccess = Module(new DataMemoryAccess()).io
  val mou              = Module(new Mou()).io

  mou.in.info := io.memoryStage.inst0.info
  mou.in.pc   := io.memoryStage.inst0.pc

  dataMemoryAccess.memoryUnit.in.allow_to_go := io.ctrl.allow_to_go
  val mem_sel = VecInit(
    io.memoryStage.inst0.info.valid &&
      io.memoryStage.inst0.info.fusel === FuType.lsu &&
      !HasExcInt(io.memoryStage.inst0.ex),
    io.memoryStage.inst1.info.valid &&
      io.memoryStage.inst1.info.fusel === FuType.lsu &&
      !HasExcInt(io.memoryStage.inst1.ex) && !HasExcInt(io.memoryStage.inst0.ex)
  )
  dataMemoryAccess.memoryUnit.in.mem_en := mem_sel.reduce(_ || _)
  dataMemoryAccess.memoryUnit.in.info := MuxCase(
    0.U.asTypeOf(new InstInfo()),
    Seq(
      mem_sel(0) -> io.memoryStage.inst0.info,
      mem_sel(1) -> io.memoryStage.inst1.info
    )
  )
  dataMemoryAccess.memoryUnit.in.src_info := MuxCase(
    0.U.asTypeOf(new SrcInfo()),
    Seq(
      mem_sel(0) -> io.memoryStage.inst0.src_info,
      mem_sel(1) -> io.memoryStage.inst1.src_info
    )
  )
  dataMemoryAccess.memoryUnit.in.ex := MuxCase(
    0.U.asTypeOf(new ExceptionInfo()),
    Seq(
      mem_sel(0) -> io.memoryStage.inst0.ex,
      mem_sel(1) -> io.memoryStage.inst1.ex
    )
  )
  dataMemoryAccess.dataMemory <> io.dataMemory

  io.decoderUnit(0).wen   := io.writeBackStage.inst0.info.reg_wen
  io.decoderUnit(0).waddr := io.writeBackStage.inst0.info.reg_waddr
  io.decoderUnit(0).wdata := io.writeBackStage.inst0.rd_info.wdata(io.writeBackStage.inst0.info.fusel)
  io.decoderUnit(1).wen   := io.writeBackStage.inst1.info.reg_wen
  io.decoderUnit(1).waddr := io.writeBackStage.inst1.info.reg_waddr
  io.decoderUnit(1).wdata := io.writeBackStage.inst1.rd_info.wdata(io.writeBackStage.inst1.info.fusel)

  io.writeBackStage.inst0.pc                        := io.memoryStage.inst0.pc
  io.writeBackStage.inst0.info                      := io.memoryStage.inst0.info
  io.writeBackStage.inst0.rd_info.wdata             := io.memoryStage.inst0.rd_info.wdata
  io.writeBackStage.inst0.rd_info.wdata(FuType.lsu) := dataMemoryAccess.memoryUnit.out.rdata
  io.writeBackStage.inst0.ex := Mux(
    mem_sel(0),
    dataMemoryAccess.memoryUnit.out.ex,
    io.memoryStage.inst0.ex
  )
  io.writeBackStage.inst0.commit := io.memoryStage.inst0.info.valid

  io.writeBackStage.inst1.pc                        := io.memoryStage.inst1.pc
  io.writeBackStage.inst1.info                      := io.memoryStage.inst1.info
  io.writeBackStage.inst1.rd_info.wdata             := io.memoryStage.inst1.rd_info.wdata
  io.writeBackStage.inst1.rd_info.wdata(FuType.lsu) := dataMemoryAccess.memoryUnit.out.rdata
  io.writeBackStage.inst1.ex := Mux(
    mem_sel(1),
    dataMemoryAccess.memoryUnit.out.ex,
    io.memoryStage.inst1.ex
  )
  io.writeBackStage.inst1.commit := io.memoryStage.inst1.info.valid &&
    !(HasExcInt(io.writeBackStage.inst0.ex))

  io.csr.in.inst(0).pc := Mux(io.ctrl.allow_to_go, io.writeBackStage.inst0.pc, 0.U)
  io.csr.in.inst(0).ex := Mux(io.ctrl.allow_to_go, io.writeBackStage.inst0.ex, 0.U.asTypeOf(new ExceptionInfo()))
  io.csr.in.inst(0).info := Mux(
    io.ctrl.allow_to_go,
    io.writeBackStage.inst0.info,
    0.U.asTypeOf(new InstInfo())
  )
  io.csr.in.inst(1).pc := Mux(io.ctrl.allow_to_go, io.writeBackStage.inst1.pc, 0.U)
  io.csr.in.inst(1).ex := Mux(io.ctrl.allow_to_go, io.writeBackStage.inst1.ex, 0.U.asTypeOf(new ExceptionInfo()))
  io.csr.in.inst(1).info := Mux(
    io.ctrl.allow_to_go,
    io.writeBackStage.inst1.info,
    0.U.asTypeOf(new InstInfo())
  )

  io.csr.in.set_lr                       := dataMemoryAccess.memoryUnit.out.set_lr && io.ctrl.allow_to_go
  io.csr.in.set_lr_val                   := dataMemoryAccess.memoryUnit.out.set_lr_val
  io.csr.in.set_lr_addr                  := dataMemoryAccess.memoryUnit.out.set_lr_addr
  dataMemoryAccess.memoryUnit.in.lr      := io.csr.out.lr
  dataMemoryAccess.memoryUnit.in.lr_addr := io.csr.out.lr_addr

  io.ctrl.flush     := io.fetchUnit.flush
  io.ctrl.mem_stall := !dataMemoryAccess.memoryUnit.out.ready && dataMemoryAccess.memoryUnit.in.mem_en

  io.fetchUnit.flush  := io.ctrl.allow_to_go && (io.csr.out.flush || mou.out.flush)
  io.fetchUnit.target := Mux(io.csr.out.flush, io.csr.out.flush_pc, mou.out.target)
}
