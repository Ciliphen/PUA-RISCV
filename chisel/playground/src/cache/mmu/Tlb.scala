package icache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CacheConfig
import cpu.pipeline.CsrTlb
import cpu.CpuConfig

object AccessType {
  def apply() = UInt(2.W)
  def fetch   = "b00".U
  def load    = "b01".U
  def store   = "b10".U
}

class Tlb_Ptw extends Bundle with HasTlbConst {
  val vpn         = Decoupled(UInt(vpnLen.W))
  val access_type = Output(AccessType())
  val pte = Flipped(Decoupled(new Bundle {
    val access_fault = Bool()
    val page_fault   = Bool()
    val entry        = pteBundle
    val rmask        = UInt(maskLen.W)
  }))
}

class Tlb_ICache extends Bundle with HasTlbConst {
  val en                      = Input(Bool())
  val vaddr                   = Input(UInt(XLEN.W))
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
  val imode   = io.csr.imode
  val dmode   = io.csr.dmode
  //  当SUM=0时，S模式内存访问U模式可访问的页面（U=1）将出现故障。
  //  当SUM=1时，这些访问是允许的。当基于页面的虚拟内存不生效时，SUM无效。
  //  请注意，虽然SUM通常在不在S模式下执行时被忽略，但当MPRV=1和MPP=S时，SUM有效。
  val sum = mstatus.sum
  // 当MXR=0时，只有标记为可读的页面（R=1）的加载才会成功。
  // 当MXR=1时，标记为可读或可执行的页面（R=1或X=1）的加载才会成功。
  // 当基于页面的虚拟内存无效时，MXR无效。
  val mxr = mstatus.mxr

  // 只有当satp.mode为8且当前模式低于M模式时，才启用虚拟内存
  val ivm_enabled = (satp.mode === 8.U) && (imode < ModeM)
  val dvm_enabled = (satp.mode === 8.U) && (dmode < ModeM)

  val itlb  = RegInit(0.U.asTypeOf(tlbBundle))
  val dtlb  = RegInit(0.U.asTypeOf(tlbBundle))
  val tlbl2 = RegInit(VecInit(Seq.fill(cpuConfig.tlbEntries)(0.U.asTypeOf(tlbBundle))))

  val ivpn = io.icache.vaddr(VADDR_WID - 1, pageOffsetLen)
  val dvpn = io.dcache.vaddr(VADDR_WID - 1, pageOffsetLen)

  // 当(VPN一致)且(ASID一致或PTE.G为1时)且(PTE.V为1)时，TLB命中
  val itlbl1_hit = vpnEq(itlb.rmask, ivpn, itlb.vpn) &&
    (itlb.asid === satp.asid || itlb.flag.g) &&
    itlb.flag.v
  val dtlbl1_hit = vpnEq(dtlb.rmask, dvpn, dtlb.vpn) &&
    (dtlb.asid === satp.asid || dtlb.flag.g) &&
    dtlb.flag.v

  val il2_hit_vec = VecInit(
    tlbl2.map(tlb =>
      vpnEq(tlb.rmask, ivpn, tlb.vpn) &&
        (tlb.asid === satp.asid || tlb.flag.g) &&
        tlb.flag.v
    )
  )
  val dl2_hit_vec = VecInit(
    tlbl2.map(tlb =>
      vpnEq(tlb.rmask, dvpn, tlb.vpn) &&
        (tlb.asid === satp.asid || tlb.flag.g) &&
        tlb.flag.v
    )
  )

  val search_l1 :: search_l2 :: search_pte :: search_fault :: Nil = Enum(4)
  val immu_state                                                  = RegInit(search_l1)
  val dmmu_state                                                  = RegInit(search_l1)

  // 使用随机的方法替换TLB条目
  val replace_index = new Counter(cpuConfig.tlbEntries)

  val ipage_fault   = RegInit(false.B)
  val dpage_fault   = RegInit(false.B)
  val iaccess_fault = RegInit(false.B)
  val daccess_fault = RegInit(false.B)

  // ptw的请求标志，0位为指令tlb请求，1位为数据tlb请求
  val req_ptw = WireInit(VecInit(Seq.fill(2)(false.B)))

  val ar_sel_lock = RegInit(false.B)
  val ar_sel_val  = RegInit(false.B)
  // 我们默认优先发送数据tlb的请求
  val choose_icache = Mux(ar_sel_lock, ar_sel_val, req_ptw(0) && !req_ptw(1))

  when(io.dcache.ptw.vpn.valid) {
    when(io.dcache.ptw.vpn.ready) {
      ar_sel_lock := false.B
    }.otherwise {
      ar_sel_lock := true.B
      ar_sel_val  := choose_icache
    }
  }

  io.icache.hit          := false.B
  io.dcache.hit          := false.B
  io.icache.access_fault := iaccess_fault
  io.dcache.access_fault := daccess_fault
  io.icache.page_fault   := ipage_fault
  io.dcache.page_fault   := dpage_fault

  // 将ptw模块集成到dcache中，ptw通过dcache的axi进行内存访问
  io.dcache.ptw.vpn.valid   := Mux(choose_icache, req_ptw(0), req_ptw(1))
  io.dcache.ptw.access_type := Mux(choose_icache, AccessType.fetch, io.dcache.access_type)
  io.dcache.ptw.vpn.bits    := Mux(choose_icache, ivpn, dvpn)
  io.dcache.ptw.pte.ready   := true.B // 恒为true
  io.dcache.csr <> io.csr

  def imodeCheck(): Unit = {
    switch(imode) {
      is(ModeS) {
        when(itlb.flag.u && sum === 0.U) {
          ipage_fault := true.B
          immu_state  := search_fault
        }.otherwise {
          io.icache.hit := true.B
        }
      }
      is(ModeU) {
        when(!itlb.flag.u) {
          ipage_fault := true.B
          immu_state  := search_fault
        }.otherwise {
          io.icache.hit := true.B
        }
      }
    }
  }

  def dmodeCheck(): Unit = {
    switch(dmode) {
      is(ModeS) {
        when(dtlb.flag.u && sum === 0.U) {
          dpage_fault := true.B
          dmmu_state  := search_fault
        }.otherwise {
          io.dcache.hit := true.B
        }
      }
      is(ModeU) {
        when(!dtlb.flag.u) {
          dpage_fault := true.B
          dmmu_state  := search_fault
        }.otherwise {
          io.dcache.hit := true.B
        }
      }
    }
  }

  // ---------------------------------------------------
  // ----------------- 指令虚实地址转换 -----------------
  // ---------------------------------------------------
  switch(immu_state) {
    is(search_l1) {
      when(io.icache.en) {
        // 在icache实现访问tlb的pma和pmp权限检查
        ipage_fault   := false.B
        iaccess_fault := false.B
        when(!ivm_enabled) {
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
          }.otherwise {
            imodeCheck()
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
        when(choose_icache && io.dcache.ptw.vpn.ready) {
          immu_state := search_pte
        }
      }
    }
    is(search_pte) {
      req_ptw(0) := true.B
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
          replace_entry.rmask        := io.dcache.ptw.pte.bits.rmask
          tlbl2(replace_index.value) := replace_entry
          itlb                       := replace_entry
          replace_index.inc()
          immu_state := search_l1
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
  // ----------------- 数据虚实地址转换 -----------------
  // ---------------------------------------------------
  switch(dmmu_state) {
    is(search_l1) {
      when(io.dcache.en) {
        // 在dcache实现访问tlb的pma和pmp权限检查
        dpage_fault   := false.B
        daccess_fault := false.B
        when(!dvm_enabled) {
          io.dcache.hit := true.B
        }.elsewhen(dtlbl1_hit) {
          // 在这里进行取指需要的所有的权限检查
          // 如果是load
          // 0. MXR位检查，分类0和1的情况
          // 1. M模式，不可能到这里，因为vm_enabled为false
          // 2. S模式，如果U位为1，需要检查SUM
          // 3. U模式，必须保证U位为1
          io.dcache.hit := false.B // 只有权限检查通过后可以置为true
          switch(io.dcache.access_type) {
            is(AccessType.load) {
              when(mxr) {
                when(!dtlb.flag.r && !dtlb.flag.x) {
                  dpage_fault := true.B
                  dmmu_state  := search_fault
                }.otherwise {
                  dmodeCheck()
                }
              }.otherwise {
                when(!dtlb.flag.r) {
                  dpage_fault := true.B
                  dmmu_state  := search_fault
                }.otherwise {
                  dmodeCheck()
                }
              }
            }
            is(AccessType.store) {
              when(!dtlb.flag.d) {
                dpage_fault := true.B
                dmmu_state  := search_fault
              }.otherwise {
                when(!dtlb.flag.w) {
                  dpage_fault := true.B
                  dmmu_state  := search_fault
                }.otherwise {
                  dmodeCheck()
                }
              }
            }
          }
        }.otherwise {
          dmmu_state := search_l2
        }
      }
    }
    is(search_l2) {
      when(dl2_hit_vec.asUInt.orR) {
        dmmu_state := search_l1
        dtlb       := tlbl2(PriorityEncoder(dl2_hit_vec))
      }.otherwise {
        req_ptw(1) := true.B
        when(!choose_icache && io.dcache.ptw.vpn.ready) {
          dmmu_state := search_pte
        }
      }
    }
    is(search_pte) {
      req_ptw(1) := true.B
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
          replace_entry.vpn          := dvpn
          replace_entry.asid         := satp.asid
          replace_entry.flag         := io.dcache.ptw.pte.bits.entry.flag
          replace_entry.ppn          := io.dcache.ptw.pte.bits.entry.ppn
          replace_entry.rmask        := io.dcache.ptw.pte.bits.rmask
          tlbl2(replace_index.value) := replace_entry
          dtlb                       := replace_entry
          replace_index.inc()
          dmmu_state := search_l1
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

  // vpn
  val src1 = io.sfence_vma.src_info.src1_data(vpnLen - 1, pageOffsetLen)
  // asid
  val src2 = io.sfence_vma.src_info.src2_data(asidLen - 1, 0)
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
      when(vpnEq(itlb.rmask, src1, itlb.vpn)) {
        itlb.flag.v := false.B
      }
      when(vpnEq(dtlb.rmask, src1, dtlb.vpn)) {
        dtlb.flag.v := false.B
      }
      for (i <- 0 until cpuConfig.tlbEntries) {
        when(vpnEq(tlbl2(i).rmask, src1, tlbl2(i).vpn)) {
          tlbl2(i).flag.v := false.B
        }
      }
    }.elsewhen(src1.orR && src2.orR) {
      // 将asid一致的且vpn一致的tlb的有效位置为0，g为1的除外
      when(itlb.asid === src2 && vpnEq(itlb.rmask, src1, itlb.vpn) && !itlb.flag.g) {
        itlb.flag.v := false.B
      }
      when(dtlb.asid === src2 && vpnEq(dtlb.rmask, src1, dtlb.vpn) && !dtlb.flag.g) {
        dtlb.flag.v := false.B
      }
      for (i <- 0 until cpuConfig.tlbEntries) {
        when(tlbl2(i).asid === src2 && vpnEq(tlbl2(i).rmask, src1, tlbl2(i).vpn) && !tlbl2(i).flag.g) {
          tlbl2(i).flag.v := false.B
        }
      }
    }
  }

  val imasktag = maskTag(itlb.rmask, itlb.ppn, ivpn)
  val dmasktag = maskTag(dtlb.rmask, dtlb.ppn, dvpn)

  io.icache.uncached := AddressSpace.isMMIO(io.icache.vaddr)
  io.icache.ptag     := Mux(ivm_enabled, imasktag, ivpn)
  io.icache.paddr    := Cat(io.icache.ptag, io.icache.vaddr(pageOffsetLen - 1, 0))

  io.dcache.uncached := AddressSpace.isMMIO(io.dcache.vaddr)
  io.dcache.ptag     := Mux(dvm_enabled, dmasktag, dvpn)
  io.dcache.paddr    := Cat(io.dcache.ptag, io.dcache.vaddr(pageOffsetLen - 1, 0))

}
