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

  io.cpu.inst_valid.map(_ := status === s_finishwait)
  val read_next_addr = (status === s_idle || status === s_finishwait)
  io.cpu.addr_err := io.cpu.addr(read_next_addr)(1, 0).orR
  val addr_err = io.cpu.addr(read_next_addr)(63, 32).orR
  val raddr    = Cat(io.cpu.addr(read_next_addr)(31, 2), 0.U(2.W))

  // default
  val ar = RegInit(0.U.asTypeOf(new Bundle {
    val valid = Bool()
    val addr  = UInt(32.W)
  }))
  val rdata   = RegInit(VecInit(Seq.fill(config.instFetchNum)(0.U(32.W))))
  val acc_err = RegInit(false.B)
  io.axi.ar.id       := 0.U
  io.axi.ar.addr     := ar.addr
  io.axi.ar.len      := (config.instFetchNum - 1).U
  io.axi.ar.size     := 2.U
  io.axi.ar.lock     := 0.U
  io.axi.ar.burst    := BURST_INCR.U
  io.axi.ar.valid    := ar.valid
  io.axi.ar.prot     := 0.U
  io.axi.ar.cache    := 0.U
  io.axi.r.ready     := true.B
  io.cpu.inst.map(_ := 0.U)
  io.cpu.acc_err     := acc_err
  io.cpu.stall       := false.B
  io.cpu.inst       := rdata

  switch(status) {
    is(s_idle) {
      when(io.cpu.req) {
        when(addr_err) {
          acc_err := true.B
          status  := s_finishwait
        }.otherwise {
          ar.addr  := raddr
          ar.valid := true.B
          status   := s_read
        }
      }
    }
    is(s_read) {
      when(io.axi.ar.ready) {
        ar.valid := false.B
      }
      when(io.axi.r.valid) {
        rdata(0) := Mux(ar.addr(2), io.axi.r.data(63, 32), io.axi.r.data(31, 0))
        rdata(1) := Mux(ar.addr(2), 0.U, io.axi.r.data(63, 32))
        acc_err  := io.axi.r.resp =/= RESP_OKEY.U
        status   := s_finishwait
      }
    }
    is(s_finishwait) {
      when(io.cpu.ready) {
        acc_err := false.B
        when(io.cpu.req) {
          when(addr_err) {
            acc_err := true.B
            status  := s_finishwait
          }.otherwise {
            ar.addr  := raddr
            ar.valid := true.B
            status   := s_read
          }
        }
      }
    }
  }
}
