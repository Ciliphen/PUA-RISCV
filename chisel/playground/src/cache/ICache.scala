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
  ======================================================
  | valid | tag |  bank 0 | bank 1  |  ...    | bank n |
  |   1   |     |         |         |         |        |
  ======================================================
  |                bank               |
  | inst 0 | inst 1 |   ...  | inst n |
  |   32   |   32   |   ...  |   32   |
  =====================================

  本CPU的实现如下：
    每个bank分为多个instBlocks，每个instBlocks的宽度为AXI_DATA_WID，这样能方便的和AXI总线进行交互
    RV64实现中AXI_DATA_WID为64，所以每个instBlocks可以存储2条指令
    而instBlocks的个数会和instFetchNum相关
      - 当instFetchNum为4时，instBlocks的个数为2
      - 当instFetchNum为2时，instBlocks的个数为1
    读取数据时会将一个bank中的所有instBlocks读取出来，然后再将instBlocks中的数据按照偏移量重新排列
    这样的设计可以保证一个bank的指令数对应instFetchNum

  ======================================================
  | valid | tag |  bank 0 | bank 1  |  ...    | bank n |
  |   1   |     |         |         |         |        |
  ======================================================
  |                bank               |
  |   instBlocks    |   instBlocks    |
  | inst 0 | inst 1 | inst 0 | inst 1 |
  |   32   |   32   |   32   |   32   |
  =====================================
 */

class ICache(cacheConfig: CacheConfig)(implicit cpuConfig: CpuConfig) extends Module with HasTlbConst {
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

  def pAddr = new Bundle {
    val tag    = UInt(ppnLen.W)
    val index  = UInt(indexWidth.W)
    val offset = UInt(offsetWidth.W)
  }

  def bankAddr = new Bundle {
    val index  = UInt(bankIndexWidth.W)
    val offset = UInt(bankOffsetWidth.W)
  }

  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_ICache())
    val axi = new ICache_AXIInterface()
  })
  require(isPow2(instFetchNum), "ninst must be power of 2")
  require(instFetchNum == bytesPerBank / 4, "instFetchNum must equal to instperbank")
  require(
    bitsPerBank >= AXI_DATA_WID && bitsPerBank % AXI_DATA_WID == 0,
    "bitsPerBank must be greater than AXI_DATA_WID"
  )

  // 一个bank是bitsPerBank宽度，一个bank中有instFetchNum个指令
  // 每个bank中指令块的个数，一个指令块是AXI_DATA_WID宽度
  val instBlocksPerBank = bitsPerBank / AXI_DATA_WID

  val bank_index  = io.cpu.addr(0)(offsetWidth - 1, bankOffsetWidth)
  val bank_offset = io.cpu.addr(0)(bankOffsetWidth - 1, log2Ceil(INST_WID / 8)) // PC低2位必定是0

  // * fsm * //
  val s_idle :: s_uncached :: s_replace :: s_wait :: s_fence :: s_tlb_refill :: Nil = Enum(6)
  val state                                                                         = RegInit(s_idle)

  // nway 路，每路 nindex 行，每行 nbank 个 bank，每行的nbank共用一个valid
  val valid = RegInit(VecInit(Seq.fill(nway)(VecInit(Seq.fill(nindex)(false.B)))))

  // * should choose next addr * //
  val use_next_addr = (state === s_idle) || (state === s_wait)

  // 读取一个cache条目中的所有bank行
  val data        = Wire(Vec(nway, Vec(nbank, Vec(instBlocksPerBank, UInt(AXI_DATA_WID.W)))))
  val data_rindex = io.cpu.addr(use_next_addr)(indexWidth + offsetWidth - 1, offsetWidth)

  val tag       = RegInit(VecInit(Seq.fill(nway)(0.U(tagWidth.W))))
  val tag_raddr = io.cpu.addr(use_next_addr)(indexWidth + offsetWidth - 1, offsetWidth)
  val tag_wstrb = RegInit(VecInit(Seq.fill(nway)(false.B)))
  val tag_wdata = RegInit(0.U(tagWidth.W))

  // * lru * //// TODO:检查lru的正确性，增加可拓展性，目前只支持两路的cache
  val lru = RegInit(VecInit(Seq.fill(nindex)(false.B)))

  val replace_index = io.cpu.addr(0)(indexWidth + offsetWidth - 1, offsetWidth)
  // 需要替换的路号
  val replace_way = lru(replace_index)

  // 用于控制写入一行cache条目中的哪个bank, 一个bank可能有多次写入
  val replace_wstrb = RegInit(
    VecInit(Seq.fill(nway)(VecInit(Seq.fill(nbank)(VecInit(Seq.fill(instBlocksPerBank)((false.B)))))))
  )

  // * cache hit * //
  val tag_compare_valid   = VecInit(Seq.tabulate(nway)(i => tag(i) === io.cpu.tlb.ptag && valid(i)(replace_index)))
  val cache_hit           = tag_compare_valid.contains(true.B)
  val cache_hit_available = cache_hit && io.cpu.tlb.hit && !io.cpu.tlb.uncached
  val select_way          = tag_compare_valid(1) // 1路命中时值为1，0路命中时值为0 //TODO:支持更多路数

  // 将一个 bank 中的指令分成 instFetchNum 份，每份 INST_WID bit
  val inst_in_bank = VecInit(
    Seq.tabulate(instFetchNum)(i => data(select_way)(bank_index).asUInt((i + 1) * INST_WID - 1, i * INST_WID))
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

  val rdata_in_wait = RegInit(VecInit(Seq.fill(instFetchNum)(0.U.asTypeOf(new Bundle {
    val inst  = UInt(INST_WID.W)
    val valid = Bool()
  }))))

  // 对于可缓存段访存时读取的数据宽度应该和AXI_DATA的宽度相同
  val cached_size = log2Ceil(AXI_DATA_WID / 8)
  val cached_len  = (nbank * instBlocksPerBank - 1)
  // 对于不可缓存段访存时读取的数据宽度应该和指令宽度相同
  val uncached_size = log2Ceil(INST_WID / 8)
  val uncached_len  = 0

  // bank tag ram
  for { i <- 0 until nway } {
    // 每一个条目中有nbank个bank，每个bank存储instFetchNum个指令
    // 每次写入cache时将写完一整个cache行
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

        bank(j)(k).io.wen   := replace_wstrb(i)(j)(k)
        bank(j)(k).io.waddr := replace_index
        bank(j)(k).io.wdata := io.axi.r.bits.data
        bank(j)(k).io.wstrb := replace_wstrb(i)(j)(k)
      }
    }
  }

  for { i <- 0 until instFetchNum } {
    io.cpu.inst_valid(i) := Mux(state === s_idle, inst_valid(i), rdata_in_wait(i).valid) && io.cpu.req
    io.cpu.inst(i)       := Mux(state === s_idle, inst(i), rdata_in_wait(i).inst)
  }

  for { i <- 0 until nway } {
    // 实例化了nway个tag ram
    val tagBram = Module(new LUTRam(nindex, tagWidth))
    tagBram.io.raddr := tag_raddr
    tag(i)           := tagBram.io.rdata

    tagBram.io.wen   := tag_wstrb(i)
    tagBram.io.waddr := replace_index
    tagBram.io.wdata := tag_wdata
  }

  io.cpu.icache_stall := Mux(state === s_idle, (!cache_hit_available && io.cpu.req), state =/= s_wait)

  io.cpu.tlb.vaddr                   := io.cpu.addr(0)
  io.cpu.tlb.complete_single_request := io.cpu.complete_single_request
  io.cpu.tlb.en                      := io.cpu.req && (state === s_idle || state === s_tlb_refill)

  val ar      = RegInit(0.U.asTypeOf(new AR()))
  val arvalid = RegInit(false.B)
  ar <> io.axi.ar.bits
  arvalid <> io.axi.ar.valid

  val r      = RegInit(0.U.asTypeOf(new R()))
  val rready = RegInit(false.B)
  r <> io.axi.r.bits
  rready <> io.axi.r.ready

  val access_fault = RegInit(false.B)
  val page_fault   = RegInit(false.B)
  // sv39的63-39位需要与第38位相同
  val addr_err =
    io.cpu
      .addr(use_next_addr)(XLEN - 1, VADDR_WID)
      .asBools
      .map(_ =/= io.cpu.addr(use_next_addr)(VADDR_WID - 1))
      .reduce(_ || _)

  io.cpu.access_fault := access_fault //TODO：实现cached段中的访存response错误
  io.cpu.page_fault   := page_fault

  switch(state) {
    is(s_idle) {
      access_fault := false.B // 在idle时清除access_fault
      page_fault   := false.B // 在idle时清除page_fault
      when(io.cpu.req) {
        when(addr_err) {
          access_fault           := true.B
          state                  := s_wait
          rdata_in_wait(0).inst  := Instructions.NOP
          rdata_in_wait(0).valid := true.B
        }.elsewhen(!io.cpu.tlb.hit) {
          state := s_tlb_refill
        }.elsewhen(io.cpu.tlb.uncached) {
          state   := s_uncached
          ar.addr := io.cpu.tlb.paddr
          ar.len  := uncached_len.U
          ar.size := uncached_size.U
          arvalid := true.B
        }.elsewhen(!cache_hit) {
          state := s_replace
          // 取指时按bank块取指
          ar.addr := Cat(io.cpu.tlb.paddr(PADDR_WID - 1, offsetWidth), 0.U(offsetWidth.W))
          ar.len  := cached_len.U
          ar.size := cached_size.U
          arvalid := true.B

          replace_wstrb(replace_way).map(_.map(_ := false.B))
          replace_wstrb(replace_way)(0)(0)  := true.B // 从第一个bank的第一个指令块开始写入
          tag_wstrb(replace_way)            := true.B
          tag_wdata                         := io.cpu.tlb.ptag
          valid(replace_way)(replace_index) := true.B
        }.elsewhen(!io.cpu.icache_stall) {
          replace_way := ~select_way
          when(!io.cpu.complete_single_request) {
            state := s_wait
            (1 until instFetchNum).foreach(i => rdata_in_wait(i).inst := inst(i))
            (0 until instFetchNum).foreach(i => rdata_in_wait(i).valid := inst_valid(i))
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
        rdata_in_wait(0).inst  := Mux(ar.addr(2), io.axi.r.bits.data(63, 32), io.axi.r.bits.data(31, 0))
        rdata_in_wait(0).valid := true.B
        rready                 := false.B
        access_fault           := io.axi.r.bits.resp =/= RESP_OKEY.U
        state                  := s_wait
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
          replace_wstrb(replace_way) :=
            ((replace_wstrb(replace_way).asUInt << 1)).asTypeOf(replace_wstrb(replace_way))
        }.otherwise {
          rready := false.B
          replace_wstrb(replace_way).map(_.map(_ := false.B))
          tag_wstrb(replace_way) := false.B
        }
      }.elsewhen(!io.axi.r.ready) {
        state := s_idle
      }
    }
    is(s_wait) {
      // 等待流水线的allow_to_go信号，防止多次发出读请求
      when(io.cpu.complete_single_request) {
        access_fault := false.B // 清除access_fault
        page_fault   := false.B // 清除page_fault
        state        := s_idle
        (0 until instFetchNum).foreach(i => rdata_in_wait(i).valid := false.B)
      }
    }
    is(s_fence) {
      // 等待dcache完成写回操作，且等待axi总线完成读取操作，因为icache发生状态转移时可能正在读取数据
      when(!io.cpu.dcache_stall && !io.axi.r.valid) {
        state := s_idle
      }
    }
    is(s_tlb_refill) {
      when(io.cpu.tlb.access_fault) {
        access_fault           := true.B
        state                  := s_wait
        rdata_in_wait(0).inst  := Instructions.NOP
        rdata_in_wait(0).valid := true.B
      }.elsewhen(io.cpu.tlb.page_fault) {
        page_fault             := true.B
        state                  := s_wait
        rdata_in_wait(0).inst  := Instructions.NOP
        rdata_in_wait(0).valid := true.B
      }.otherwise {
        when(io.cpu.tlb.hit) {
          state := s_idle
        }
      }
    }
  }

  // * fence * //
  // 不论icache在什么状态，fence指令优先度最高，会强制将icache状态转移为s_fence
  when(io.cpu.fence_i) {
    valid := 0.U.asTypeOf(valid) // fence.i指令需要icache，等同于将所有valid位置0
    state := s_fence
  }

  println("----------------------------------------")
  println("ICache: ")
  println("nindex: " + nindex)
  println("nbank: " + nbank)
  println("bankOffsetWidth: " + bankOffsetWidth)
  println("bytesPerBank: " + bytesPerBank)
  println("tagWidth: " + tagWidth)
  println("indexWidth: " + indexWidth)
  println("offsetWidth: " + offsetWidth)
  println("----------------------------------------")
}
