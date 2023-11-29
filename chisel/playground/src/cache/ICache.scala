// * Cache 设计借鉴了nscscc2021 cqu的cdim * //
package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

class ICache(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_ICache())
    val axi = new ICache_AXIInterface()
  })

  val s_idle :: s_uncached :: s_save :: Nil = Enum(3)
  val status                                = RegInit(s_idle)

  val read_next_addr = (status === s_idle || status === s_save)
  val pc             = Cat(io.cpu.addr(read_next_addr)(31, 2), 0.U(2.W))

  // default
  val arvalid = RegInit(false.B)
  val araddr  = RegInit(0.U(AXI_ADDR_WID.W))
  io.axi.ar.id    := 0.U
  io.axi.ar.addr  := araddr
  io.axi.ar.len   := 0.U
  io.axi.ar.size  := 2.U
  io.axi.ar.lock  := 0.U
  io.axi.ar.burst := BURST_INCR.U
  io.axi.ar.valid := arvalid
  io.axi.ar.prot  := 0.U
  io.axi.ar.cache := 0.U

  val rready = RegInit(false.B)
  val saved = RegInit(VecInit(Seq.fill(config.instFetchNum)(0.U.asTypeOf(new Bundle {
    val inst  = UInt(AXI_DATA_WID.W)
    val valid = Bool()
  }))))
  io.axi.r.ready := true.B

  val acc_err  = RegInit(false.B)
  val addr_err = io.cpu.addr(read_next_addr)(63, 32).orR

  (0 until config.instFetchNum).foreach(i => {
    io.cpu.inst(i)       := Mux(status === s_idle && !acc_err, 0.U, saved(i).inst)
    io.cpu.inst_valid(i) := Mux(status === s_idle && !acc_err, false.B, saved(i).valid) && io.cpu.req
  })

  io.cpu.addr_err     := addr_err
  io.cpu.acc_err      := acc_err
  io.cpu.icache_stall := Mux(status === s_idle && !acc_err, io.cpu.req, status =/= s_save)

  switch(status) {
    is(s_idle) {
      acc_err := false.B
      when(io.cpu.req) {
        when(addr_err) {
          acc_err        := true.B
          saved(0).valid := true.B
          status         := s_save
        }.otherwise {
          araddr         := pc
          arvalid        := true.B
          io.axi.ar.len  := 0.U
          io.axi.ar.size := 2.U
          status         := s_uncached
        }
      }
    }
    is(s_uncached) {
      when(io.axi.ar.valid) {
        when(io.axi.ar.ready) {
          arvalid := false.B
          rready  := true.B
        }
      }.elsewhen(io.axi.r.valid && io.axi.r.ready) {
        saved(0).inst  := Mux(araddr(2), io.axi.r.data(63, 32), io.axi.r.data(31, 0))
        saved(0).valid := true.B
        acc_err        := io.axi.r.resp =/= RESP_OKEY.U
        rready         := false.B
        status         := s_save
      }
    }
    is(s_save) {
      when(io.cpu.cpu_ready && !io.cpu.icache_stall) {
        status := s_idle
        (0 until config.instFetchNum).foreach(i => saved(i).valid := false.B)
      }
    }
  }
}
