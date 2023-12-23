package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.CacheConfig
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

class ICache(cacheConfig: CacheConfig)(implicit config: CpuConfig) extends Module {
  val nway:            Int = cacheConfig.nway
  val nindex:          Int = cacheConfig.nindex
  val nbank:           Int = cacheConfig.nbank
  val instFetchNum:    Int = config.instFetchNum
  val bankOffsetWidth: Int = cacheConfig.bankOffsetWidth
  val bankIndexWidth:  Int = cacheConfig.offsetWidth - bankOffsetWidth
  val bytesPerBank:    Int = cacheConfig.bytesPerBank
  val tagWidth:        Int = cacheConfig.tagWidth
  val indexWidth:      Int = cacheConfig.indexWidth
  val offsetWidth:     Int = cacheConfig.offsetWidth
  val bitsPerBank:     Int = cacheConfig.bitsPerBank
  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_ICache())
    val axi = new ICache_AXIInterface()
  })
  require(isPow2(instFetchNum), "ninst must be power of 2")
  require(instFetchNum == bytesPerBank / 4, "instFetchNum must equal to instperbank")
  require(bitsPerBank >= AXI_DATA_WID, "bitsPerBank must be greater than AXI_DATA_WID")

  // 整个宽度为PADDR_WID的地址
  // ==========================================================
  // |        tag         |  index |         offset           |
  // |                    |        | bank index | bank offset |
  // ==========================================================

  // 一个bank是bitsPerBank宽度，一个bank中有instFetchNum个指令
  // 每个bank中指令块的个数，一个指令块是AXI_DATA_WID宽度
  val instBlocksPerBank = bitsPerBank / AXI_DATA_WID

  val bank_index  = io.cpu.addr(0)(offsetWidth - 1, bankOffsetWidth)
  val bank_offset = io.cpu.addr(0)(bankOffsetWidth - 1, 2) // PC低2位必定是0

  val tlb_fill = RegInit(false.B)
  // * fsm * //
  val s_idle :: s_uncached :: s_replace :: s_save :: Nil = Enum(4)
  val state                                              = RegInit(s_idle)

  // * nway * nindex * //
  // * 128 bit for 4 inst * //
  // =========================================================
  // | valid | tag |  bank 0 | bank 1  |  bank 2 | bank 3 |
  // | 1111  | 20  |   128   |   128   |   128   |  128   |
  // =========================================================
  // |                bank               |
  // | inst 0 | inst 1 | inst 2 | inst 3 |
  // |   32   |   32   |   32   |   32   |
  // =====================================

  val valid = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nbank)(false.B)))))

  // * should choose next addr * //
  val should_next_addr = (state === s_idle && !tlb_fill) || (state === s_save)

  // 读取一个cache条目中的所有bank行
  val data        = Wire(Vec(nway, Vec(nbank, Vec(instBlocksPerBank, UInt(AXI_DATA_WID.W)))))
  val data_rindex = io.cpu.addr(should_next_addr)(indexWidth + offsetWidth - 1, offsetWidth)

  val tag       = RegInit(VecInit(Seq.fill(nway)(0.U(tagWidth.W))))
  val tag_raddr = io.cpu.addr(should_next_addr)(indexWidth + offsetWidth - 1, offsetWidth)
  val tag_wstrb = RegInit(VecInit(Seq.fill(nway)(false.B)))
  val tag_wdata = RegInit(0.U(tagWidth.W))

  // * lru * //
  val lru = RegInit(VecInit(Seq.fill(nindex * nbank)(false.B))) // TODO:检查lru的正确性

  // * itlb * //
  when(tlb_fill) { tlb_fill := false.B }
  io.cpu.tlb.fill           := tlb_fill
  io.cpu.tlb.icache_is_save := (state === s_save)

  // * fence * //
  // fence指令时清空cache，等同于将所有valid位置0
  when(io.cpu.fence && !io.cpu.icache_stall && io.cpu.cpu_ready) {
    valid := 0.U.asTypeOf(valid)
  }

  // * replace index * //
  val replace_index = RegInit(0.U(indexWidth.W))
  // 用于控制写入一行cache条目中的哪个bank, 一个bank可能有多次写入
  val repalce_wstrb = RegInit(
    VecInit(Seq.fill(nway)(VecInit(Seq.fill(nbank)(VecInit(Seq.fill(instBlocksPerBank)((false.B)))))))
  )

  // * virtual index * //
  val virtual_index = io.cpu.addr(0)(indexWidth + offsetWidth - 1, offsetWidth)

  // * cache hit * //
  val tag_compare_valid   = VecInit(Seq.tabulate(nway)(i => tag(i) === io.cpu.tlb.tag && valid(virtual_index)(i)))
  val cache_hit           = tag_compare_valid.contains(true.B)
  val cache_hit_available = cache_hit && io.cpu.tlb.translation_ok && !io.cpu.tlb.uncached
  val sel                 = tag_compare_valid(1)

  // |     bank        |
  // | inst 0 | inst 1 |
  // |   32   |   32   |
  // 将一个 bank 中的指令分成 instFetchNum 份，每份 INST_WID bit
  val inst_in_bank = VecInit(
    Seq.tabulate(instFetchNum)(i => data(sel)(bank_index).asUInt((i + 1) * INST_WID - 1, i * INST_WID))
  )

  // 将 inst_in_bank 中的指令按照 bank_offset 位偏移量重新排列
  // 处理偏移导致的跨 bank 读取
  // 当offset为0时，不需要重新排列
  // 当offset为1时，此时发送到cpu的inst0应该是inst1，inst1应该无数据，并设置对应的valid
  val inst = VecInit(
    Seq.tabulate(instFetchNum)(i =>
      Mux(
        i.U <= ((instFetchNum - 1).U - bank_offset),
        inst_in_bank(i.U + bank_offset),
        0.U
      )
    )
  )
  val inst_valid = VecInit(
    Seq.tabulate(instFetchNum)(i => cache_hit_available && i.U <= ((instFetchNum - 1).U - bank_offset))
  )

  val saved = RegInit(VecInit(Seq.fill(instFetchNum)(0.U.asTypeOf(new Bundle {
    val inst  = UInt(INST_WID.W)
    val valid = Bool()
  }))))

  // 对于可缓存段访存时读取的数据宽度应该和AXI_DATA的宽度相同
  val cached_rsize = log2Ceil(AXI_DATA_WID / 8)
  // 对于不可缓存段访存时读取的数据宽度应该和指令宽度相同
  val uncached_rsize = log2Ceil(INST_WID / 8)

  // bank tag ram
  for { i <- 0 until nway } {
    // 每一个条目中有nbank个bank，每个bank存储instFetchNum个指令
    val bank =
      Seq.fill(nbank)(
        Seq.fill(instBlocksPerBank)(
          Module(new SimpleDualPortRam(depth = nindex, width = AXI_DATA_WID, byteAddressable = false))
        )
      )
    for { j <- 0 until nbank } {
      for { k <- 0 until instBlocksPerBank } {
        bank(j)(k).io.ren   := true.B
        bank(j)(k).io.raddr := data_rindex
        data(i)(j)(k)       := bank(j)(k).io.rdata

        bank(j)(k).io.wen   := repalce_wstrb(i)(j)(k)
        bank(j)(k).io.waddr := replace_index
        bank(j)(k).io.wdata := io.axi.r.bits.data
        bank(j)(k).io.wstrb := repalce_wstrb(i)(j)(k)
      }
    }
  }

  for { i <- 0 until instFetchNum } {
    io.cpu.inst_valid(i) := Mux(state === s_idle && !tlb_fill, inst_valid(i), saved(i).valid) && io.cpu.req
    io.cpu.inst(i)       := Mux(state === s_idle && !tlb_fill, inst(i), saved(i).inst)
  }

  for { i <- 0 until nway } {
    val tag_bram = Module(new LUTRam(nindex, tagWidth))
    tag_bram.io.raddr := tag_raddr
    tag(i)            := tag_bram.io.rdata

    tag_bram.io.wen   := tag_wstrb(i)
    tag_bram.io.waddr := replace_index
    tag_bram.io.wdata := tag_wdata
  }

  io.cpu.icache_stall := Mux(state === s_idle && !tlb_fill, (!cache_hit_available && io.cpu.req), state =/= s_save)

  val ar      = RegInit(0.U.asTypeOf(new AR()))
  val arvalid = RegInit(false.B)
  ar <> io.axi.ar.bits
  arvalid <> io.axi.ar.valid

  val r      = RegInit(0.U.asTypeOf(new R()))
  val rready = RegInit(false.B)
  r <> io.axi.r.bits
  rready <> io.axi.r.ready

  val acc_err  = RegInit(false.B)
  val addr_err = io.cpu.addr(should_next_addr)(XLEN - 1, PADDR_WID).orR

  when(acc_err) { acc_err := false.B }
  io.cpu.acc_err := acc_err

  switch(state) {
    is(s_idle) {
      when(tlb_fill) {
        when(!io.cpu.tlb.hit) {
          state          := s_save
          saved(0).inst  := 0.U
          saved(0).valid := true.B
        }
      }.elsewhen(io.cpu.req) {
        when(addr_err) {
          acc_err        := true.B
          state          := s_save
          saved(0).inst  := 0.U
          saved(0).valid := true.B
        }.elsewhen(!io.cpu.tlb.translation_ok) {
          tlb_fill := true.B
        }.elsewhen(io.cpu.tlb.uncached) {
          state   := s_uncached
          ar.addr := io.cpu.tlb.pa
          ar.len  := 0.U
          ar.size := uncached_rsize.U
          arvalid := true.B
        }.elsewhen(!cache_hit) {
          state := s_replace
          // 取指时按bank块取指
          ar.addr := Cat(io.cpu.tlb.pa(PADDR_WID - 1, offsetWidth), 0.U(offsetWidth.W))
          ar.len  := (nbank * instBlocksPerBank - 1).U
          ar.size := cached_rsize.U
          arvalid := true.B

          replace_index := virtual_index
          repalce_wstrb(lru(virtual_index)).map(_.map(_ := false.B))
          repalce_wstrb(lru(virtual_index))(0)(0)  := true.B // 从第一个bank的第一个指令块开始写入
          tag_wstrb(lru(virtual_index))            := true.B
          tag_wdata                                := io.cpu.tlb.tag
          valid(virtual_index)(lru(virtual_index)) := true.B
        }.elsewhen(!io.cpu.icache_stall) {
          lru(virtual_index) := ~sel
          when(!io.cpu.cpu_ready) {
            state := s_save
            (1 until instFetchNum).foreach(i => saved(i).inst := inst(i))
            (0 until instFetchNum).foreach(i => saved(i).valid := inst_valid(i))
          }
        }
      }
    }
    is(s_uncached) {
      when(io.axi.ar.valid) {
        when(io.axi.ar.ready) {
          arvalid := false.B
          rready  := true.B
        }
      }.elsewhen(io.axi.r.fire) {
        // * uncached not support burst transport * //
        state          := s_save
        saved(0).inst  := Mux(ar.addr(2), io.axi.r.bits.data(63, 32), io.axi.r.bits.data(31, 0))
        saved(0).valid := true.B
        rready         := false.B
        acc_err        := io.axi.r.bits.resp =/= RESP_OKEY.U
      }
    }
    is(s_replace) {
      when(io.axi.ar.valid) {
        when(io.axi.ar.ready) {
          arvalid := false.B
          rready  := true.B
        }
      }.elsewhen(io.axi.r.fire) {
        // * burst transport * //
        when(!io.axi.r.bits.last) {
          // 左移写掩码，写入下一个bank，或是同一个bank的下一个指令
          repalce_wstrb(lru(virtual_index)) :=
            ((repalce_wstrb(lru(virtual_index)).asUInt << 1)).asTypeOf(repalce_wstrb(lru(virtual_index)))
        }.otherwise {
          rready := false.B
          repalce_wstrb(lru(virtual_index)).map(_.map(_ := false.B))
          tag_wstrb(lru(virtual_index)) := false.B
        }
      }.elsewhen(!io.axi.r.ready) {
        state := s_idle
      }
    }
    is(s_save) {
      when(io.cpu.cpu_ready && !io.cpu.icache_stall) {
        state := s_idle
        (0 until instFetchNum).foreach(i => saved(i).valid := false.B)
      }
    }
  }

  println("ICache: ")
  println("nindex: " + nindex)
  println("nbank: " + nbank)
  println("bankOffsetWidth: " + bankOffsetWidth)
  println("bytesPerBank: " + bytesPerBank)
  println("tagWidth: " + tagWidth)
  println("indexWidth: " + indexWidth)
  println("offsetWidth: " + offsetWidth)
}
