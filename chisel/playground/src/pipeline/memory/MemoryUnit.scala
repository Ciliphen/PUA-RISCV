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
  dataMemoryAccess.memoryUnit.in.mem_en    := io.memoryStage.inst0.mem.en
  dataMemoryAccess.memoryUnit.in.info      := io.memoryStage.inst0.mem.info
  dataMemoryAccess.memoryUnit.in.mem_wdata := io.memoryStage.inst0.mem.wdata
  dataMemoryAccess.memoryUnit.in.mem_addr  := io.memoryStage.inst0.mem.addr
  dataMemoryAccess.memoryUnit.in.mem_sel   := io.memoryStage.inst0.mem.sel
  dataMemoryAccess.memoryUnit.in.ex(0)     := io.memoryStage.inst0.ex
  dataMemoryAccess.memoryUnit.in.ex(1)     := io.memoryStage.inst1.ex
  dataMemoryAccess.dataMemory.in.acc_err   := io.dataMemory.in.acc_err
  dataMemoryAccess.dataMemory.in.rdata     := io.dataMemory.in.rdata
  io.dataMemory.out                        := dataMemoryAccess.dataMemory.out

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
  io.writeBackStage.inst0.ex                        := io.memoryStage.inst0.ex
  io.writeBackStage.inst0.ex.exception(loadAccessFault) := io.memoryStage.inst0.mem.sel(0) &&
    LSUOpType.isLoad(io.memoryStage.inst0.info.op) && dataMemoryAccess.memoryUnit.out.acc_err
  io.writeBackStage.inst0.ex.exception(storeAccessFault) := io.memoryStage.inst0.mem.sel(0) &&
    LSUOpType.isStore(io.memoryStage.inst0.info.op) && dataMemoryAccess.memoryUnit.out.acc_err
  io.writeBackStage.inst0.commit := io.memoryStage.inst0.info.valid

  io.writeBackStage.inst1.pc                        := io.memoryStage.inst1.pc
  io.writeBackStage.inst1.info                      := io.memoryStage.inst1.info
  io.writeBackStage.inst1.rd_info.wdata             := io.memoryStage.inst1.rd_info.wdata
  io.writeBackStage.inst1.rd_info.wdata(FuType.lsu) := dataMemoryAccess.memoryUnit.out.rdata
  io.writeBackStage.inst1.ex                        := io.memoryStage.inst1.ex
  io.writeBackStage.inst1.ex.exception(loadAccessFault) := io.memoryStage.inst0.mem.sel(1) &&
    LSUOpType.isLoad(io.memoryStage.inst1.info.op) && dataMemoryAccess.memoryUnit.out.acc_err
  io.writeBackStage.inst1.ex.exception(storeAccessFault) := io.memoryStage.inst0.mem.sel(1) &&
    LSUOpType.isStore(io.memoryStage.inst1.info.op) && dataMemoryAccess.memoryUnit.out.acc_err
  io.writeBackStage.inst1.commit := io.memoryStage.inst1.info.valid &&
    !(io.writeBackStage.inst0.ex.exception.asUInt.orR || io.writeBackStage.inst0.ex.interrupt.asUInt.orR)

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

  io.fetchUnit.flush  := io.csr.out.flush && io.ctrl.allow_to_go
  io.fetchUnit.target := Mux(io.csr.out.flush, io.csr.out.flush_pc, io.writeBackStage.inst0.pc + 4.U)

  io.ctrl.flush := io.fetchUnit.flush
}
