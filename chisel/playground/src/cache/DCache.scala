// * Cache 设计借鉴了nscscc2021 cqu的cdim * //
package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

class DCache(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_DCache())
    val axi = new DCache_AXIInterface()
  })

  // * fsm * //
  val s_idle :: s_uncached :: s_writeback :: s_save :: Nil = Enum(4)
  val status                                               = RegInit(s_idle)

  val wstrb_gen = Wire(UInt(8.W))
  wstrb_gen := MuxLookup(io.cpu.size, "b1111_1111".U)(
    Seq(
      0.U -> ("b1".U << io.cpu.addr(2, 0)),
      1.U -> ("b11".U << Cat(io.cpu.addr(2, 1), 0.U(1.W))),
      2.U -> ("b1111".U << Cat(io.cpu.addr(2), 0.U(2.W))),
      3.U -> ("b1111_1111".U)
    )
  )

  io.cpu.valid := status === s_save

  val addr_err = io.cpu.addr(63, 32).orR

  // default
  val awvalid = RegInit(false.B)
  val awaddr  = RegInit(0.U(32.W))
  val awsize  = RegInit(0.U(3.W))
  io.axi.aw.id    := 1.U
  io.axi.aw.addr  := awaddr
  io.axi.aw.len   := 0.U
  io.axi.aw.size  := awsize
  io.axi.aw.burst := BURST_INCR.U
  io.axi.aw.valid := awvalid
  io.axi.aw.prot  := 0.U
  io.axi.aw.lock  := 0.U
  io.axi.aw.cache := 0.U

  val wvalid = RegInit(false.B)
  io.axi.w.id    := 1.U
  io.axi.w.data  := 0.U
  io.axi.w.strb  := 0.U
  io.axi.w.last  := 1.U
  io.axi.w.valid := wvalid

  io.axi.b.ready := 1.U

  val araddr = RegInit(0.U(32.W))
  val arsize = RegInit(0.U(3.W))
  io.axi.ar.id    := 1.U
  io.axi.ar.addr  := araddr
  io.axi.ar.len   := 0.U
  io.axi.ar.size  := arsize
  io.axi.ar.burst := BURST_INCR.U
  val arvalid = RegInit(false.B)
  io.axi.ar.valid := arvalid
  io.axi.ar.prot  := 0.U
  io.axi.ar.cache := 0.U
  io.axi.ar.lock  := 0.U
  val rready = RegInit(false.B)
  io.axi.r.ready := rready

  val saved_rdata = RegInit(0.U(DATA_WID.W))
  val acc_err     = RegInit(false.B)
  io.cpu.rdata        := saved_rdata
  io.cpu.dcache_stall := Mux(status === s_idle, io.cpu.en, status =/= s_save)
  io.cpu.acc_err      := acc_err

  switch(status) {
    is(s_idle) {
      acc_err := false.B
      when(io.cpu.en) {
        when(addr_err) {
          acc_err := true.B
          status  := s_save
        }.otherwise {
          when(io.cpu.write) {
            awaddr        := io.cpu.addr(31, 0)
            awsize        := Cat(false.B, io.cpu.size)
            awvalid       := true.B
            io.axi.w.data := io.cpu.wdata
            io.axi.w.strb := wstrb_gen
            wvalid        := true.B
            status        := s_writeback
          }.otherwise {
            araddr         := io.cpu.addr(31, 0)
            io.axi.ar.size := Cat(false.B, io.cpu.size)
            arvalid        := true.B
            rready         := true.B
            status         := s_uncached
          }
        }
      }
    }
    is(s_uncached) {
      when(io.axi.ar.ready && io.axi.ar.valid) {
        arvalid := false.B
      }
      when(io.axi.r.valid) {
        saved_rdata := io.axi.r.data
        acc_err     := io.axi.r.resp =/= RESP_OKEY.U
        status      := s_save
      }
    }
    is(s_writeback) {
      when(io.axi.aw.ready) {
        awvalid := false.B
      }
      when(io.axi.w.ready) {
        wvalid := false.B
      }
      when(io.axi.b.valid) {
        acc_err := io.axi.b.resp =/= RESP_OKEY.U
        status  := s_idle
      }
    }
    is(s_save) {
      when(!io.cpu.dcache_stall && io.cpu.cpu_ready) {
        status := s_idle
      }
    }
  }
}
