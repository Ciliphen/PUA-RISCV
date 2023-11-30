package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.decoder.RegWrite
import cpu.pipeline.memory.ExecuteUnitMemoryUnit
import cpu.pipeline.fetch.ExecuteUnitBranchPredictor

class ExecuteUnit(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl         = new ExecuteCtrl()
    val executeStage = Input(new DecoderUnitExecuteUnit())
    val csr          = Flipped(new CsrExecuteUnit())
    val bpu          = new ExecuteUnitBranchPredictor()
    val fetchUnit = Output(new Bundle {
      val branch = Bool()
      val target = UInt(PC_WID.W)
    })
    val decoderUnit = new Bundle {
      val forward = Output(
        Vec(
          config.fuNum,
          new Bundle {
            val exe          = new RegWrite()
            val exe_mem_wreg = Bool()
          }
        )
      )
    }
    val memoryStage = Output(new ExecuteUnitMemoryUnit())
  })

  val fu            = Module(new Fu()).io
  val accessMemCtrl = Module(new ExeAccessMemCtrl()).io

  val valid = VecInit(
    io.executeStage.inst0.info.valid && io.ctrl.allow_to_go,
    io.executeStage.inst1.info.valid && io.ctrl.allow_to_go
  )

  io.ctrl.inst(0).mem_wreg  := io.executeStage.inst0.info.mem_wreg
  io.ctrl.inst(0).reg_waddr := io.executeStage.inst0.info.reg_waddr
  io.ctrl.inst(1).mem_wreg  := io.executeStage.inst1.info.mem_wreg
  io.ctrl.inst(1).reg_waddr := io.executeStage.inst1.info.reg_waddr
  io.ctrl.branch := valid(0) && io.ctrl.allow_to_go &&
    (io.executeStage.inst0.jb_info.jump_regiser || fu.branch.pred_fail)

  val csr_sel0 = valid(0) && io.executeStage.inst0.info.fusel === FuType.csr &&
    !(io.executeStage.inst0.ex.exception.asUInt.orR || io.executeStage.inst0.ex.interrupt.asUInt.orR)
  val csr_sel1 = valid(1) && io.executeStage.inst1.info.fusel === FuType.csr &&
    !(io.executeStage.inst1.ex.exception.asUInt.orR || io.executeStage.inst1.ex.interrupt.asUInt.orR)
  io.csr.in.valid := (csr_sel0 || csr_sel1)
  io.csr.in.info := MuxCase(
    0.U.asTypeOf(new InstInfo()),
    Seq(
      csr_sel0 -> io.executeStage.inst0.info,
      csr_sel1 -> io.executeStage.inst1.info
    )
  )
  io.csr.in.src_info := MuxCase(
    0.U.asTypeOf(new SrcInfo()),
    Seq(
      csr_sel0 -> io.executeStage.inst0.src_info,
      csr_sel1 -> io.executeStage.inst1.src_info
    )
  )
  io.csr.in.ex := MuxCase(
    0.U.asTypeOf(new ExceptionInfo()),
    Seq(
      csr_sel0 -> io.executeStage.inst0.ex,
      csr_sel1 -> io.executeStage.inst1.ex
    )
  )

  // input accessMemCtrl
  accessMemCtrl.inst(0).info := io.executeStage.inst0.info
  accessMemCtrl.inst(0).src_info  := io.executeStage.inst0.src_info
  accessMemCtrl.inst(0).ex.in     := io.executeStage.inst0.ex
  accessMemCtrl.inst(1).info := io.executeStage.inst1.info
  accessMemCtrl.inst(1).src_info  := io.executeStage.inst1.src_info
  accessMemCtrl.inst(1).ex.in     := io.executeStage.inst1.ex

  // input fu
  fu.ctrl <> io.ctrl.fu
  fu.inst(0).pc := io.executeStage.inst0.pc
  fu.inst(0).mul_en := io.executeStage.inst0.info.fusel === FuType.mdu &&
    !MDUOpType.isDiv(io.executeStage.inst0.info.op)
  fu.inst(0).div_en := io.executeStage.inst0.info.fusel === FuType.mdu &&
    MDUOpType.isDiv(io.executeStage.inst0.info.op)
  fu.inst(0).info := io.executeStage.inst0.info
  fu.inst(0).src_info  := io.executeStage.inst0.src_info
  fu.inst(0).ex.in :=
    Mux(io.executeStage.inst0.info.fusel === FuType.lsu, accessMemCtrl.inst(0).ex.out, io.executeStage.inst0.ex)
  fu.inst(1).pc := io.executeStage.inst1.pc
  fu.inst(1).mul_en := io.executeStage.inst1.info.fusel === FuType.mdu &&
    !MDUOpType.isDiv(io.executeStage.inst1.info.op)
  fu.inst(1).div_en := io.executeStage.inst1.info.fusel === FuType.mdu &&
    MDUOpType.isDiv(io.executeStage.inst1.info.op)
  fu.inst(1).info  := io.executeStage.inst1.info
  fu.inst(1).src_info   := io.executeStage.inst1.src_info
  fu.inst(1).ex.in      := io.executeStage.inst1.ex
  fu.branch.pred_branch := io.executeStage.inst0.jb_info.pred_branch

  io.bpu.pc               := io.executeStage.inst0.pc
  io.bpu.update_pht_index := io.executeStage.inst0.jb_info.update_pht_index
  io.bpu.branch           := fu.branch.branch
  io.bpu.branch_inst      := io.executeStage.inst0.jb_info.branch_inst

  io.fetchUnit.branch := io.ctrl.branch
  io.fetchUnit.target := MuxCase(
    io.executeStage.inst0.pc + 4.U, // 默认顺序运行吧
    Seq(
      (fu.branch.pred_fail && fu.branch.branch)    -> io.executeStage.inst0.jb_info.branch_target,
      (fu.branch.pred_fail && !fu.branch.branch)   -> (io.executeStage.inst0.pc + 4.U),
      (io.executeStage.inst0.jb_info.jump_regiser) -> (io.executeStage.inst0.src_info.src1_data + io.executeStage.inst0.src_info.src2_data)
    )
  )

  io.ctrl.fu_stall := fu.stall_req

  io.memoryStage.inst0.mem.en        := accessMemCtrl.mem.out.en
  io.memoryStage.inst0.mem.ren       := accessMemCtrl.mem.out.ren
  io.memoryStage.inst0.mem.wen       := accessMemCtrl.mem.out.wen
  io.memoryStage.inst0.mem.addr      := accessMemCtrl.mem.out.addr
  io.memoryStage.inst0.mem.wdata     := accessMemCtrl.mem.out.wdata
  io.memoryStage.inst0.mem.sel       := accessMemCtrl.inst.map(_.mem_sel)
  io.memoryStage.inst0.mem.info := accessMemCtrl.mem.out.info

  io.memoryStage.inst0.pc                        := io.executeStage.inst0.pc
  io.memoryStage.inst0.info                 := io.executeStage.inst0.info
  io.memoryStage.inst0.rd_info.wdata(FuType.alu) := fu.inst(0).result.alu
  io.memoryStage.inst0.rd_info.wdata(FuType.mdu) := fu.inst(0).result.mdu
  io.memoryStage.inst0.rd_info.wdata(FuType.csr) := io.csr.out.rdata
  io.memoryStage.inst0.rd_info.wdata(FuType.lsu) := 0.U
  io.memoryStage.inst0.rd_info.wdata(FuType.mou) := 0.U
  val has_ex0 =
    (io.executeStage.inst0.ex.exception.asUInt.orR || io.executeStage.inst0.ex.interrupt.asUInt.orR) && io.executeStage.inst0.info.valid
  io.memoryStage.inst0.ex := Mux(
    has_ex0,
    io.executeStage.inst0.ex,
    MuxLookup(io.executeStage.inst0.info.fusel, io.executeStage.inst0.ex)(
      Seq(
        FuType.alu -> fu.inst(0).ex.out,
        FuType.mdu -> fu.inst(0).ex.out,
        FuType.lsu -> accessMemCtrl.inst(0).ex.out,
        FuType.csr -> io.csr.out.ex
      )
    )
  )

  io.memoryStage.inst1.pc                        := io.executeStage.inst1.pc
  io.memoryStage.inst1.info                 := io.executeStage.inst1.info
  io.memoryStage.inst1.rd_info.wdata(FuType.alu) := fu.inst(1).result.alu
  io.memoryStage.inst1.rd_info.wdata(FuType.mdu) := fu.inst(1).result.mdu
  io.memoryStage.inst1.rd_info.wdata(FuType.csr) := io.csr.out.rdata
  io.memoryStage.inst1.rd_info.wdata(FuType.lsu) := 0.U
  io.memoryStage.inst1.rd_info.wdata(FuType.mou) := 0.U
  val has_ex1 =
    (io.executeStage.inst1.ex.exception.asUInt.orR || io.executeStage.inst1.ex.interrupt.asUInt.orR) && io.executeStage.inst1.info.valid
  io.memoryStage.inst1.ex := Mux(
    has_ex1,
    io.executeStage.inst1.ex,
    MuxLookup(io.executeStage.inst1.info.fusel, io.executeStage.inst1.ex)(
      Seq(
        FuType.alu -> fu.inst(1).ex.out,
        FuType.mdu -> fu.inst(1).ex.out,
        FuType.lsu -> accessMemCtrl.inst(1).ex.out,
        FuType.csr -> io.csr.out.ex
      )
    )
  )

  io.decoderUnit.forward(0).exe.wen      := io.memoryStage.inst0.info.reg_wen
  io.decoderUnit.forward(0).exe.waddr    := io.memoryStage.inst0.info.reg_waddr
  io.decoderUnit.forward(0).exe.wdata    := io.memoryStage.inst0.rd_info.wdata(io.memoryStage.inst0.info.fusel)
  io.decoderUnit.forward(0).exe_mem_wreg := io.memoryStage.inst0.info.mem_wreg

  io.decoderUnit.forward(1).exe.wen      := io.memoryStage.inst1.info.reg_wen
  io.decoderUnit.forward(1).exe.waddr    := io.memoryStage.inst1.info.reg_waddr
  io.decoderUnit.forward(1).exe.wdata    := io.memoryStage.inst1.rd_info.wdata(io.memoryStage.inst1.info.fusel)
  io.decoderUnit.forward(1).exe_mem_wreg := io.memoryStage.inst1.info.mem_wreg
}
