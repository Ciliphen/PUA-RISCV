package icache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.pipeline.execute.CsrTlb
import cpu.CpuConfig

object AccessType {
  def apply() = UInt(2.W)
  def fetch   = "b00".U
  def load    = "b01".U
  def store   = "b10".U
}

class Tlb_Ptw extends Bundle with Sv39Const {
  val vpn         = Decoupled(UInt(vpnLen.W))
  val access_type = Output(AccessType())
  val pte = Flipped(Decoupled(new Bundle {
    val access_fault = Bool()
    val page_fault   = Bool()
    val entry        = pteBundle
    val addr         = UInt(PADDR_WID.W)
  }))
}

class Tlb_ICache extends Bundle with Sv39Const {
  val en                      = Input(Bool())
  val addr                    = Input(UInt(XLEN.W))
  val complete_single_request = Input(Bool())

  val uncached     = Output(Bool())
  val hit          = Output(Bool())
  val ptag         = Output(UInt(cacheTagLen.W))
  val paddr        = Output(UInt(PADDR_WID.W))
  val access_fault = Output(Bool())
  val page_fault   = Output(Bool())
}

class Tlb_DCache extends Tlb_ICache {
  val access_type = Input(AccessType())

  // ptw 相关参数
  val ptw = new Tlb_Ptw()
  val csr = new CsrTlb()
}

class Tlb extends Module with HasTlbConst with HasCSRConst {
  val io = IO(new Bundle {
    val icache     = new Tlb_ICache()
    val dcache     = new Tlb_DCache()
    val csr        = Flipped(new CsrTlb())
    val sfence_vma = Input(new MouTlb())
  })

  val satp    = io.csr.satp.asTypeOf(satpBundle)
  val mstatus = io.csr.mstatus.asTypeOf(new Mstatus)
  val mode    = io.csr.mode
  //  当SUM=0时，S模式内存访问U模式可访问的页面（U=1）将出现故障。
  //  当SUM=1时，这些访问是允许的。当基于页面的虚拟内存不生效时，SUM无效。
  //  请注意，虽然SUM通常在不在S模式下执行时被忽略，但当MPRV=1和MPP=S时，SUM有效。
  val sum_valid = (mode === ModeS) || mstatus.mprv && mstatus.mpp === ModeS
  val sum       = mstatus.sum
  // 当MXR=0时，只有标记为可读的页面（R=1）的加载才会成功。
  // 当MXR=1时，标记为可读或可执行的页面（R=1或X=1）的加载才会成功。
  // 当基于页面的虚拟内存无效时，MXR无效。
  val mxr = mstatus.mxr

  // 只有当satp.mode为8且当前模式低于M模式时，才启用虚拟内存
  val vm_enabled = (satp.mode === 8.U) && (mode < ModeM)

  val itlb  = RegInit(0.U.asTypeOf(tlbBundle))
  val dtlb  = RegInit(0.U.asTypeOf(tlbBundle))
  val tlbl2 = RegInit(VecInit(Seq.fill(cpuConfig.tlbEntries)(0.U.asTypeOf(tlbBundle))))

  val ivpn = io.icache.addr(VADDR_WID - 1, pageOffsetLen)
  val dvpn = io.dcache.addr(VADDR_WID - 1, pageOffsetLen)

  // 当(VPN一致)且(ASID一致或PTE.G为1时)且(PTE.V为1)时，TLB命中
  val itlbl1_hit = itlb.vpn === ivpn &&
    (itlb.asid === satp.asid || itlb.flag.g) &&
    itlb.flag.v
  val dtlbl1_hit = dtlb.vpn === dvpn &&
    (dtlb.asid === satp.asid || dtlb.flag.g) &&
    dtlb.flag.v

  val il2_hit_vec = VecInit(
    tlbl2.map(tlb =>
      tlb.vpn === ivpn &&
        (tlb.asid === satp.asid || tlb.flag.g) &&
        tlb.flag.v
    )
  )
  val dl2_hit_vec = VecInit(
    tlbl2.map(tlb =>
      tlb.vpn === dvpn &&
        (tlb.asid === satp.asid || tlb.flag.g) &&
        tlb.flag.v
    )
  )

  val search_l1 :: search_l2 :: search_pte :: search_fault :: Nil = Enum(4)
  val immu_state                                                  = RegInit(search_l1)
  val dmmu_state                                                  = RegInit(search_l1)

  // 使用随机的方法替换TLB条目
  val replace_index = new Counter(cpuConfig.tlbEntries)
  replace_index.inc()

  val ipage_fault   = RegInit(false.B)
  val dpage_fault   = RegInit(false.B)
  val iaccess_fault = RegInit(false.B)
  val daccess_fault = RegInit(false.B)

  // ptw的请求标志，0位为指令tlb请求，1位为数据tlb请求
  val req_ptw = WireInit(VecInit(Seq.fill(2)(false.B)))

  val ar_sel_lock = RegInit(false.B)
  val ar_sel_val  = RegInit(false.B)
  // 我们默认优先发送数据tlb的请求
  val ar_sel = Mux(ar_sel_lock, ar_sel_val, !req_ptw(0) && req_ptw(1))

  when(io.dcache.ptw.vpn.valid) {
    when(io.dcache.ptw.vpn.ready) {
      ar_sel_lock := false.B
    }.otherwise {
      ar_sel_lock := true.B
      ar_sel_val  := ar_sel
    }
  }

  io.icache.hit          := false.B
  io.dcache.hit          := false.B
  io.icache.access_fault := iaccess_fault
  io.dcache.access_fault := daccess_fault
  io.icache.page_fault   := ipage_fault
  io.dcache.page_fault   := dpage_fault

  // 将ptw模块集成到dcache中，ptw通过dcache的axi进行内存访问
  io.dcache.ptw.vpn.valid   := false.B
  io.dcache.ptw.access_type := Mux(ar_sel === 0.U, AccessType.fetch, io.dcache.access_type)
  io.dcache.ptw.vpn.bits    := Mux(ar_sel === 0.U, ivpn, dvpn)
  io.dcache.ptw.pte.ready   := true.B // 恒为true
  io.dcache.csr <> io.csr

  // ---------------------------------------------------
  // ----------------- 指令虚实地址转换 -----------------
  // ---------------------------------------------------
  switch(immu_state) {
    is(search_l1) {
      when(io.icache.en) {
        // 在icache实现访问tlb的pma和pmp权限检查
        ipage_fault   := false.B
        iaccess_fault := false.B
        when(!vm_enabled) {
          io.icache.hit := true.B
        }.elsewhen(itlbl1_hit) {
          // 在这里进行取指需要的所有的权限检查
          // 0. X位检查，只有可执行的页面才能取指
          // 1. M模式，不可能到这里，因为vm_enabled为false
          // 2. S模式，如果U位为1，需要检查SUM
          // 3. U模式，必须保证U位为1
          io.icache.hit := false.B // 只有权限检查通过后可以置为true
          when(!itlb.flag.x) {
            ipage_fault := true.B
            immu_state  := search_fault
          }.elsewhen(mode === ModeS) {
            when(itlb.flag.u && sum === 0.U) {
              ipage_fault := true.B
              immu_state  := search_fault
            }.otherwise {
              io.icache.hit := true.B
            }
          }.elsewhen(mode === ModeU) {
            when(!itlb.flag.u) {
              ipage_fault := true.B
              immu_state  := search_fault
            }.otherwise {
              io.icache.hit := true.B
            }
          }
        }.otherwise {
          immu_state := search_l2
        }
      }
    }
    is(search_l2) {
      when(il2_hit_vec.asUInt.orR) {
        immu_state := search_l1
        itlb       := tlbl2(PriorityEncoder(il2_hit_vec))
      }.otherwise {
        req_ptw(0) := true.B
        when(ar_sel === 0.U && io.dcache.ptw.vpn.ready) {
          io.dcache.ptw.vpn.valid := true.B
          immu_state              := search_pte
        }
      }
    }
    is(search_pte) {
      io.dcache.ptw.vpn.valid := true.B
      when(io.dcache.ptw.pte.valid) {
        when(io.dcache.ptw.pte.bits.access_fault) {
          iaccess_fault := true.B
          immu_state    := search_fault
        }.elsewhen(io.dcache.ptw.pte.bits.page_fault) {
          ipage_fault := true.B
          immu_state  := search_fault
        }.otherwise {
          // 在内存中找寻到了页表，将其写入TLB
          val replace_entry = Wire(tlbBundle)
          replace_entry.vpn          := ivpn
          replace_entry.asid         := satp.asid
          replace_entry.flag         := io.dcache.ptw.pte.bits.entry.flag
          replace_entry.ppn          := io.dcache.ptw.pte.bits.entry.ppn
          replace_entry.pteaddr      := io.dcache.ptw.pte.bits.addr
          tlbl2(replace_index.value) := replace_entry
          itlb                       := replace_entry
          immu_state                 := search_l1
        }
      }
    }
    is(search_fault) {
      when(io.icache.complete_single_request) {
        ipage_fault   := false.B
        iaccess_fault := false.B
        immu_state    := search_l1
      }
    }
  }

  // ---------------------------------------------------
  // ----------------- 指令虚实地址转换 -----------------
  // ---------------------------------------------------
  switch(dmmu_state) {
    is(search_l1) {
      when(io.dcache.en) {
        // 在dcache实现访问tlb的pma和pmp权限检查
        dpage_fault   := false.B
        daccess_fault := false.B
        when(!vm_enabled) {
          io.dcache.hit := true.B
        }.elsewhen(dtlbl1_hit) {
          // 在这里进行取指需要的所有的权限检查
          // 0. X位检查，只有可执行的页面才能取指
          // 1. M模式，不可能到这里，因为vm_enabled为false
          // 2. S模式，如果U位为1，需要检查SUM
          // 3. U模式，必须保证U位为1
          io.dcache.hit := false.B // 只有权限检查通过后可以置为true
          // TODO:增加权限检查

        }.otherwise {
          dmmu_state := search_l2
        }
      }
    }
    is(search_l2) {
      when(il2_hit_vec.asUInt.orR) {
        dmmu_state := search_l1
        dtlb       := tlbl2(PriorityEncoder(il2_hit_vec))
      }.otherwise {
        req_ptw(0) := true.B
        when(ar_sel === 0.U && io.dcache.ptw.vpn.ready) {
          io.dcache.ptw.vpn.valid := true.B
          dmmu_state              := search_pte
        }
      }
    }
    is(search_pte) {
      io.dcache.ptw.vpn.valid := true.B
      when(io.dcache.ptw.pte.valid) {
        when(io.dcache.ptw.pte.bits.access_fault) {
          daccess_fault := true.B
          dmmu_state    := search_fault
        }.elsewhen(io.dcache.ptw.pte.bits.page_fault) {
          dpage_fault := true.B
          dmmu_state  := search_fault
        }.otherwise {
          // 在内存中找寻到了页表，将其写入TLB
          val replace_entry = Wire(tlbBundle)
          replace_entry.vpn          := ivpn
          replace_entry.asid         := satp.asid
          replace_entry.flag         := io.dcache.ptw.pte.bits.entry.flag
          replace_entry.ppn          := io.dcache.ptw.pte.bits.entry.ppn
          replace_entry.pteaddr      := io.dcache.ptw.pte.bits.addr
          tlbl2(replace_index.value) := replace_entry
          dtlb                       := replace_entry
          dmmu_state                 := search_l1
        }
      }
    }
    is(search_fault) {
      when(io.dcache.complete_single_request) {
        dpage_fault   := false.B
        daccess_fault := false.B
        dmmu_state    := search_l1
      }
    }
  }

  val src1 = io.sfence_vma.src_info.src1_data
  val src2 = io.sfence_vma.src_info.src2_data
  when(io.sfence_vma.valid) {
    when(!src1.orR && !src2.orR) {
      // 将所有tlb的有效位置为0
      itlb.flag.v := false.B
      dtlb.flag.v := false.B
      for (i <- 0 until cpuConfig.tlbEntries) {
        tlbl2(i).flag.v := false.B
      }
    }.elsewhen(!src1.orR && src2.orR) {
      // 将asid一致的且g不为1的tlb的有效位置为0
      when(itlb.asid === src2 && !itlb.flag.g) {
        itlb.flag.v := false.B
      }
      when(dtlb.asid === src2 && !dtlb.flag.g) {
        dtlb.flag.v := false.B
      }
      for (i <- 0 until cpuConfig.tlbEntries) {
        when(tlbl2(i).asid === src2 && !tlbl2(i).flag.g) {
          tlbl2(i).flag.v := false.B
        }
      }
    }.elsewhen(src1.orR && !src2.orR) {
      // 将vpn一致的tlb的有效位置为0
      when(itlb.vpn === src1) {
        itlb.flag.v := false.B
      }
      when(dtlb.vpn === src1) {
        dtlb.flag.v := false.B
      }
      for (i <- 0 until cpuConfig.tlbEntries) {
        when(tlbl2(i).vpn === src1) {
          tlbl2(i).flag.v := false.B
        }
      }
    }.elsewhen(src1.orR && src2.orR) {
      // 将asid一致的且vpn一致的tlb的有效位置为0，g为1的除外
      when(itlb.asid === src2 && itlb.vpn === src1 && !itlb.flag.g) {
        itlb.flag.v := false.B
      }
      when(dtlb.asid === src2 && dtlb.vpn === src1 && !dtlb.flag.g) {
        dtlb.flag.v := false.B
      }
      for (i <- 0 until cpuConfig.tlbEntries) {
        when(tlbl2(i).asid === src2 && tlbl2(i).vpn === src1 && !tlbl2(i).flag.g) {
          tlbl2(i).flag.v := false.B
        }
      }
    }
  }

  io.icache.uncached := AddressSpace.isMMIO(io.icache.addr)
  io.icache.ptag     := Mux(vm_enabled, itlb.ppn, io.icache.addr(PADDR_WID - 1, pageOffsetLen))
  io.icache.paddr    := Cat(io.icache.ptag, io.icache.addr(pageOffsetLen - 1, 0))

  io.dcache.uncached := AddressSpace.isMMIO(io.dcache.addr)
  io.dcache.ptag     := Mux(vm_enabled, dtlb.ppn, io.dcache.addr(PADDR_WID - 1, pageOffsetLen))
  io.dcache.paddr    := Cat(io.dcache.ptag, io.dcache.addr(pageOffsetLen - 1, 0))

}
