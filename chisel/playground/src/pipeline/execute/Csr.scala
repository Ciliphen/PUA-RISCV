package cpu.pipeline.execute

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.pipeline.memory.CsrInfo
import cpu.CpuConfig
import cpu.pipeline.decoder.CsrDecoderUnit

class CsrMemoryUnit(implicit val config: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val inst = Vec(
      config.fuNum,
      new Bundle {
        val pc = UInt(PC_WID.W)
        val ex = new ExceptionInfo()
      }
    )
  })
  val out = Output(new Bundle {
    val flush    = Bool()
    val flush_pc = UInt(PC_WID.W)
  })
}

class CsrExecuteUnit(implicit val config: CpuConfig) extends Bundle {
  val in = Input(new Bundle {
    val inst_info  = Vec(config.fuNum, new InstInfo())
    val mtc0_wdata = UInt(DATA_WID.W)
  })
  val out = Output(new Bundle {
    val csr_rdata = Vec(config.fuNum, UInt(DATA_WID.W))
    val debug     = Output(new CsrInfo())
  })
}

class Csr(implicit val config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val ext_int = Input(UInt(EXT_INT_WID.W))
    val ctrl = Input(new Bundle {
      val exe_stall = Bool()
      val mem_stall = Bool()
    })
    val decoderUnit = Output(new CsrDecoderUnit())
    val executeUnit = new CsrExecuteUnit()
    val memoryUnit  = new CsrMemoryUnit()
  })
  // 优先使用inst0的信息
  val ex_sel     = io.memoryUnit.in.inst(0).ex.flush_req || !io.memoryUnit.in.inst(1).ex.flush_req
  val pc         = Mux(ex_sel, io.memoryUnit.in.inst(0).pc, io.memoryUnit.in.inst(1).pc)
  val ex         = Mux(ex_sel, io.memoryUnit.in.inst(0).ex, io.memoryUnit.in.inst(1).ex)
  val mtc0_wen   = io.executeUnit.in.inst_info(0).op === EXE_MTC0
  val mtc0_wdata = io.executeUnit.in.mtc0_wdata
  val mtc0_addr  = io.executeUnit.in.inst_info(0).csr_addr
  val exe_op     = io.executeUnit.in.inst_info(0).op
  val exe_stall  = io.ctrl.exe_stall
  val mem_stall  = io.ctrl.mem_stall

  // ---------------csr-defines-----------------

  // index register (0,0)
  val csr_index = RegInit(0.U.asTypeOf(new CsrIndex()))

  // random register (1,0)
  val random_init = Wire(new CsrRandom())
  random_init        := 0.U.asTypeOf(new CsrRandom())
  random_init.random := (TLB_NUM - 1).U
  val csr_random = RegInit(random_init)

  // entrylo0 register (2,0)
  val csr_entrylo0 = RegInit(0.U.asTypeOf(new CsrEntryLo()))

  // entrylo1 register (3,0)
  val csr_entrylo1 = RegInit(0.U.asTypeOf(new CsrEntryLo()))

  // context register (4,0)
  val csr_context = RegInit(0.U.asTypeOf(new CsrContext()))

  // page mask register (5,0)
  val csr_pagemask = 0.U

  // wired register (6,0)
  val csr_wired = RegInit(0.U.asTypeOf(new CsrWired()))

  // badvaddr register (8,0)
  val csr_badvaddr = RegInit(0.U.asTypeOf(new CsrBadVAddr()))

  // count register (9,0)
  val count_init = Wire(new CsrCount())
  count_init       := 0.U.asTypeOf(new CsrCount())
  count_init.count := 1.U
  val csr_count = RegInit(count_init)

  // entryhi register (10,0)
  val csr_entryhi = RegInit(0.U.asTypeOf(new CsrEntryHi()))

  // compare register (11,0)
  val csr_compare = RegInit(0.U.asTypeOf(new CsrCompare()))

  // status register (12,0)
  val status_init = Wire(new CsrStatus())
  status_init     := 0.U.asTypeOf(new CsrStatus())
  status_init.bev := true.B
  val csr_status = RegInit(status_init)

  // cause register (13,0)
  val csr_cause = RegInit(0.U.asTypeOf(new CsrCause()))

  // epc register (14,0)
  val csr_epc = RegInit(0.U.asTypeOf(new CsrEpc()))

  // prid register (15,0)
  val prid = "h_0001_8003".U

  // ebase register (15,1)
  val ebase_init = Wire(new CsrEbase())
  ebase_init      := 0.U.asTypeOf(new CsrEbase())
  ebase_init.fill := true.B
  val csr_ebase = RegInit(ebase_init)

  // config register (16,0)
  val csr_config = Wire(new CsrConfig())
  csr_config    := 0.U.asTypeOf(new CsrConfig())
  csr_config.k0 := 3.U
  csr_config.mt := 1.U
  csr_config.m  := true.B

  // config1 register (16,1)
  val csr_config1 = Wire(new CsrConfig1())
  csr_config1    := 0.U.asTypeOf(new CsrConfig1())
  csr_config1.il := 5.U
  csr_config1.ia := 1.U
  csr_config1.dl := 5.U
  csr_config1.da := 1.U
  csr_config1.ms := (TLB_NUM - 1).U

  // taglo register (28,0)
  val csr_taglo = RegInit(0.U(DATA_WID.W))

  // taghi register (29,0)
  val csr_taghi = RegInit(0.U(DATA_WID.W))

  // error epc register (30,0)
  val csr_error_epc = RegInit(0.U.asTypeOf(new CsrEpc()))

  // random register (1,0)
  csr_random.random := Mux(csr_random.random === csr_wired.wired, (TLB_NUM - 1).U, (csr_random.random - 1.U))

  // context register (4,0)
  when(!mem_stall && ex.flush_req) {
    when(VecInit(EX_TLBL, EX_TLBS, EX_MOD).contains(ex.excode)) {
      csr_context.badvpn2 := ex.badvaddr(31, 13)
    }
  }.elsewhen(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_CONTEXT_ADDR) {
      csr_context.ptebase := mtc0_wdata.asTypeOf(new CsrContext()).ptebase
    }
  }

  // wired register (6,0)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_WIRED_ADDR) {
      csr_wired.wired   := mtc0_wdata.asTypeOf(new CsrWired()).wired
      csr_random.random := (TLB_NUM - 1).U
    }
  }

  // badvaddr register (8,0)
  when(!mem_stall && ex.flush_req) {
    when(VecInit(EX_ADEL, EX_TLBL, EX_ADES, EX_TLBS, EX_MOD).contains(ex.excode)) {
      csr_badvaddr.badvaddr := ex.badvaddr
    }
  }

  // count register (9,0)
  val tick = RegInit(false.B)
  tick := !tick
  when(tick) {
    csr_count.count := csr_count.count + 1.U
  }
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_COUNT_ADDR) {
      csr_count.count := mtc0_wdata.asTypeOf(new CsrCount()).count
    }
  }

  // entryhi register (10,0)
  when(!mem_stall && ex.flush_req) {
    when(VecInit(EX_TLBL, EX_TLBS, EX_MOD).contains(ex.excode)) {
      csr_entryhi.vpn2 := ex.badvaddr(31, 13)
    }
  }.elsewhen(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_ENTRYHI_ADDR) {
      val wdata = mtc0_wdata.asTypeOf(new CsrEntryHi())
      csr_entryhi.asid := wdata.asid
      csr_entryhi.vpn2 := wdata.vpn2
    }
  }

  // compare register (11,0)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_COMPARE_ADDR) {
      csr_compare.compare := mtc0_wdata.asTypeOf(new CsrCompare()).compare
    }
  }

  // status register (12,0)
  when(!mem_stall && ex.eret) {
    when(csr_status.erl) {
      csr_status.erl := false.B
    }.otherwise {
      csr_status.exl := false.B
    }
  }.elsewhen(!mem_stall && ex.flush_req) {
    csr_status.exl := true.B
  }.elsewhen(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_STATUS_ADDR) {
      val wdata = mtc0_wdata.asTypeOf(new CsrStatus())
      csr_status.cu0 := wdata.cu0
      csr_status.ie  := wdata.ie
      csr_status.exl := wdata.exl
      csr_status.erl := wdata.erl
      csr_status.um  := wdata.um
      csr_status.im  := wdata.im
      csr_status.bev := wdata.bev
    }
  }

  // cause register (13,0)
  csr_cause.ip := Cat(
    csr_cause.ip(7) || csr_compare.compare === csr_count.count || io.ext_int(5), // TODO:此处的ext_int可能不对
    io.ext_int(4, 0),
    csr_cause.ip(1, 0)
  )
  when(!mem_stall && ex.flush_req && !ex.eret) {
    when(!csr_status.exl) {
      csr_cause.bd := ex.bd
    }
    csr_cause.excode := MuxLookup(ex.excode, csr_cause.excode)(
      Seq(
        EX_NO -> EXC_NO,
        EX_INT -> EXC_INT,
        EX_MOD -> EXC_MOD,
        EX_TLBL -> EXC_TLBL,
        EX_TLBS -> EXC_TLBS,
        EX_ADEL -> EXC_ADEL,
        EX_ADES -> EXC_ADES,
        EX_SYS -> EXC_SYS,
        EX_BP -> EXC_BP,
        EX_RI -> EXC_RI,
        EX_CPU -> EXC_CPU,
        EX_OV -> EXC_OV
      )
    )
  }.elsewhen(!exe_stall) {
    when(mtc0_wen) {
      when(mtc0_addr === CSR_COMPARE_ADDR) {
        csr_cause.ip := Cat(false.B, csr_cause.ip(6, 0))
      }.elsewhen(mtc0_addr === CSR_CAUSE_ADDR) {
        val wdata = mtc0_wdata.asTypeOf(new CsrCause())
        csr_cause.ip := Cat(
          csr_cause.ip(7, 2),
          wdata.ip(1, 0)
        )
        csr_cause.iv := wdata.iv
      }
    }
  }

  // epc register (14,0)
  when(!mem_stall && ex.flush_req) {
    when(!csr_status.exl) {
      csr_epc.epc := Mux(ex.bd, pc - 4.U, pc)
    }
  }.elsewhen(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_EPC_ADDR) {
      csr_epc.epc := mtc0_wdata.asTypeOf(new CsrEpc()).epc
    }
  }

  // ebase register (15,1)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_EBASE_ADDR) {
      csr_ebase.ebase := mtc0_wdata.asTypeOf(new CsrEbase()).ebase
    }
  }

  // taglo register (28,0)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_TAGLO_ADDR) {
      csr_taglo := mtc0_wdata
    }
  }

  // taghi register (29,0)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_TAGHI_ADDR) {
      csr_taghi := mtc0_wdata
    }
  }

  // error epc register (30,0)
  when(!exe_stall) {
    when(mtc0_wen && mtc0_addr === CSR_ERROR_EPC_ADDR) {
      csr_error_epc.epc := mtc0_wdata.asTypeOf(new CsrEpc()).epc
    }
  }

  for (i <- 0 until config.fuNum) {
    io.executeUnit.out.csr_rdata(i) := MuxLookup(io.executeUnit.in.inst_info(i).csr_addr, 0.U)(
      Seq(
        CSR_INDEX_ADDR -> csr_index.asUInt,
        CSR_RANDOM_ADDR -> csr_random.asUInt,
        CSR_ENTRYLO0_ADDR -> csr_entrylo0.asUInt,
        CSR_ENTRYLO1_ADDR -> csr_entrylo1.asUInt,
        CSR_CONTEXT_ADDR -> csr_context.asUInt,
        CSR_PAGE_MASK_ADDR -> csr_pagemask,
        CSR_WIRED_ADDR -> csr_wired.asUInt,
        CSR_BADV_ADDR -> csr_badvaddr.asUInt,
        CSR_COUNT_ADDR -> csr_count.asUInt,
        CSR_ENTRYHI_ADDR -> csr_entryhi.asUInt,
        CSR_COMPARE_ADDR -> csr_compare.asUInt,
        CSR_STATUS_ADDR -> csr_status.asUInt,
        CSR_CAUSE_ADDR -> csr_cause.asUInt,
        CSR_EPC_ADDR -> csr_epc.asUInt,
        CSR_PRID_ADDR -> prid,
        CSR_EBASE_ADDR -> csr_ebase.asUInt,
        CSR_CONFIG_ADDR -> csr_config.asUInt,
        CSR_CONFIG1_ADDR -> csr_config1.asUInt,
        CSR_TAGLO_ADDR -> csr_taglo,
        CSR_TAGHI_ADDR -> csr_taghi,
        CSR_ERROR_EPC_ADDR -> csr_error_epc.asUInt
      )
    )
  }
  io.decoderUnit.cause_ip  := csr_cause.ip
  io.decoderUnit.status_im := csr_status.im
  io.decoderUnit.kernel_mode := (csr_status.exl && !(ex.eret && csr_status.erl)) ||
    (csr_status.erl && !ex.eret) ||
    !csr_status.um ||
    (ex.flush_req && !ex.eret)
  io.decoderUnit.access_allowed    := io.decoderUnit.kernel_mode || csr_status.cu0
  io.decoderUnit.intterupt_allowed := csr_status.ie && !csr_status.exl && !csr_status.erl

  io.executeUnit.out.debug.csr_cause  := csr_cause.asUInt
  io.executeUnit.out.debug.csr_count  := csr_count.asUInt
  io.executeUnit.out.debug.csr_random := csr_random.asUInt

  val trap_base = Mux(
    csr_status.bev,
    "hbfc00200".U(PC_WID.W),
    csr_ebase.asUInt
  )
  io.memoryUnit.out.flush    := false.B
  io.memoryUnit.out.flush_pc := 0.U
  when(ex.eret) {
    io.memoryUnit.out.flush    := true.B && !io.ctrl.mem_stall
    io.memoryUnit.out.flush_pc := Mux(csr_status.erl, csr_error_epc.epc, csr_epc.epc)
  }.elsewhen(ex.flush_req) {
    io.memoryUnit.out.flush := true.B && !io.ctrl.mem_stall
    io.memoryUnit.out.flush_pc := Mux(
      csr_status.exl,
      trap_base + "h180".U,
      trap_base + "h200".U
    )
  }
}
