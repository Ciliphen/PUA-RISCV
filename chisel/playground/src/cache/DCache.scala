package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.CacheConfig
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._
import icache.mmu.AccessType

/*
  整个宽度为PADDR_WID的地址
  ==========================================================
  |        tag         |  index |         offset           |
  |                    |        | bank index | bank offset |
  ==========================================================

  nway 组，nindex 行
  ==============================================================
  | valid | dirty | tag |  bank 0 | bank 1  |  ...    | bank n |
  |   1   |   1   |     |         |         |         |        |
  ==============================================================
  |                bank               |
  | data 0 | data 1 |   ...  | data n |
  |  XLEN  |  XLEN  |   ...  |  XLEN  |
  =====================================

  本 CPU 的实现如下：
    每个bank分为多个dataBlocks，每个dataBlocks的宽度为AXI_DATA_WID，这样能方便的和AXI总线进行交互
    RV64实现中AXI_DATA_WID为64，所以每个dataBlocks可以存储1个数据
    为了简化设计，目前*一个bank中只有一个dataBlocks*，即每个bank中只能存储一个数据
      这样的话dataBlocks可以被简化掉，直接用bank代替
      //TODO：解决AXI_DATA_WID小于XLEN的情况

  ==============================================================
  | valid | dirty | tag |  bank 0 | bank 1  |  ...    | bank n |
  |   1   |   1   |     |         |         |         |        |
  ==============================================================
  |      bank       |
  |   dataBlocks    |
  |     data 0      |
  |       64        |
  ===================
 */

class WriteBufferUnit extends Bundle {
  val data = UInt(XLEN.W)
  val addr = UInt(XLEN.W)
  val strb = UInt(AXI_STRB_WID.W)
  val size = UInt(AXI_SIZE_WID.W)
}

class DCache(cacheConfig: CacheConfig)(implicit cpuConfig: CpuConfig) extends Module with Sv39Const with HasCSRConst {
  val nway            = cacheConfig.nway
  val nindex          = cacheConfig.nindex
  val nbank           = cacheConfig.nbank
  val instFetchNum    = cpuConfig.instFetchNum
  val bankOffsetWidth = cacheConfig.bankOffsetWidth
  val bankIndexWidth  = cacheConfig.offsetWidth - bankOffsetWidth
  val bytesPerBank    = cacheConfig.bytesPerBank
  val tagWidth        = cacheConfig.tagWidth
  val indexWidth      = cacheConfig.indexWidth
  val offsetWidth     = cacheConfig.offsetWidth
  val bitsPerBank     = cacheConfig.bitsPerBank
  val writeFifoDepth  = 4

  // 每个bank中存AXI_DATA_WID位的数据
  // TODO:目前的实现只保证了AXI_DATA_WID为XLEN的情况下的正确性
  require(AXI_DATA_WID == XLEN, "AXI_DATA_WID should be greater than XLEN")

  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_DCache())
    val axi = new DCache_AXIInterface()
  })

  // dcache的状态机
  val s_idle :: s_uncached :: s_fence :: s_replace :: s_wait :: s_tlb_refill :: Nil = Enum(6)
  val state                                                                         = RegInit(s_idle)

  // ptw的状态机
  val s_handshake :: s_send :: s_receive :: s_check :: s_set :: Nil = Enum(5)
  val ptw_state                                                     = RegInit(s_handshake)

  io.cpu.tlb.ptw.vpn.ready := false.B

  // ==========================================================
  // |        tag         |  index |         offset           |
  // |                    |        | bank index | bank offset |
  // ==========================================================

  // exe级的index，用于访问第i行的数据
  val exe_index = io.cpu.exe_addr(indexWidth + offsetWidth - 1, offsetWidth)
  // mem级的bank的index，用于访问第i个bank的数据
  val bank_index = io.cpu.addr(bankIndexWidth + bankOffsetWidth - 1, bankOffsetWidth)

  // // 一个bank行内存了一个数据，所以bank_offset恒为0
  // val bank_offset =
  //   if (bankOffsetWidth > log2Ceil(XLEN / 8))
  //     io.cpu.addr(bankOffsetWidth - 1, log2Ceil(XLEN / 8)) // 保证地址对齐
  //   else
  //     0.U

  // axi信号中size的宽度，对于cached段，size为3位
  val cached_size = log2Ceil(AXI_DATA_WID / 8)
  val cached_len  = (nbank - 1)

  // * valid dirty * //
  // 每行有一个有效位和一个脏位
  val valid = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nway)(false.B))))) // FIXME：nway放前面会导致栈溢出错误
  val dirty = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nway)(false.B)))))
  val lru   = RegInit(VecInit(Seq.fill(nindex)(false.B))) // TODO:支持更多路数，目前只支持2路

  // 对于2路组相连的cache: 0:第0路脏位为真，1:第1路脏位为真，2:两路都为假
  val dirty_table = Wire(Vec(nindex, UInt(log2Ceil(nway + 1).W)))
  // 用于指示哪个行的脏位为真
  val dirty_index = Wire(UInt(indexWidth.W))
  // 用于指示哪个路的脏位为真
  val dirty_way = dirty_table(dirty_index)

  for (i <- 0 until nindex) {
    val dirtyMappings = (0 until nway).map { way =>
      dirty(i)(way) -> way.U
    }
    dirty_table(i) := MuxCase(
      nway.U,
      dirtyMappings
    )
  }

  dirty_index := PriorityEncoder(dirty_table.map(w => w =/= nway.U))

  // 表示进入fence的写回状态
  val fence = RegInit(false.B)
  // 表示准备好了fence的写回数据，因为bank读数据要两拍
  val fence_data_ready = RegInit(false.B)

  // 对于uncached段使用writeFifo进行写回
  val writeFifo          = Module(new Queue(new WriteBufferUnit(), writeFifoDepth))
  val writeFifo_axi_busy = RegInit(false.B)
  val writeFifo_busy     = writeFifo.io.deq.valid // || writeFifo_axi_busy 应该不需要这个判断

  writeFifo.io.enq.valid := false.B
  writeFifo.io.enq.bits  := 0.U.asTypeOf(new WriteBufferUnit())
  writeFifo.io.deq.ready := false.B

  // * victim cache * //
  val burst = RegInit(0.U.asTypeOf(new Bundle {
    val wstrb = Vec(nway, UInt(nbank.W)) // 用于控制写回哪个bank
  }))

  // 用于解决在replace时读写时序不一致的问题
  val bank_windex      = RegInit(0.U((offsetWidth - log2Ceil(XLEN / 8)).W))
  val bank_replication = RegInit(VecInit(Seq.fill(nbank)(0.U(XLEN.W))))

  // 是否使用exe的地址进行提前访存
  val use_next_addr = (state === s_idle) || (state === s_wait)
  val do_replace    = RegInit(false.B)
  // replace index 表示行的索引
  val replace_index = io.cpu.addr(indexWidth + offsetWidth - 1, offsetWidth)
  val replace_wstrb = Wire(Vec(nbank, Vec(nway, UInt(AXI_STRB_WID.W))))
  val replace_wdata = Mux(state === s_replace, io.axi.r.bits.data, io.cpu.wdata)

  val replace_way = lru(replace_index)

  val replace_dirty = dirty(replace_index)(replace_way)

  val tag_rindex = Mux(use_next_addr, exe_index, replace_index)
  val tag_wstrb  = RegInit(VecInit(Seq.fill(nway)(false.B)))
  val tag_wdata  = RegInit(0.U(tagWidth.W))

  val data = Wire(Vec(nbank, Vec(nway, UInt(XLEN.W))))
  // 使用寄存器类型才能防止idle时tag出现无法hit的错误
  val tag = RegInit(VecInit(Seq.fill(nway)(0.U(tagWidth.W))))

  val tag_compare_valid = Wire(Vec(nway, Bool()))
  val cache_hit         = tag_compare_valid.contains(true.B)

  val mmio_read_stall  = io.cpu.tlb.uncached && !io.cpu.wen.orR
  val mmio_write_stall = io.cpu.tlb.uncached && io.cpu.wen.orR && !writeFifo.io.enq.ready
  val cached_stall     = !io.cpu.tlb.uncached && !cache_hit

  val select_way = tag_compare_valid(1)

  val dcache_stall = Mux(
    state === s_idle,
    Mux(
      io.cpu.en,
      (cached_stall || mmio_read_stall || mmio_write_stall || !io.cpu.tlb.hit),
      io.cpu.fence_i || fence
    ),
    state =/= s_wait
  )
  io.cpu.dcache_ready := !dcache_stall

  val saved_rdata = RegInit(0.U(XLEN.W))

  io.cpu.rdata := Mux(state === s_wait, saved_rdata, data(bank_index)(select_way))

  io.cpu.tlb.addr        := io.cpu.addr
  io.cpu.tlb.access_type := Mux(io.cpu.en && io.cpu.wen.orR, AccessType.store, AccessType.load)
  io.cpu.tlb.en          := io.cpu.en

  val bank_raddr = Mux(state === s_fence, dirty_index, Mux(use_next_addr, exe_index, replace_index))
  val tag_raddr  = Mux(state === s_fence, dirty_index, tag_rindex)

  val wstrb = Wire(Vec(nindex, (Vec(nway, UInt(AXI_STRB_WID.W)))))
  wstrb                         := 0.U.asTypeOf(wstrb)
  wstrb(bank_index)(select_way) := io.cpu.wstrb

  // bank tagv ram
  val tagRam = Seq.fill(nway)(Module(new LUTRam(nindex, tagWidth)))
  for { i <- 0 until nway } {
    val bank = Seq.fill(nbank)(Module(new SimpleDualPortRam(nindex, AXI_DATA_WID, byteAddressable = true)))
    for { j <- 0 until nbank } {
      bank(j).io.ren   := true.B
      bank(j).io.raddr := bank_raddr
      data(j)(i)       := bank(j).io.rdata

      bank(j).io.wen   := replace_wstrb(j)(i).orR
      bank(j).io.waddr := replace_index
      bank(j).io.wdata := replace_wdata
      bank(j).io.wstrb := replace_wstrb(j)(i)

      tagRam(i).io.raddr := tag_raddr
      tag(i)             := tagRam(i).io.rdata

      tagRam(i).io.wen   := tag_wstrb(i)
      tagRam(i).io.waddr := replace_index
      tagRam(i).io.wdata := tag_wdata

      tag_compare_valid(i) :=
        tag(i) === io.cpu.tlb.ptag && // tag相同
        valid(replace_index)(i) && // cache行有效位为真
        io.cpu.tlb.hit // 页表有效

      replace_wstrb(j)(i) := Mux(
        tag_compare_valid(i) && io.cpu.en && io.cpu.wen.orR && !io.cpu.tlb.uncached && state === s_idle,
        wstrb(j)(i),
        Fill(AXI_STRB_WID, burst.wstrb(i)(j))
      )
    }
  }

  val ar      = RegInit(0.U.asTypeOf(new AR()))
  val arvalid = RegInit(false.B)
  io.axi.ar.bits <> ar
  io.axi.ar.valid := arvalid
  val rready = RegInit(false.B)
  io.axi.r.ready := rready
  val aw      = RegInit(0.U.asTypeOf(new AW()))
  val awvalid = RegInit(false.B)
  io.axi.aw.bits <> aw
  io.axi.aw.valid := awvalid
  val w      = RegInit(0.U.asTypeOf(new W()))
  val wvalid = RegInit(false.B)
  io.axi.w.bits <> w
  io.axi.w.bits.last := w.last && wvalid
  io.axi.w.valid     := wvalid

  io.axi.b.ready := true.B

  val access_fault = RegInit(false.B)
  val page_fault   = RegInit(false.B)
  val addr_err     = io.cpu.addr(XLEN - 1, VADDR_WID).orR

  io.cpu.access_fault := access_fault
  io.cpu.page_fault   := page_fault

  // write buffer
  when(writeFifo_axi_busy) {
    when(io.axi.aw.fire) {
      awvalid := false.B
    }
    when(io.axi.w.fire) {
      wvalid := false.B
      w.last := false.B
    }
    when(io.axi.b.fire) {
      writeFifo_axi_busy := false.B
    }
  }.elsewhen(writeFifo.io.deq.valid) {
    writeFifo.io.deq.ready := writeFifo.io.deq.valid
    when(writeFifo.io.deq.fire) {
      aw.addr := writeFifo.io.deq.bits.addr
      aw.size := writeFifo.io.deq.bits.size
      w.data  := writeFifo.io.deq.bits.data
      w.strb  := writeFifo.io.deq.bits.strb
    }
    aw.len             := 0.U
    awvalid            := true.B
    w.last             := true.B
    wvalid             := true.B
    writeFifo_axi_busy := true.B
  }

  switch(state) {
    is(s_idle) {
      access_fault := false.B // 在idle时清除access_fault
      page_fault   := false.B // 在idle时清除page_fault
      when(io.cpu.en) {
        when(addr_err) {
          access_fault := true.B
        }.elsewhen(!io.cpu.tlb.hit) {
          state := s_tlb_refill
        }.elsewhen(io.cpu.tlb.uncached) {
          when(io.cpu.wen.orR) {
            when(writeFifo.io.enq.ready) {
              writeFifo.io.enq.valid     := true.B
              writeFifo.io.enq.bits.addr := io.cpu.tlb.paddr
              writeFifo.io.enq.bits.size := io.cpu.rlen
              writeFifo.io.enq.bits.strb := io.cpu.wstrb
              writeFifo.io.enq.bits.data := io.cpu.wdata

              state := s_wait
            }
          }.elsewhen(!writeFifo_busy) {
            ar.addr := io.cpu.tlb.paddr
            ar.len  := 0.U
            ar.size := io.cpu.rlen
            arvalid := true.B
            state   := s_uncached
            rready  := true.B
          } // when store buffer busy, read will stop at s_idle but stall pipeline.
        }.otherwise {
          when(!cache_hit) {
            state                    := s_replace
            bank_windex              := 0.U
            burst.wstrb(replace_way) := 1.U // 先写入第一块bank
            when(replace_dirty) {
              // cache行的脏位为真时需要写回，备份一下cache行，便于处理读写时序问题
              (0 until nbank).map(i => bank_replication(i) := data(i)(select_way))
            }
          }.otherwise {
            when(io.cpu.dcache_ready) {
              // update lru and mark dirty
              replace_way := ~select_way
              when(io.cpu.wen.orR) {
                dirty(replace_index)(select_way) := true.B
              }
              when(!io.cpu.complete_single_request) {
                saved_rdata := data(bank_index)(select_way)
                state       := s_wait
              }
            }
          }
        }
      }.otherwise {
        io.cpu.tlb.ptw.vpn.ready := ptw_state === s_handshake
        when(io.cpu.fence_i) {
          // fence.i 需要将所有脏位为true的行写回
          when(dirty.asUInt.orR) {
            when(!writeFifo_busy) {
              state            := s_fence
              fence_data_ready := false.B // bank读数据要两拍
            }
          }.otherwise {
            // 当所有脏位为fault时，fence.i可以直接完成
            state := s_wait
          }
        }
      }
    }
    is(s_uncached) {
      when(arvalid && io.axi.ar.ready) {
        arvalid := false.B
      }
      when(io.axi.r.fire) {
        rready       := false.B
        saved_rdata  := io.axi.r.bits.data
        access_fault := io.axi.r.bits.resp =/= RESP_OKEY.U
        state        := s_wait
      }
    }
    is(s_fence) {
      when(fence) {
        when(io.axi.aw.fire) {
          awvalid := false.B
        }
        when(io.axi.w.fire) {
          when(w.last) {
            wvalid := false.B
          }.otherwise {
            bank_windex := bank_windex + 1.U
            w.data      := data(bank_windex + 1.U)(dirty_way)
            when(bank_windex + 1.U === (cached_len).U) {
              w.last := true.B
            }
          }
        }
        when(io.axi.b.valid) {
          // TODO: 增加此处的acc_err错误处理
          // acc_err := io.axi.b.bits.resp =/= RESP_OKEY.U
          dirty(dirty_index)(dirty_way) := false.B // 写回完成，清除脏位
          fence_data_ready              := false.B
          fence                         := false.B
        }
      }.elsewhen(dirty.asUInt.orR) {
        when(fence_data_ready) {
          // for axi write
          aw.addr := Cat(
            Mux(dirty_way === 0.U, tagRam(0).io.rdata, tagRam(1).io.rdata),
            dirty_index,
            0.U(offsetWidth.W)
          )
          aw.len      := cached_len.U
          aw.size     := cached_size.U
          awvalid     := true.B
          w.data      := data(0)(dirty_way) // 从第零块bank开始写回
          w.strb      := ~0.U(AXI_STRB_WID.W)
          w.last      := false.B
          wvalid      := true.B
          bank_windex := 0.U
          fence       := true.B
        }.otherwise {
          fence_data_ready := true.B
        }
      }.otherwise {
        state := s_wait
      }
    }
    is(s_replace) {
      // 防止和写队列冲突
      when(!writeFifo_busy) {
        when(do_replace) {
          when(replace_dirty) {
            when(io.axi.aw.fire) {
              awvalid := false.B
            }
            when(io.axi.w.fire) {
              when(w.last) {
                wvalid := false.B
              }.otherwise {
                bank_windex := bank_windex + 1.U
                w.data      := bank_replication(bank_windex + 1.U)
                when(bank_windex + 1.U === (cached_len).U) {
                  w.last := true.B
                }
              }
            }
            when(io.axi.b.valid) {
              // TODO: 增加此处的acc_err错误处理
              // acc_err := io.axi.b.bits.resp =/= RESP_OKEY.U
              replace_dirty := false.B // 写回完成，清除脏位
            }
          } //上面都是写回部分的代码
          when(io.axi.ar.fire) {
            tag_wstrb(replace_way) := false.B
            arvalid                := false.B
          }
          when(io.axi.r.fire) {
            when(io.axi.r.bits.last) {
              rready                   := false.B
              burst.wstrb(replace_way) := 0.U
            }.otherwise {
              burst.wstrb(replace_way) := burst.wstrb(replace_way) << 1
            }
          }
          when(
            (!replace_dirty || io.axi.b.valid) && // 不需要替换或写回完成
              ((io.axi.r.valid && io.axi.r.bits.last) || !rready) // 读取完成
          ) {
            valid(replace_index)(replace_way) := true.B
            do_replace                        := false.B
            state                             := s_idle
          }
        }.otherwise {
          // 这里相当于增加了一拍，用于发射读写控制信号
          do_replace := true.B

          // for ar axi
          ar.addr                  := Cat(io.cpu.tlb.paddr(PADDR_WID - 1, offsetWidth), 0.U(offsetWidth.W))
          ar.len                   := cached_len.U
          ar.size                  := cached_size.U // 8 字节
          arvalid                  := true.B
          rready                   := true.B
          burst.wstrb(replace_way) := 1.U // 先写入第一块bank
          tag_wstrb(replace_way)   := true.B
          tag_wdata                := io.cpu.tlb.ptag
          when(replace_dirty) {
            aw.addr     := Cat(tag(replace_way), replace_index, 0.U(offsetWidth.W))
            aw.len      := cached_len.U
            aw.size     := cached_size.U
            awvalid     := true.B
            w.data      := data(0)(replace_way)
            w.strb      := ~0.U(AXI_STRB_WID.W)
            w.last      := false.B
            wvalid      := true.B
            bank_windex := 0.U
          }
        }
      }
    }
    is(s_wait) {
      // 等待流水线的allow_to_go信号，防止多次发出读、写请求
      when(io.cpu.complete_single_request) {
        state := s_idle
      }
    }
    is(s_tlb_refill) {
      io.cpu.tlb.ptw.vpn.ready := ptw_state === s_handshake
      when(io.cpu.tlb.access_fault) {
        access_fault := true.B
        state        := s_wait
      }.elsewhen(io.cpu.tlb.page_fault) {
        page_fault := true.B
        state      := s_wait
      }.otherwise {
        when(io.cpu.tlb.hit) {
          state := s_idle
        }
      }
    }
  }

  // ==========================================================
  // 实现页表访问，回填tlb
  val satp        = io.cpu.tlb.csr.satp.asTypeOf(satpBundle)
  val mstatus     = io.cpu.tlb.csr.mstatus.asTypeOf(new Mstatus)
  val mode        = Mux(io.cpu.tlb.access_type === AccessType.fetch, io.cpu.tlb.csr.imode, io.cpu.tlb.csr.dmode)
  val sum         = mstatus.sum
  val mxr         = mstatus.mxr
  val vpn         = io.cpu.tlb.ptw.vpn.bits.asTypeOf(vpnBundle)
  val access_type = io.cpu.tlb.ptw.access_type
  val ppn         = RegInit(0.U(ppnLen.W))
  val vpn_index   = RegInit(0.U(log2Up(level).W)) // 页表访问的层级
  val pte         = RegInit(0.U.asTypeOf(pteBundle)) // 页表项

  io.cpu.tlb.ptw.pte.valid             := false.B
  io.cpu.tlb.ptw.pte.bits              := DontCare
  io.cpu.tlb.ptw.pte.bits.access_fault := false.B
  io.cpu.tlb.ptw.pte.bits.page_fault   := false.B
  io.cpu.tlb.complete_single_request   := io.cpu.complete_single_request
  require(AXI_DATA_WID == XLEN) // 目前只考虑了AXI_DATA_WID == XLEN的情况

  def raisePageFault(): Unit = {
    io.cpu.tlb.ptw.pte.valid           := true.B
    io.cpu.tlb.ptw.pte.bits.page_fault := true.B
    ptw_state                          := s_handshake
  }

  def modeCheck(): Unit = {
    switch(mode) {
      is(ModeS) {
        when(pte.flag.u && !sum) {
          raisePageFault()
        }.otherwise {
          ptw_state := s_set
        }
      }
      is(ModeU) {
        when(!pte.flag.u) {
          raisePageFault()
        }.otherwise {
          ptw_state := s_set
        }
      }
    }
  }

  switch(ptw_state) {
    is(s_handshake) {
      // 页表访问虚地址握手
      when(io.cpu.tlb.ptw.vpn.valid) {
        vpn_index := (level - 1).U
        ppn       := satp.ppn
        ptw_state := s_send
      }
    }
    is(s_send) {
      arvalid := true.B
      val vpnn = Mux1H(
        Seq(
          (vpn_index === 0.U) -> vpn.vpn0,
          (vpn_index === 1.U) -> vpn.vpn1,
          (vpn_index === 2.U) -> vpn.vpn2
        )
      )
      ar.addr   := paddrApply(ppn, vpnn)
      ar.size   := log2Ceil(AXI_DATA_WID / 8).U // 一个pte的大小是8字节
      ar.len    := 0.U // 读一拍即可
      ptw_state := s_receive
      rready    := true.B
    }
    is(s_receive) {
      when(io.axi.ar.fire) {
        arvalid := false.B
      }
      when(io.axi.r.fire) {
        rready := false.B
        val pte_temp = io.axi.r.bits.data.asTypeOf(pteBundle)
        when(!pte_temp.flag.v || !pte_temp.flag.r && pte_temp.flag.w) {
          raisePageFault()
        }.otherwise {
          when(pte_temp.flag.r || pte_temp.flag.x) {
            // 找到了叶子页
            pte       := pte_temp
            ptw_state := s_check
          }.otherwise {
            // 该pte指向下一个页表
            vpn_index := vpn_index - 1.U
            when(vpn_index - 1.U < 0.U) {
              raisePageFault()
            }.otherwise {
              ppn       := pte_temp.ppn
              ptw_state := s_send
            }
          }
        }
      }
    }
    is(s_check) {
      // 检查权限
      switch(access_type) {
        is(AccessType.load) {
          when(mxr) {
            when(!pte.flag.r && !pte.flag.x) {
              raisePageFault()
            }.otherwise {
              modeCheck()
            }
          }.otherwise {
            when(!pte.flag.r) {
              raisePageFault()
            }.otherwise {
              modeCheck()
            }
          }
        }
        is(AccessType.store) {
          when(!pte.flag.w) {
            raisePageFault()
          }.otherwise {
            modeCheck()
          }
        }
        is(AccessType.fetch) {
          when(!pte.flag.x) {
            raisePageFault()
          }.otherwise {
            modeCheck()
          }
        }
      }
    }
    is(s_set) {
      when(
        vpn_index > 0.U && (
          vpn_index === 1.U && pte.ppn(0) ||
            vpn_index === 2.U && pte.ppn(1, 0).orR
        )
      ) {
        raisePageFault()
      }.elsewhen(!pte.flag.a || access_type === AccessType.store && !pte.flag.d) {
        raisePageFault() // 使用软件的方式设置脏位以及访问位
      }.otherwise {
        // 翻译成功
        io.cpu.tlb.ptw.pte.valid      := true.B
        io.cpu.tlb.ptw.pte.bits.addr  := ar.addr
        io.cpu.tlb.ptw.pte.bits.entry := pte
        val ppn_set = Wire(ppnBundle)
        when(vpn_index === 2.U) {
          ppn_set.ppn2 := pte.ppn.asTypeOf(ppnBundle).ppn2
          ppn_set.ppn1 := vpn.vpn1
          ppn_set.ppn0 := vpn.vpn0
        }.elsewhen(vpn_index === 1.U) {
          ppn_set.ppn2 := pte.ppn.asTypeOf(ppnBundle).ppn2
          ppn_set.ppn1 := pte.ppn.asTypeOf(ppnBundle).ppn1
          ppn_set.ppn0 := vpn.vpn0
        }.otherwise {
          ppn_set := pte.ppn.asTypeOf(ppnBundle)
        }
        io.cpu.tlb.ptw.pte.bits.entry.ppn := ppn_set.asUInt

        ptw_state := s_handshake
      }
    }
  }

  println("----------------------------------------")
  println("DCache: ")
  println("nindex: " + nindex)
  println("nbank: " + nbank)
  println("bitsPerBank: " + bitsPerBank)
  println("bankOffsetWidth: " + bankOffsetWidth)
  println("bankIndexWidth: " + bankIndexWidth)
  println("tagWidth: " + tagWidth)
  println("indexWidth: " + indexWidth)
  println("offsetWidth: " + offsetWidth)
  println("----------------------------------------")
}
