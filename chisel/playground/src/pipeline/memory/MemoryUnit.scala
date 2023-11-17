// package cpu.pipeline.memory

// import chisel3._
// import chisel3.util._
// import cpu.defines._
// import cpu.defines.Const._
// import cpu.CpuConfig
// import cpu.pipeline.decoder.RegWrite
// import cpu.pipeline.execute.CsrMemoryUnit
// import cpu.pipeline.writeback.MemoryUnitWriteBackUnit

// class MemoryUnit(implicit val config: CpuConfig) extends Module {
//   val io = IO(new Bundle {
//     val ctrl        = new MemoryCtrl()
//     val memoryStage = Input(new ExecuteUnitMemoryUnit())
//     val fetchUnit = Output(new Bundle {
//       val flush    = Bool()
//       val flush_pc = UInt(PC_WID.W)
//     })
//     val decoderUnit    = Output(Vec(config.fuNum, new RegWrite()))
//     val csr            = Flipped(new CsrMemoryUnit())
//     val writeBackStage = Output(new MemoryUnitWriteBackUnit())
//     val dataMemory = new Bundle {
//       val in = Input(new Bundle {
//         val rdata = UInt(DATA_WID.W)
//       })
//       val out = Output(new Bundle {
//         val en    = Bool()
//         val rlen  = UInt(2.W)
//         val wen   = UInt(4.W)
//         val addr  = UInt(DATA_ADDR_WID.W)
//         val wdata = UInt(DATA_WID.W)
//       })
//     }
//   })

//   val dataMemoryAccess = Module(new DataMemoryAccess()).io
//   dataMemoryAccess.memoryUnit.in.mem_en    := io.memoryStage.inst0.mem.en
//   dataMemoryAccess.memoryUnit.in.inst_info := io.memoryStage.inst0.mem.inst_info
//   dataMemoryAccess.memoryUnit.in.mem_wdata := io.memoryStage.inst0.mem.wdata
//   dataMemoryAccess.memoryUnit.in.mem_addr  := io.memoryStage.inst0.mem.addr
//   dataMemoryAccess.memoryUnit.in.mem_sel   := io.memoryStage.inst0.mem.sel
//   dataMemoryAccess.memoryUnit.in.ex(0)     := io.memoryStage.inst0.ex
//   dataMemoryAccess.memoryUnit.in.ex(1)     := io.memoryStage.inst1.ex
//   dataMemoryAccess.dataMemory.in.rdata     := io.dataMemory.in.rdata
//   dataMemoryAccess.memoryUnit.in.llbit     := io.memoryStage.inst0.mem.llbit
//   io.dataMemory.out                        := dataMemoryAccess.dataMemory.out

//   io.decoderUnit(0).wen   := io.writeBackStage.inst0.inst_info.reg_wen
//   io.decoderUnit(0).waddr := io.writeBackStage.inst0.inst_info.reg_waddr
//   io.decoderUnit(0).wdata := io.writeBackStage.inst0.rd_info.wdata
//   io.decoderUnit(1).wen   := io.writeBackStage.inst1.inst_info.reg_wen
//   io.decoderUnit(1).waddr := io.writeBackStage.inst1.inst_info.reg_waddr
//   io.decoderUnit(1).wdata := io.writeBackStage.inst1.rd_info.wdata

//   io.writeBackStage.inst0.pc        := io.memoryStage.inst0.pc
//   io.writeBackStage.inst0.inst_info := io.memoryStage.inst0.inst_info
//   io.writeBackStage.inst0.rd_info.wdata := Mux(
//     io.writeBackStage.inst0.inst_info.mem_wreg,
//     dataMemoryAccess.memoryUnit.out.rdata,
//     io.memoryStage.inst0.rd_info.wdata,
//   )
//   io.writeBackStage.inst0.ex := io.memoryStage.inst0.ex
//   io.writeBackStage.inst0.ex.excode := MuxCase(
//     io.memoryStage.inst0.ex.excode,
//     Seq(
//       (io.memoryStage.inst0.ex.excode =/= EX_NO) -> io.memoryStage.inst0.ex.excode,
//     ),
//   )
//   io.writeBackStage.inst0.ex.flush_req := io.memoryStage.inst0.ex.flush_req || io.writeBackStage.inst0.ex.excode =/= EX_NO
//   io.writeBackStage.inst0.csr := io.memoryStage.inst0.csr

//   io.writeBackStage.inst1.pc        := io.memoryStage.inst1.pc
//   io.writeBackStage.inst1.inst_info := io.memoryStage.inst1.inst_info
//   io.writeBackStage.inst1.rd_info.wdata := Mux(
//     io.writeBackStage.inst1.inst_info.mem_wreg,
//     dataMemoryAccess.memoryUnit.out.rdata,
//     io.memoryStage.inst1.rd_info.wdata,
//   )
//   io.writeBackStage.inst1.ex := io.memoryStage.inst1.ex
//   io.writeBackStage.inst1.ex.excode := MuxCase(
//     io.memoryStage.inst1.ex.excode,
//     Seq(
//       (io.memoryStage.inst1.ex.excode =/= EX_NO) -> io.memoryStage.inst1.ex.excode,
//     ),
//   )
//   io.writeBackStage.inst1.ex.flush_req := io.memoryStage.inst1.ex.flush_req || io.writeBackStage.inst1.ex.excode =/= EX_NO

//   io.csr.in.inst(0).pc := io.writeBackStage.inst0.pc
//   io.csr.in.inst(0).ex := io.writeBackStage.inst0.ex
//   io.csr.in.inst(1).pc := io.writeBackStage.inst1.pc
//   io.csr.in.inst(1).ex := io.writeBackStage.inst1.ex

//   io.fetchUnit.flush := Mux(
//     io.csr.out.flush,
//     io.csr.out.flush,
//     io.writeBackStage.inst0.inst_info.op === EXE_MTC0 && io.ctrl.allow_to_go,
//   )
//   io.fetchUnit.flush_pc := Mux(io.csr.out.flush, io.csr.out.flush_pc, io.writeBackStage.inst0.pc + 4.U)

//   io.ctrl.flush_req := io.fetchUnit.flush
//   io.ctrl.eret      := io.writeBackStage.inst0.ex.eret
// }
