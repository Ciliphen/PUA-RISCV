package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Fu(implicit val cpuConfig: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = new ExecuteFuCtrl()
    val inst = Vec(
      cpuConfig.decoderNum,
      new Bundle {
        val pc       = Input(UInt(XLEN.W))
        val info     = Input(new Info())
        val src_info = Input(new SrcInfo())
        val result = Output(new Bundle {
          val mdu = UInt(XLEN.W)
          val alu = UInt(XLEN.W)
        })
      }
    )
    val dataMemory = new Bundle {
      val addr = Output(UInt(XLEN.W))
    }
    val branch = new Bundle {
      val pred_branch   = Input(Bool())
      val jump_regiser  = Input(Bool())
      val branch_target = Input(UInt(XLEN.W))
      val branch        = Output(Bool())
      val flush         = Output(Bool())
      val target        = Output(UInt(XLEN.W))
    }
  })

  val alu        = Seq.fill(cpuConfig.decoderNum)(Module(new Alu()))
  val branchCtrl = Module(new BranchCtrl()).io
  val mdu        = Module(new Mdu()).io

  branchCtrl.in.pc            := io.inst(0).pc
  branchCtrl.in.info          := io.inst(0).info
  branchCtrl.in.src_info      := io.inst(0).src_info
  branchCtrl.in.pred_branch   := io.branch.pred_branch
  branchCtrl.in.jump_regiser  := io.branch.jump_regiser
  branchCtrl.in.branch_target := io.branch.branch_target
  io.branch.branch            := branchCtrl.out.branch

  val branchCtrl_flush = (branchCtrl.out.pred_fail || io.branch.jump_regiser)
  io.branch.flush  := branchCtrl_flush
  io.branch.target := branchCtrl.out.target

  for (i <- 0 until (cpuConfig.commitNum)) {
    alu(i).io.info     := Mux(io.inst(i).info.fusel === FuType.alu, io.inst(i).info, 0.U.asTypeOf(new Info()))
    alu(i).io.src_info := Mux(io.inst(i).info.fusel === FuType.alu, io.inst(i).src_info, 0.U.asTypeOf(new SrcInfo()))
  }

  val mdu_sel = VecInit(
    io.inst(0).info.fusel === FuType.mdu,
    io.inst(1).info.fusel === FuType.mdu
  )

  mdu.info := MuxCase(
    0.U.asTypeOf(new Info()),
    Seq(mdu_sel(0) -> io.inst(0).info, mdu_sel(1) -> io.inst(1).info)
  )
  mdu.src_info := MuxCase(
    0.U.asTypeOf(new SrcInfo()),
    Seq(mdu_sel(0) -> io.inst(0).src_info, mdu_sel(1) -> io.inst(1).src_info)
  )
  mdu.allow_to_go := io.ctrl.allow_to_go

  io.ctrl.stall := io.inst.map(_.info.fusel === FuType.mdu).reduce(_ || _) && !mdu.ready

  io.inst(0).result.alu := alu(0).io.result
  io.inst(0).result.mdu := mdu.result

  io.inst(1).result.alu := alu(1).io.result
  io.inst(1).result.mdu := mdu.result

  val mem_addr = Seq.tabulate(cpuConfig.commitNum)(i =>
    Mux(
      LSUOpType.isAMO(io.inst(i).info.op) || LSUOpType.isLR(io.inst(i).info.op),
      io.inst(i).src_info.src1_data,
      io.inst(i).src_info.src1_data + io.inst(i).info.imm
    )
  )
  // 当mem级访存发生stall时，mem_addr_last不变
  val mem_addr_last = RegEnable(io.dataMemory.addr, io.ctrl.allow_to_go)
  io.dataMemory.addr := Mux(
    !io.ctrl.allow_to_go,
    mem_addr_last,
    Mux(io.inst(0).info.fusel === FuType.lsu, mem_addr(0), mem_addr(1))
  )
}
