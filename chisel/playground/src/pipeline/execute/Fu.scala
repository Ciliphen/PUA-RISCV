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
        val pc        = Input(UInt(PC_WID.W))
        val mul_en    = Input(Bool())
        val div_en    = Input(Bool())
        val info = Input(new InstInfo())
        val src_info  = Input(new SrcInfo())
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
      val pred_branch = Input(Bool())
      val branch      = Output(Bool())
      val pred_fail   = Output(Bool())
    }
  })

  val alu = Seq.fill(config.decoderNum)(Module(new Alu()))
//   val mul        = Module(new Mul()).io
//   val div        = Module(new Div()).io
  val branchCtrl = Module(new BranchCtrl()).io

  branchCtrl.in.info   := io.inst(0).info
  branchCtrl.in.src_info    := io.inst(0).src_info
  branchCtrl.in.pred_branch := io.branch.pred_branch
  io.branch.branch          := branchCtrl.out.branch
  io.branch.pred_fail       := branchCtrl.out.pred_fail

  for (i <- 0 until (config.fuNum)) {
    alu(i).io.info := io.inst(i).info
    alu(i).io.src_info  := io.inst(i).src_info
    // alu(i).io.mul.result        := mul.result
    // alu(i).io.mul.ready         := mul.ready
    // alu(i).io.div.ready         := div.ready
    // alu(i).io.div.result        := div.result
    io.inst(i).ex.out        := io.inst(i).ex.in
    io.inst(i).ex.out.exception := io.inst(i).ex.in.exception
  }

//   mul.src1        := Mux(io.inst(0).mul_en, io.inst(0).src_info.src1_data, io.inst(1).src_info.src1_data)
//   mul.src2        := Mux(io.inst(0).mul_en, io.inst(0).src_info.src2_data, io.inst(1).src_info.src2_data)
//   mul.signed      := Mux(io.inst(0).mul_en, alu(0).io.mul.signed, alu(1).io.mul.signed)
//   mul.start       := Mux(io.inst(0).mul_en, alu(0).io.mul.en, alu(1).io.mul.en)
//   mul.allow_to_go := io.ctrl.allow_to_go

//   div.src1        := Mux(io.inst(0).div_en, io.inst(0).src_info.src1_data, io.inst(1).src_info.src1_data)
//   div.src2        := Mux(io.inst(0).div_en, io.inst(0).src_info.src2_data, io.inst(1).src_info.src2_data)
//   div.signed      := Mux(io.inst(0).div_en, alu(0).io.div.signed, alu(1).io.div.signed)
//   div.start       := Mux(io.inst(0).div_en, alu(0).io.div.en, alu(1).io.div.en)
//   div.allow_to_go := io.ctrl.allow_to_go

//   io.stall_req := (io.inst.map(_.div_en).reduce(_ || _) && !div.ready) ||
//     (io.inst.map(_.mul_en).reduce(_ || _) && !mul.ready)
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
