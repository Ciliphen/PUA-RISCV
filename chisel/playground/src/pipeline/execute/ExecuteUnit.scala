package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.RegWrite
import cpu.pipeline.ExecuteUnitMemoryUnit
import cpu.pipeline.ExecuteUnitBranchPredictor

class ExecuteUnit(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl         = new ExecuteCtrl()
    val executeStage = Input(new DecodeUnitExecuteUnit())
    val csr          = Flipped(new CsrExecuteUnit())
    val bpu          = new ExecuteUnitBranchPredictor()
    val fetchUnit = Output(new Bundle {
      val flush  = Bool()
      val target = UInt(XLEN.W)
    })
    val decodeUnit = new Bundle {
      val forward = Output(
        Vec(
          cpuConfig.commitNum,
          new Bundle {
            val exe     = new RegWrite()
            val is_load = Bool()
          }
        )
      )
    }
    val memoryStage = Output(new ExecuteUnitMemoryUnit())
    val dataMemory = new Bundle {
      val addr = Output(UInt(XLEN.W))
    }
  })

  val valid = io.executeStage.inst.map(_.info.valid && io.ctrl.allow_to_go)
  val fusel = io.executeStage.inst.map(_.info.fusel)

  io.ctrl.flush := io.fetchUnit.flush
  for (i <- 0 until (cpuConfig.commitNum)) {
    io.ctrl.inst(i).is_load :=
      io.executeStage.inst(i).info.fusel === FuType.lsu && io.executeStage.inst(i).info.reg_wen
    io.ctrl.inst(i).reg_waddr := io.executeStage.inst(i).info.reg_waddr
  }

  val is_csr = VecInit(
    Seq.tabulate(cpuConfig.commitNum)(i =>
      fusel(i) === FuType.csr && valid(i) && !(HasExcInt(io.executeStage.inst(i).ex))
    )
  )

  io.csr.in.valid := is_csr.asUInt.orR

  def selectInstField[T <: Data](select: Vec[Bool], fields: Seq[T]): T = {
    require(select.length == fields.length)
    Mux1H(select.zip(fields))
  }

  io.csr.in.pc       := selectInstField(is_csr, io.executeStage.inst.map(_.pc))
  io.csr.in.info     := selectInstField(is_csr, io.executeStage.inst.map(_.info))
  io.csr.in.src_info := selectInstField(is_csr, io.executeStage.inst.map(_.src_info))
  io.csr.in.ex       := selectInstField(is_csr, io.executeStage.inst.map(_.ex))

  val fu = Module(new Fu()).io
  fu.ctrl <> io.ctrl.fu
  for (i <- 0 until (cpuConfig.commitNum)) {
    fu.inst(i).pc       := io.executeStage.inst(i).pc
    fu.inst(i).info     := io.executeStage.inst(i).info
    fu.inst(i).src_info := io.executeStage.inst(i).src_info
  }
  fu.branch.pred_branch   := io.executeStage.jump_branch_info.pred_branch
  fu.branch.jump_regiser  := io.executeStage.jump_branch_info.jump_regiser
  fu.branch.branch_target := io.executeStage.jump_branch_info.branch_target

  io.dataMemory.addr := fu.dataMemory.addr

  io.bpu.pc               := io.executeStage.inst(0).pc
  io.bpu.update_pht_index := io.executeStage.jump_branch_info.update_pht_index
  io.bpu.branch           := fu.branch.branch
  io.bpu.branch_inst      := io.executeStage.jump_branch_info.branch_inst

  io.fetchUnit.flush  := valid(0) && io.ctrl.allow_to_go && (fu.branch.flush || io.csr.out.flush)
  io.fetchUnit.target := Mux(io.csr.out.flush, io.csr.out.target, fu.branch.target)

  for (i <- 0 until (cpuConfig.commitNum)) {
    io.memoryStage.inst(i).pc                        := io.executeStage.inst(i).pc
    io.memoryStage.inst(i).info                      := io.executeStage.inst(i).info
    io.memoryStage.inst(i).src_info                  := io.executeStage.inst(i).src_info
    io.memoryStage.inst(i).rd_info.wdata             := DontCare
    io.memoryStage.inst(i).rd_info.wdata(FuType.alu) := fu.inst(i).result.alu
    io.memoryStage.inst(i).rd_info.wdata(FuType.bru) := io.executeStage.inst(i).pc + 4.U
    io.memoryStage.inst(i).rd_info.wdata(FuType.mdu) := fu.inst(i).result.mdu
    io.memoryStage.inst(i).rd_info.wdata(FuType.csr) := io.csr.out.rdata
    io.memoryStage.inst(i).ex := Mux(
      (HasExcInt(io.executeStage.inst(i).ex)) && io.executeStage.inst(i).info.valid,
      io.executeStage.inst(i).ex,
      MuxLookup(io.executeStage.inst(i).info.fusel, io.executeStage.inst(i).ex)(
        Seq(
          FuType.csr -> io.csr.out.ex
        )
      )
    )
    io.memoryStage.inst(i).ex.exception(instAddrMisaligned) :=
      io.executeStage.inst(i).ex.exception(instAddrMisaligned) ||
      io.fetchUnit.flush && io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR
    io.memoryStage.inst(i).ex.tval(instAddrMisaligned) := Mux(
      io.executeStage.inst(i).ex.exception(instAddrMisaligned),
      io.executeStage.inst(i).ex.tval(instAddrMisaligned),
      io.fetchUnit.target
    )

    io.decodeUnit.forward(i).exe.wen   := io.memoryStage.inst(i).info.reg_wen
    io.decodeUnit.forward(i).exe.waddr := io.memoryStage.inst(i).info.reg_waddr
    io.decodeUnit.forward(i).exe.wdata := io.memoryStage.inst(i).rd_info.wdata(io.memoryStage.inst(i).info.fusel)
    io.decodeUnit.forward(i).is_load   := io.ctrl.inst(i).is_load
  }
}
