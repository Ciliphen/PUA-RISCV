package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.CacheConfig
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

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
    为了简化设计，目前一个bank中只有一个dataBlocks，即每个bank中只能存储一个数据
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
  val addr = UInt(DATA_ADDR_WID.W)
  val strb = UInt(AXI_STRB_WID.W)
  val size = UInt(AXI_SIZE_WID.W)
}

class DCache(cacheConfig: CacheConfig)(implicit config: CpuConfig) extends Module {
  val nway            = cacheConfig.nway
  val nindex          = cacheConfig.nindex
  val nbank           = cacheConfig.nbank
  val instFetchNum    = config.instFetchNum
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

  // * fsm * //
  val s_idle :: s_uncached :: s_writeback :: s_replace :: s_wait :: Nil = Enum(5)
  val state                                                             = RegInit(s_idle)

  // ==========================================================
  // |        tag         |  index |         offset           |
  // |                    |        | bank index | bank offset |
  // ==========================================================

  val index      = io.cpu.addr(indexWidth + offsetWidth - 1, offsetWidth)
  val bank_addr  = io.cpu.addr(indexWidth + offsetWidth - 1, log2Ceil(XLEN / 8)) // TODO：目前临时使用一下
  val bank_index = io.cpu.addr(bankIndexWidth + bankOffsetWidth - 1, bankOffsetWidth)
  val bank_offset =
    if (bankOffsetWidth > log2Ceil(XLEN / 8))
      io.cpu.addr(bankOffsetWidth - 1, log2Ceil(XLEN / 8)) // 保证地址对齐
    else
      0.U

  val tlb_fill = RegInit(false.B)
  io.cpu.tlb.fill := tlb_fill

  // 每个bank中只有一个dataBlocks
  val dataBlocksPerBank = 1
  // axi信号中size的宽度，对于cached段，size为3位
  val cached_size = log2Ceil(AXI_DATA_WID / 8)
  val cached_len  = (nbank * dataBlocksPerBank - 1)

  // * valid dirty * //
  // 每行有一个有效位和一个脏位
  val valid = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nway)(false.B)))))
  val dirty = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nway)(false.B)))))
  val lru   = RegInit(VecInit(Seq.fill(nindex)(false.B))) // TODO:支持更多路数，目前只支持2路

  val writeFifo = Module(new Queue(new WriteBufferUnit(), writeFifoDepth))

  writeFifo.io.enq.valid := false.B
  writeFifo.io.enq.bits  := 0.U.asTypeOf(new WriteBufferUnit())
  writeFifo.io.deq.ready := false.B

  val axi_cnt        = Counter(cached_len + 1)
  val read_ready_cnt = RegInit(0.U((offsetWidth - log2Ceil(XLEN / 8)).W))

  // * victim cache * //
  val victim = RegInit(0.U.asTypeOf(new Bundle {
    val valid     = Bool()
    val index     = UInt(indexWidth.W)
    val waddr     = UInt((indexWidth + offsetWidth - log2Ceil(XLEN / 8) + 1).W)
    val wstrb     = Vec(nway, UInt(AXI_STRB_WID.W))
    val working   = Bool()
    val writeback = Bool()
  }))
  val victim_cnt  = Counter(cached_len + 1)
  val victim_addr = Cat(victim.index, victim_cnt.value)

  val fence_index = index
  val fence = RegInit(0.U.asTypeOf(new Bundle {
    val working = Bool()
  }))

  val read_buffer  = RegInit(VecInit(Seq.fill(16)(0.U(XLEN.W))))
  val ar_handshake = RegInit(false.B)
  val aw_handshake = RegInit(false.B)

  //
  val data_raddr    = Mux(victim.valid, victim_addr, bank_addr)
  val replace_wstrb = Wire(Vec(nway, UInt(AXI_STRB_WID.W)))
  val replace_waddr = Mux(victim.valid, victim.waddr, bank_addr)
  val replace_wdata = Mux(state === s_replace, io.axi.r.bits.data, io.cpu.wdata)

  val replace_way = lru(index)

  val tag_raddr = Mux(victim.valid, victim.index, index)
  val tag_wstrb = RegInit(VecInit(Seq.fill(nway)(false.B)))
  val tag_wdata = RegInit(0.U(tagWidth.W))

  val data = Wire(Vec(nway, UInt(XLEN.W)))
  val tag  = RegInit(VecInit(Seq.fill(nway)(0.U(tagWidth.W))))

  val tag_compare_valid = Wire(Vec(nway, Bool()))
  val cache_hit         = tag_compare_valid.contains(true.B)

  val mmio_read_stall  = io.cpu.tlb.uncached && !io.cpu.wen.orR
  val mmio_write_stall = io.cpu.tlb.uncached && io.cpu.wen.orR && !writeFifo.io.enq.ready
  val cached_stall     = !io.cpu.tlb.uncached && !cache_hit

  val select_way = tag_compare_valid(1)

  val dcache_stall = Mux(
    state === s_idle && !tlb_fill,
    Mux(io.cpu.en, (cached_stall || mmio_read_stall || mmio_write_stall || !io.cpu.tlb.translation_ok), io.cpu.fence),
    state =/= s_wait
  )
  io.cpu.dcache_ready := !dcache_stall

  val saved_rdata = RegInit(0.U(XLEN.W))

  // forward last stored data in data bram
  val last_waddr         = RegNext(replace_waddr)
  val last_wstrb         = RegInit(VecInit(Seq.fill(nway)(0.U(XLEN.W))))
  val last_wdata         = RegNext(replace_wdata)
  val cache_data_forward = Wire(Vec(nway, UInt(XLEN.W)))

  io.cpu.rdata := Mux(state === s_wait, saved_rdata, cache_data_forward(select_way))

  // bank tagv ram
  for { i <- 0 until nway } {
    val bank = Module(new SimpleDualPortRam(nindex * nbank, AXI_DATA_WID, byteAddressable = true))
    bank.io.ren   := true.B
    bank.io.raddr := data_raddr
    data(i)       := bank.io.rdata

    bank.io.wen   := replace_wstrb(i).orR
    bank.io.waddr := replace_waddr
    bank.io.wdata := replace_wdata
    bank.io.wstrb := replace_wstrb(i)

    val tagRam = Module(new LUTRam(nindex, tagWidth))
    tagRam.io.raddr := tag_raddr
    tag(i)          := tagRam.io.rdata

    tagRam.io.wen   := tag_wstrb(i)
    tagRam.io.waddr := victim.index
    tagRam.io.wdata := tag_wdata

    tag_compare_valid(i) := tag(i) === io.cpu.tlb.ptag && valid(index)(i) && io.cpu.tlb.translation_ok
    cache_data_forward(i) := Mux(
      last_waddr === bank_addr,
      ((last_wstrb(i) & last_wdata) | (data(i) & (~last_wstrb(i)))),
      data(i)
    )

    replace_wstrb(i) := Mux(
      tag_compare_valid(i) && io.cpu.en && io.cpu.wen.orR && !io.cpu.tlb.uncached && state === s_idle && !tlb_fill,
      io.cpu.wstrb,
      victim.wstrb(i)
    )

    last_wstrb(i) := Cat((AXI_STRB_WID - 1 to 0 by -1).map(j => Fill(8, replace_wstrb(i)(j))))
  }
  val writeFifo_axi_busy = RegInit(false.B)

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
  io.axi.w.valid := wvalid

  io.axi.b.ready := true.B

  val acc_err  = RegInit(false.B)
  val addr_err = io.cpu.addr(XLEN - 1, VADDR_WID).orR
  when(acc_err) {
    acc_err := false.B
  }
  io.cpu.acc_err := acc_err

  // write buffer
  when(writeFifo_axi_busy) { // To implement SC memory ordering, when store buffer busy, axi is unseable.
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
      when(tlb_fill) {
        tlb_fill := false.B
        when(!io.cpu.tlb.hit) {
          state := s_wait
        }
      }.elsewhen(io.cpu.en) {
        when(addr_err) {
          acc_err := true.B
        }.elsewhen(!io.cpu.tlb.translation_ok) {
          when(io.cpu.tlb.tlb1_ok) {
            state := s_wait
          }.otherwise {
            tlb_fill := true.B
          }
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
          }.elsewhen(!(writeFifo.io.deq.valid || writeFifo_axi_busy)) {
            ar.addr := Mux(io.cpu.rlen === 2.U, Cat(io.cpu.tlb.paddr(31, 2), 0.U(2.W)), io.cpu.tlb.paddr)
            ar.len  := 0.U
            ar.size := io.cpu.rlen
            arvalid := true.B
            state   := s_uncached
            rready  := true.B
          } // when store buffer busy, read will stop at s_idle but stall pipeline.
        }.otherwise {
          when(!cache_hit) {
            state := s_replace
            axi_cnt.reset()
            victim.index := index
            victim_cnt.reset()
            read_ready_cnt   := 0.U
            victim.waddr     := Cat(index, 0.U((offsetWidth - log2Ceil(XLEN / 8)).W))
            victim.valid     := true.B
            victim.writeback := dirty(index)(replace_way)
          }.otherwise {
            when(io.cpu.dcache_ready) {
              // update lru and mark dirty
              replace_way := ~select_way
              when(io.cpu.wen.orR) {
                dirty(index)(select_way) := true.B
              }
              when(!io.cpu.complete_single_request) {
                saved_rdata := cache_data_forward(select_way)
                state       := s_wait
              }
            }
          }
        }
      }.elsewhen(io.cpu.fence) {
        when(dirty(fence_index).contains(true.B)) {
          when(!(writeFifo.io.deq.valid || writeFifo_axi_busy)) {
            state := s_writeback
            axi_cnt.reset()
            victim.index := fence_index
            victim_cnt.reset()
            read_ready_cnt := 0.U
            victim.valid   := true.B
          }
        }.otherwise {
          when(valid(fence_index).contains(true.B)) {
            valid(fence_index)(0) := false.B
            valid(fence_index)(1) := false.B
          }
          state := s_wait
        }
      }
    }
    is(s_uncached) {
      when(arvalid && io.axi.ar.ready) {
        arvalid := false.B
      }
      when(io.axi.r.valid) {
        saved_rdata := io.axi.r.bits.data
        acc_err     := io.axi.r.bits.resp =/= RESP_OKEY.U
        state       := s_wait
      }
    }
    is(s_writeback) {
      when(fence.working) {
        when(victim_cnt.value =/= (cached_len).U) {
          victim_cnt.inc()
        }
        read_ready_cnt              := victim_cnt.value
        read_buffer(read_ready_cnt) := data(dirty(fence_index)(1))
        when(!aw_handshake) {
          aw.addr      := Cat(tag(dirty(fence_index)(1)), fence_index, 0.U(6.W))
          aw.len       := cached_len.U
          aw.size      := cached_size.U
          awvalid      := true.B
          w.data       := data(dirty(fence_index)(1))
          w.strb       := ~0.U(AXI_STRB_WID.W)
          w.last       := false.B
          wvalid       := true.B
          aw_handshake := true.B
        }
        when(io.axi.aw.fire) {
          awvalid := false.B
        }
        when(io.axi.w.fire) {
          when(w.last) {
            wvalid := false.B
          }.otherwise {
            w.data := Mux(
              ((axi_cnt.value + 1.U) === read_ready_cnt),
              data(dirty(fence_index)(1)),
              read_buffer(axi_cnt.value + 1.U)
            )
            axi_cnt.inc()
            when(axi_cnt.value + 1.U === (cached_len).U) {
              w.last := true.B
            }
          }
        }
        when(io.axi.b.valid) {
          dirty(fence_index)(dirty(fence_index)(1)) := false.B
          fence.working                             := false.B
          victim.valid                              := false.B
          acc_err                                   := io.axi.b.bits.resp =/= RESP_OKEY.U
          state                                     := s_idle
        }
      }.otherwise {
        aw_handshake  := false.B
        fence.working := true.B
        victim_cnt.inc()
      }
    }
    is(s_replace) {
      when(!(writeFifo.io.deq.valid || writeFifo_axi_busy)) {
        when(victim.working) {
          when(victim.writeback) {
            when(victim_cnt.value =/= (cached_len).U) {
              victim_cnt.inc()
            }
            read_ready_cnt              := victim_cnt.value
            read_buffer(read_ready_cnt) := data(replace_way)
            when(!aw_handshake) {
              aw.addr      := Cat(tag(replace_way), index, 0.U(offsetWidth.W))
              aw.len       := cached_len.U
              aw.size      := cached_size.U
              awvalid      := true.B
              aw_handshake := true.B
              w.data       := data(replace_way)
              w.strb       := ~0.U(AXI_STRB_WID.W)
              w.last       := false.B
              wvalid       := true.B
            }
            when(io.axi.aw.fire) {
              awvalid := false.B
            }
            when(io.axi.w.fire) {
              when(w.last) {
                wvalid := false.B
              }.otherwise {
                w.data := Mux(
                  ((axi_cnt.value + 1.U) === read_ready_cnt),
                  data(replace_way),
                  read_buffer(axi_cnt.value + 1.U)
                )
                axi_cnt.inc()
                when(axi_cnt.value + 1.U === (cached_len).U) {
                  w.last := true.B
                }
              }
            }
            when(io.axi.b.valid) {
              dirty(index)(replace_way) := false.B
              victim.writeback          := false.B
            }
          }
          when(!ar_handshake) {
            ar.addr                   := Cat(io.cpu.tlb.paddr(PADDR_WID - 1, offsetWidth), 0.U(offsetWidth.W))
            ar.len                    := cached_len.U
            ar.size                   := cached_size.U // 8 字节
            arvalid                   := true.B
            rready                    := true.B
            ar_handshake              := true.B
            victim.wstrb(replace_way) := ~0.U(AXI_STRB_WID.W)
            tag_wstrb(replace_way)    := true.B
            tag_wdata                 := io.cpu.tlb.ptag
          }
          when(io.axi.ar.fire) {
            tag_wstrb(replace_way) := false.B
            arvalid                := false.B
          }
          when(io.axi.r.fire) {
            when(io.axi.r.bits.last) {
              rready                    := false.B
              victim.wstrb(replace_way) := 0.U
            }.otherwise {
              victim.waddr := victim.waddr + 1.U
            }
          }
          when(
            (!victim.writeback || io.axi.b.valid) && ((ar_handshake && io.axi.r.valid && io.axi.r.bits.last) || (ar_handshake && !rready))
          ) {
            victim.valid              := false.B
            valid(index)(replace_way) := true.B
          }
          when(!victim.valid) {
            victim.working := false.B
            state          := s_idle
          }
        }.otherwise {
          ar_handshake   := false.B
          aw_handshake   := false.B
          victim.working := true.B
          victim_cnt.inc()
        }
      }
    }
    is(s_wait) {
      // 等待流水线的allow_to_go信号，防止多次发出读、写请求
      when(io.cpu.complete_single_request) {
        state := s_idle
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
