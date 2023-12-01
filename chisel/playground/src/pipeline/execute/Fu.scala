package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Fu(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ctrl = new ExecuteFuCtrl()
    val inst = Vec(
      config.decoderNum,
      new Bundle {
        val pc       = Input(UInt(PC_WID.W))
        val info     = Input(new InstInfo())
        val src_info = Input(new SrcInfo())
        val ex = new Bundle {
          val in  = Input(new ExceptionInfo())
          val out = Output(new ExceptionInfo())
        }
        val result = Output(new Bundle {
          val mdu = UInt(DATA_WID.W)
          val alu = UInt(DATA_WID.W)
        })
      }
    )
    val stall_req = Output(Bool())
    val branch = new Bundle {
      val pred_branch   = Input(Bool())
      val jump_regiser  = Input(Bool())
      val branch_target = Input(UInt(PC_WID.W))
      val branch        = Output(Bool())
      val flush         = Output(Bool())
      val target        = Output(UInt(PC_WID.W))
    }
  })

  val alu        = Seq.fill(config.decoderNum)(Module(new Alu()))
  val branchCtrl = Module(new BranchCtrl()).io

  branchCtrl.in.pc            := io.inst(0).pc
  branchCtrl.in.info          := io.inst(0).info
  branchCtrl.in.src_info      := io.inst(0).src_info
  branchCtrl.in.pred_branch   := io.branch.pred_branch
  branchCtrl.in.jump_regiser  := io.branch.jump_regiser
  branchCtrl.in.branch_target := io.branch.branch_target
  io.branch.branch            := branchCtrl.out.branch
  io.branch.flush             := (branchCtrl.out.pred_fail || io.branch.jump_regiser)
  io.branch.target            := branchCtrl.out.target

  for (i <- 0 until (config.fuNum)) {
    alu(i).io.info              := io.inst(i).info
    alu(i).io.src_info          := io.inst(i).src_info
    io.inst(i).ex.out           := io.inst(i).ex.in
    io.inst(i).ex.out.exception := io.inst(i).ex.in.exception
  }

  io.stall_req := false.B

  io.inst(0).result.alu := Mux(
    ALUOpType.isBru(io.inst(0).info.op),
    io.inst(0).pc + 4.U,
    alu(0).io.result
  )
  io.inst(0).result.mdu := DontCare
  io.inst(1).result.alu := alu(1).io.result
  io.inst(1).result.mdu := DontCare
}
