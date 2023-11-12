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

  val s_idle :: s_read :: s_finishwait :: Nil = Enum(3)
  val status                                  = RegInit(s_idle)

  io.cpu.valid := status === s_finishwait
  val addr_err = io.cpu.addr.orR

  io.axi.ar.addr  := 0.U
  io.axi.ar.len   := 0.U
  io.axi.ar.size  := 2.U
  io.axi.ar.burst := BURST_FIXED.U
  io.axi.ar.valid := false.B
  io.axi.r.ready  := true.B
  io.cpu.rdata    := 0.U
  io.cpu.acc_err  := false.B

  switch(status) {
    is(s_idle) {
      when(io.cpu.en) {
        io.cpu.acc_err := true.B
        status         := s_finishwait
      }.otherwise {
        io.axi.ar.addr  := Cat(io.cpu.addr(31, 2), 0.U(2.W))
        io.axi.ar.valid := true.B
        status          := s_read
      }
    }
    is(s_read) {
      when(io.axi.ar.ready) {
        io.axi.ar.valid := false.B
      }
      when(io.axi.r.valid) {
        io.cpu.rdata   := Mux(io.axi.ar.addr(2), io.axi.r.data(63, 32), io.axi.r.data(31, 0))
        io.cpu.acc_err := io.axi.r.resp =/= RESP_OKEY.U
        status         := s_finishwait
      }
    }
    is(s_finishwait) {
      when(io.cpu.ready) {
        io.cpu.acc_err := false.B
        when(io.cpu.en) {
          when(addr_err) {
            io.cpu.acc_err := true.B
            status         := s_finishwait
          }.otherwise {
            io.axi.ar.addr  := Cat(io.cpu.addr(31, 2), 0.U(2.W))
            io.axi.ar.valid := true.B
            status          := s_read
          }
        }
      }
    }
  }
}
