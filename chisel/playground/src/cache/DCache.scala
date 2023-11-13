// * Cache 设计借鉴了nscscc2021 cqu的cdim * //
package cache

import chisel3._
import chisel3.util._
import memory._
import cpu.CacheConfig
import cpu.defines._
import cpu.CpuConfig
import cpu.defines.Const._

class DCache(implicit config: CpuConfig) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new Cache_DCache())
    val axi = new DCache_AXIInterface()
  })

  // * fsm * //
  val s_idle :: s_read :: s_write :: s_finishwait :: Nil = Enum(4)
  val status                                             = RegInit(s_idle)

  val wstrb_gen = Wire(UInt(8.W))
  wstrb_gen := MuxLookup(io.cpu.size, "b1111_1111".U)(
    Seq(
      0.U -> ("b1".U << io.cpu.addr(2, 0)),
      1.U -> ("b11".U << Cat(io.cpu.addr(2, 1), 0.U(1.W))),
      2.U -> ("b1111".U << Cat(io.cpu.addr(2), 0.U(2.W))),
      3.U -> ("b1111_1111".U)
    )
  )

  io.cpu.valid := status === s_finishwait

  val addr_err = io.cpu.addr(63, 32).orR

  // default
  io.axi.aw.id    := 1.U
  io.axi.aw.addr  := 0.U
  io.axi.aw.len   := 0.U
  io.axi.aw.size  := 0.U
  io.axi.aw.burst := BURST_FIXED.U
  io.axi.aw.valid := 0.U
  io.axi.aw.prot  := 0.U
  io.axi.aw.lock  := 0.U
  io.axi.aw.cache := 0.U
  io.axi.w.id     := 1.U
  io.axi.w.data   := 0.U
  io.axi.w.strb   := 0.U
  io.axi.w.last   := 1.U
  io.axi.w.valid  := 0.U
  io.axi.b.ready  := 1.U
  io.axi.ar.id    := 1.U
  io.axi.ar.addr  := 0.U
  io.axi.ar.len   := 0.U
  io.axi.ar.size  := 0.U
  io.axi.ar.burst := BURST_FIXED.U
  val arvalid = RegInit(false.B)
  io.axi.ar.valid := arvalid
  io.axi.ar.prot  := 0.U
  io.axi.ar.cache := 0.U
  io.axi.ar.lock  := 0.U
  io.axi.r.ready  := true.B
  io.cpu.rdata    := 0.U

  io.cpu.acc_err := false.B

  switch(status) {
    is(s_idle) {
      when(io.cpu.en) {
        when(addr_err) {
          io.cpu.acc_err := true.B
          status         := s_finishwait
        }.otherwise {
          when(io.cpu.write) {
            io.axi.aw.addr  := io.cpu.addr(31, 0)
            io.axi.aw.size  := Cat(false.B, io.cpu.size)
            io.axi.aw.valid := true.B
            io.axi.w.data   := io.cpu.wdata
            io.axi.w.strb   := wstrb_gen
            io.axi.w.valid  := true.B
            status          := s_write
          }.otherwise {
            io.axi.ar.addr := io.cpu.addr(31, 0)
            io.axi.ar.size := Cat(false.B, io.cpu.size)
            arvalid        := true.B
            status         := s_read
          }
        }
      }
    }
    is(s_read) {
      when(io.axi.ar.ready) {
        arvalid := false.B
      }
      when(io.axi.r.valid) {
        io.cpu.rdata   := io.axi.r.data
        io.cpu.acc_err := io.axi.r.resp =/= RESP_OKEY.U
        status         := s_finishwait
      }
    }
    is(s_write) {
      when(io.axi.aw.ready) {
        io.axi.aw.valid := false.B
      }
      when(io.axi.w.ready) {
        io.axi.w.valid := false.B
      }
      when(io.axi.b.valid) {
        io.cpu.acc_err := io.axi.b.resp =/= RESP_OKEY.U
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
            when(io.cpu.write) {
              io.axi.aw.addr  := io.cpu.addr(31, 0)
              io.axi.aw.size  := Cat(false.B, io.cpu.size)
              io.axi.aw.valid := true.B
              io.axi.w.data   := io.cpu.wdata
              io.axi.w.strb   := wstrb_gen
              io.axi.w.valid  := true.B
              status          := s_write
            }.otherwise {
              io.axi.ar.addr := io.cpu.addr(31, 0)
              io.axi.ar.size := Cat(false.B, io.cpu.size)
              arvalid        := true.B
              status         := s_read
            }
          }
        }.otherwise {
          status := s_idle
        }
      }
    }
  }
}
