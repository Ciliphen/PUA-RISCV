package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.decode.RegWrite
import cpu.pipeline.memory.ExecuteUnitMemoryUnit
import cpu.pipeline.fetch.ExecuteUnitBranchPredictor

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
            val exe          = new RegWrite()
            val exe_mem_wreg = Bool()
          }
        )
      )
    }
    val memoryStage = Output(new ExecuteUnitMemoryUnit())
    val dataMemory = new Bundle {
      val addr = Output(UInt(XLEN.W))
    }
  })

  val fu = Module(new Fu()).io

  val valid = VecInit(
    io.executeStage.inst0.info.valid && io.ctrl.allow_to_go,
    io.executeStage.inst1.info.valid && io.ctrl.allow_to_go
  )

  val fusel = VecInit(
    io.executeStage.inst0.info.fusel,
    io.executeStage.inst1.info.fusel
  )

  val is_csr = VecInit(
    fusel(0) === FuType.csr && valid(0) &&
      !(HasExcInt(io.executeStage.inst0.ex)),
    fusel(1) === FuType.csr && valid(1) &&
      !(HasExcInt(io.executeStage.inst1.ex))
  )

  val mem_wreg = VecInit(
    io.executeStage.inst0.info.fusel === FuType.lsu && io.executeStage.inst0.info.reg_wen,
    io.executeStage.inst1.info.fusel === FuType.lsu && io.executeStage.inst1.info.reg_wen
  )

  io.ctrl.inst(0).mem_wreg  := mem_wreg(0)
  io.ctrl.inst(0).reg_waddr := io.executeStage.inst0.info.reg_waddr
  io.ctrl.inst(1).mem_wreg  := mem_wreg(1)
  io.ctrl.inst(1).reg_waddr := io.executeStage.inst1.info.reg_waddr
  io.ctrl.flush             := io.fetchUnit.flush

  io.csr.in.valid := is_csr.asUInt.orR
  io.csr.in.pc := MuxCase(
    0.U,
    Seq(
      is_csr(0) -> io.executeStage.inst0.pc,
      is_csr(1) -> io.executeStage.inst1.pc
    )
  )
  io.csr.in.info := MuxCase(
    0.U.asTypeOf(new InstInfo()),
    Seq(
      is_csr(0) -> io.executeStage.inst0.info,
      is_csr(1) -> io.executeStage.inst1.info
    )
  )
  io.csr.in.src_info := MuxCase(
    0.U.asTypeOf(new SrcInfo()),
    Seq(
      is_csr(0) -> io.executeStage.inst0.src_info,
      is_csr(1) -> io.executeStage.inst1.src_info
    )
  )
  io.csr.in.ex := MuxCase(
    0.U.asTypeOf(new ExceptionInfo()),
    Seq(
      is_csr(0) -> io.executeStage.inst0.ex,
      is_csr(1) -> io.executeStage.inst1.ex
    )
  )

  val is_lsu = VecInit(
    fusel(0) === FuType.lsu && valid(0) &&
      !(HasExcInt(io.executeStage.inst0.ex)),
    fusel(1) === FuType.lsu && valid(1) &&
      !(HasExcInt(io.executeStage.inst1.ex))
  )

  // input fu
  fu.ctrl <> io.ctrl.fu
  fu.inst(0).pc           := io.executeStage.inst0.pc
  fu.inst(0).info         := io.executeStage.inst0.info
  fu.inst(0).src_info     := io.executeStage.inst0.src_info
  fu.inst(1).pc           := io.executeStage.inst1.pc
  fu.inst(1).info         := io.executeStage.inst1.info
  fu.inst(1).src_info     := io.executeStage.inst1.src_info
  fu.branch.pred_branch   := io.executeStage.inst0.jb_info.pred_branch
  fu.branch.jump_regiser  := io.executeStage.inst0.jb_info.jump_regiser
  fu.branch.branch_target := io.executeStage.inst0.jb_info.branch_target

  io.dataMemory.addr := fu.dataMemory.addr

  io.bpu.pc               := io.executeStage.inst0.pc
  io.bpu.update_pht_index := io.executeStage.inst0.jb_info.update_pht_index
  io.bpu.branch           := fu.branch.branch
  io.bpu.branch_inst      := io.executeStage.inst0.jb_info.branch_inst

  io.fetchUnit.flush := valid(0) && io.ctrl.allow_to_go &&
    (fu.branch.flush || io.csr.out.flush)
  io.fetchUnit.target := Mux(io.csr.out.flush, io.csr.out.target, fu.branch.target)

  io.ctrl.fu_stall := fu.stall_req

  io.memoryStage.inst0.pc                        := io.executeStage.inst0.pc
  io.memoryStage.inst0.info                      := io.executeStage.inst0.info
  io.memoryStage.inst0.src_info                  := io.executeStage.inst0.src_info
  io.memoryStage.inst0.rd_info.wdata             := DontCare
  io.memoryStage.inst0.rd_info.wdata(FuType.alu) := fu.inst(0).result.alu
  io.memoryStage.inst0.rd_info.wdata(FuType.bru) := io.executeStage.inst0.pc + 4.U
  io.memoryStage.inst0.rd_info.wdata(FuType.mdu) := fu.inst(0).result.mdu
  io.memoryStage.inst0.rd_info.wdata(FuType.csr) := io.csr.out.rdata
  val has_ex0 =
    (HasExcInt(io.executeStage.inst0.ex)) && io.executeStage.inst0.info.valid
  io.memoryStage.inst0.ex := Mux(
    has_ex0,
    io.executeStage.inst0.ex,
    MuxLookup(io.executeStage.inst0.info.fusel, io.executeStage.inst0.ex)(
      Seq(
        FuType.csr -> io.csr.out.ex
      )
    )
  )
  io.memoryStage.inst0.ex.exception(instrAddrMisaligned) := io.executeStage.inst0.ex.exception(instrAddrMisaligned) ||
    io.fetchUnit.flush && io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR
  io.memoryStage.inst0.ex.tval(instrAddrMisaligned) := Mux(
    io.executeStage.inst0.ex.exception(instrAddrMisaligned),
    io.executeStage.inst0.ex.tval(instrAddrMisaligned),
    io.fetchUnit.target
  )

  io.memoryStage.inst1.pc                        := io.executeStage.inst1.pc
  io.memoryStage.inst1.info                      := io.executeStage.inst1.info
  io.memoryStage.inst1.src_info                  := io.executeStage.inst1.src_info
  io.memoryStage.inst1.rd_info.wdata             := DontCare
  io.memoryStage.inst1.rd_info.wdata(FuType.alu) := fu.inst(1).result.alu
  io.memoryStage.inst1.rd_info.wdata(FuType.mdu) := fu.inst(1).result.mdu
  io.memoryStage.inst1.rd_info.wdata(FuType.csr) := io.csr.out.rdata
  val has_ex1 =
    (HasExcInt(io.executeStage.inst1.ex)) && io.executeStage.inst1.info.valid
  io.memoryStage.inst1.ex := Mux(
    has_ex1,
    io.executeStage.inst1.ex,
    MuxLookup(io.executeStage.inst1.info.fusel, io.executeStage.inst1.ex)(
      Seq(
        FuType.csr -> io.csr.out.ex
      )
    )
  )
  io.memoryStage.inst1.ex.exception(instrAddrMisaligned) := io.executeStage.inst1.ex.exception(instrAddrMisaligned) ||
    io.fetchUnit.flush && io.fetchUnit.target(log2Ceil(INST_WID / 8) - 1, 0).orR
  io.memoryStage.inst1.ex.tval(instrAddrMisaligned) := Mux(
    io.executeStage.inst1.ex.exception(instrAddrMisaligned),
    io.executeStage.inst1.ex.tval(instrAddrMisaligned),
    io.fetchUnit.target
  )

  io.decodeUnit.forward(0).exe.wen      := io.memoryStage.inst0.info.reg_wen
  io.decodeUnit.forward(0).exe.waddr    := io.memoryStage.inst0.info.reg_waddr
  io.decodeUnit.forward(0).exe.wdata    := io.memoryStage.inst0.rd_info.wdata(io.memoryStage.inst0.info.fusel)
  io.decodeUnit.forward(0).exe_mem_wreg := io.ctrl.inst(0).mem_wreg

  io.decodeUnit.forward(1).exe.wen      := io.memoryStage.inst1.info.reg_wen
  io.decodeUnit.forward(1).exe.waddr    := io.memoryStage.inst1.info.reg_waddr
  io.decodeUnit.forward(1).exe.wdata    := io.memoryStage.inst1.rd_info.wdata(io.memoryStage.inst1.info.fusel)
  io.decodeUnit.forward(1).exe_mem_wreg := io.ctrl.inst(1).mem_wreg
}
