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
  val s_idle :: s_uncached :: s_writeback :: Nil = Enum(3)
  val status                                     = RegInit(s_idle)

  val addr_err = io.cpu.addr(63, 32).orR

  // default
  val awvalid = RegInit(false.B)
  val awaddr  = RegInit(0.U(AXI_ADDR_WID.W))
  val awsize  = RegInit(0.U(AXI_SIZE_WID.W))
  io.axi.aw.bits.id    := 1.U
  io.axi.aw.bits.addr  := awaddr
  io.axi.aw.bits.len   := 0.U
  io.axi.aw.bits.size  := awsize
  io.axi.aw.bits.burst := BURST_INCR.U
  io.axi.aw.valid      := awvalid
  io.axi.aw.bits.prot  := 0.U
  io.axi.aw.bits.lock  := 0.U
  io.axi.aw.bits.cache := 0.U

  val wvalid = RegInit(false.B)
  val wdata  = RegInit(0.U(AXI_DATA_WID.W))
  val wstrb  = RegInit(0.U(AXI_STRB_WID.W))
  io.axi.w.bits.id   := 1.U
  io.axi.w.bits.data := wdata
  io.axi.w.bits.strb := wstrb
  io.axi.w.bits.last := 1.U
  io.axi.w.valid     := wvalid

  io.axi.b.ready := 1.U

  val araddr  = RegInit(0.U(AXI_ADDR_WID.W))
  val arsize  = RegInit(0.U(AXI_SIZE_WID.W))
  val arvalid = RegInit(false.B)
  io.axi.ar.bits.id    := 1.U
  io.axi.ar.bits.addr  := araddr
  io.axi.ar.bits.len   := 0.U
  io.axi.ar.bits.size  := arsize
  io.axi.ar.bits.burst := BURST_INCR.U
  io.axi.ar.valid      := arvalid
  io.axi.ar.bits.prot  := 0.U
  io.axi.ar.bits.cache := 0.U
  io.axi.ar.bits.lock  := 0.U

  val rready = RegInit(false.B)
  io.axi.r.ready := rready

  val saved_rdata = RegInit(0.U(DATA_WID.W))
  val acc_err     = RegInit(false.B)

  val mmio_read_stall  = !io.cpu.wen.orR
  val mmio_write_stall = io.cpu.wen.orR && !io.axi.w.ready
  val cached_stall     = false.B
  io.cpu.dcache_ready := status === s_idle
  io.cpu.rdata        := saved_rdata
  io.cpu.acc_err      := acc_err

  switch(status) {
    is(s_idle) {
      acc_err := false.B
      when(io.cpu.en) {
        when(addr_err) {
          acc_err := true.B
          status  := s_idle
        }.otherwise {
          when(io.cpu.wen) {
            awaddr  := io.cpu.addr(31, 0)
            awsize  := Cat(false.B, io.cpu.size)
            awvalid := true.B
            wdata   := io.cpu.wdata
            wstrb   := io.cpu.wstrb
            wvalid  := true.B
            status  := s_writeback
          }.otherwise {
            araddr  := io.cpu.addr(31, 0)
            arsize  := Cat(false.B, io.cpu.size)
            arvalid := true.B
            rready  := true.B
            status  := s_uncached
          }
        }
      }
    }
    is(s_uncached) {
      when(io.axi.ar.ready && io.axi.ar.valid) {
        arvalid := false.B
      }
      when(io.axi.r.valid) {
        saved_rdata := io.axi.r.bits.data
        acc_err     := io.axi.r.bits.resp =/= RESP_OKEY.U
        status      := s_idle
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
        acc_err := io.axi.b.bits.resp =/= RESP_OKEY.U
        status  := s_idle
      }
    }
  }
}
