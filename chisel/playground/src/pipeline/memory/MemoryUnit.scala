package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig
import chisel3.util.experimental.BoringUtils

class MemoryUnit(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl        = new MemoryCtrl()
    val memoryStage = Input(new ExecuteUnitMemoryUnit())
    val fetchUnit = Output(new Bundle {
      val flush  = Bool()
      val target = UInt(XLEN.W)
    })
    val decodeUnit     = Output(Vec(cpuConfig.commitNum, new RegWrite()))
    val csr            = Flipped(new CsrMemoryUnit())
    val writeBackStage = Output(new MemoryUnitWriteBackUnit())
    val dataMemory     = new Lsu_DataMemory()
  })

  val lsu = Module(new Lsu()).io
  val mou = Module(new Mou()).io

  mou.in.info := io.memoryStage.inst(0).info
  mou.in.pc   := io.memoryStage.inst(0).pc

  def selectInstField[T <: Data](select: Vec[Bool], fields: Seq[T]): T = {
    require(select.length == fields.length)
    Mux1H(select.zip(fields))
  }

  val lsu_sel = VecInit(
    io.memoryStage.inst(0).info.valid &&
      io.memoryStage.inst(0).info.fusel === FuType.lsu &&
      !HasExcInt(io.memoryStage.inst(0).ex),
    io.memoryStage.inst(1).info.valid &&
      io.memoryStage.inst(1).info.fusel === FuType.lsu &&
      !HasExcInt(io.memoryStage.inst(1).ex) && !HasExcInt(io.memoryStage.inst(0).ex) // 要保证指令0无异常
  )
  lsu.memoryUnit.in.mem_en   := lsu_sel.reduce(_ || _)
  lsu.memoryUnit.in.info     := selectInstField(lsu_sel, io.memoryStage.inst.map(_.info))
  lsu.memoryUnit.in.src_info := selectInstField(lsu_sel, io.memoryStage.inst.map(_.src_info))
  lsu.memoryUnit.in.ex       := selectInstField(lsu_sel, io.memoryStage.inst.map(_.ex))
  lsu.dataMemory <> io.dataMemory
  lsu.memoryUnit.in.allow_to_go := io.ctrl.allow_to_go

  val csr_sel =
    HasExcInt(io.writeBackStage.inst(0).ex) || !HasExcInt(io.writeBackStage.inst(1).ex)

  io.csr.in.pc   := 0.U
  io.csr.in.ex   := 0.U.asTypeOf(new ExceptionInfo())
  io.csr.in.info := 0.U.asTypeOf(new Info())

  def selectInstField[T <: Data](select: Bool, fields: Seq[T]): T = {
    Mux1H(Seq(select -> fields(0), !select -> fields(1)))
  }

  when(io.ctrl.allow_to_go) {
    io.csr.in.pc   := selectInstField(csr_sel, io.memoryStage.inst.map(_.pc))
    io.csr.in.ex   := selectInstField(csr_sel, io.writeBackStage.inst.map(_.ex))
    io.csr.in.info := selectInstField(csr_sel, io.memoryStage.inst.map(_.info))
  }

  for (i <- 0 until cpuConfig.commitNum) {
    io.decodeUnit(i).wen   := io.writeBackStage.inst(i).info.reg_wen
    io.decodeUnit(i).waddr := io.writeBackStage.inst(i).info.reg_waddr
    io.decodeUnit(i).wdata := io.writeBackStage.inst(i).rd_info.wdata(io.writeBackStage.inst(i).info.fusel)

    io.writeBackStage.inst(i).pc                        := io.memoryStage.inst(i).pc
    io.writeBackStage.inst(i).info                      := io.memoryStage.inst(i).info
    io.writeBackStage.inst(i).rd_info.wdata             := io.memoryStage.inst(i).rd_info.wdata
    io.writeBackStage.inst(i).rd_info.wdata(FuType.lsu) := lsu.memoryUnit.out.rdata
    io.writeBackStage.inst(i).ex := Mux(
      lsu_sel(i),
      lsu.memoryUnit.out.ex,
      io.memoryStage.inst(i).ex
    )
  }

  io.writeBackStage.inst(1).info.valid := io.memoryStage.inst(1).info.valid &&
    !(io.fetchUnit.flush && csr_sel) // 指令0导致flush时，不应该提交指令1

  io.ctrl.flush     := io.fetchUnit.flush
  io.ctrl.mem_stall := !lsu.memoryUnit.out.ready && lsu.memoryUnit.in.mem_en

  io.ctrl.fence_i                 := mou.out.fence_i
  io.ctrl.complete_single_request := lsu.memoryUnit.out.complete_single_request

  io.ctrl.sfence_vma.valid    := mou.out.sfence_vma
  io.ctrl.sfence_vma.src_info := io.memoryStage.inst(0).src_info

  val flush = Wire(Bool())
  flush               := io.ctrl.allow_to_go && (io.csr.out.flush || mou.out.flush)
  io.fetchUnit.flush  := flush
  io.fetchUnit.target := Mux(io.csr.out.flush, io.csr.out.target, mou.out.target)

  BoringUtils.addSource(flush, "mem_flush")
}
