package cache

import chisel3._
import chisel3.util._
import cpu.defines._

class CacheAXIInterface extends Module {
  val io = IO(new Bundle {
    val icache = Flipped(new ICache_AXIInterface())
    val dcache = Flipped(new DCache_AXIInterface())
    val axi    = new AXI()
  })

  // pass-through aw {
  io.axi.aw.bits.id    := 1.U
  io.axi.aw.bits.addr  := io.dcache.aw.bits.addr
  io.axi.aw.bits.len   := io.dcache.aw.bits.len
  io.axi.aw.bits.size  := io.dcache.aw.bits.size
  io.axi.aw.valid      := io.dcache.aw.valid
  io.axi.aw.bits.burst := 1.U
  io.axi.aw.bits.prot  := 0.U
  io.axi.aw.bits.cache := 0.U
  io.axi.aw.bits.lock  := 0.U
  io.dcache.aw.ready   := io.axi.aw.ready
  // pass-through aw }

  // pass-through w {
  io.axi.w.bits.id   := 1.U
  io.axi.w.bits.data := io.dcache.w.bits.data
  io.axi.w.bits.strb := io.dcache.w.bits.strb
  io.axi.w.bits.last := io.dcache.w.bits.last
  io.axi.w.valid     := io.dcache.w.valid
  io.dcache.w.ready  := io.axi.w.ready
  // pass-through aw }

  // pass-through b {
  io.dcache.b.bits.id   := io.axi.b.bits.id
  io.dcache.b.valid     := io.axi.b.valid
  io.dcache.b.bits.resp := io.axi.b.bits.resp
  io.axi.b.ready        := io.dcache.b.ready
  // pass-through b }

  // mux ar {
  // we need to lock ar to avoid signals change during handshake
  val ar_sel_lock = RegInit(false.B)
  val ar_sel_val  = RegInit(false.B)
  val ar_sel      = Mux(ar_sel_lock, ar_sel_val, !io.icache.ar.valid && io.dcache.ar.valid)

  when(io.axi.ar.valid) {
    when(io.axi.ar.ready) {
      ar_sel_lock := false.B
    }.otherwise {
      ar_sel_lock := true.B
      ar_sel_val  := ar_sel
    }
  }

  io.axi.ar.bits.id    := Cat(0.U(3.W), ar_sel)
  io.axi.ar.bits.addr  := Mux(ar_sel, io.dcache.ar.bits.addr, io.icache.ar.bits.addr)
  io.axi.ar.bits.len   := Mux(ar_sel, io.dcache.ar.bits.len, io.icache.ar.bits.len)
  io.axi.ar.bits.size  := Mux(ar_sel, io.dcache.ar.bits.size, io.icache.ar.bits.size)
  io.axi.ar.valid      := Mux(ar_sel, io.dcache.ar.valid, io.icache.ar.valid)
  io.axi.ar.bits.burst := 1.U
  io.axi.ar.bits.prot  := 0.U
  io.axi.ar.bits.cache := 0.U
  io.axi.ar.bits.lock  := 0.U
  io.icache.ar.ready   := !ar_sel && io.axi.ar.ready
  io.dcache.ar.ready   := ar_sel && io.axi.ar.ready
  // mux ar }

  // mux r based on rid {
  val r_sel = io.axi.r.bits.id(0)
  io.icache.r.bits.id   := io.axi.r.bits.id
  io.icache.r.bits.data := io.axi.r.bits.data
  io.icache.r.bits.resp := io.axi.r.bits.resp
  io.icache.r.bits.last := io.axi.r.bits.last
  io.icache.r.valid     := !r_sel && io.axi.r.valid
  io.dcache.r.bits.id   := io.axi.r.bits.id
  io.dcache.r.bits.data := io.axi.r.bits.data
  io.dcache.r.bits.resp := io.axi.r.bits.resp
  io.dcache.r.bits.last := io.axi.r.bits.last
  io.dcache.r.valid     := r_sel && io.axi.r.valid
  io.axi.r.ready        := Mux(r_sel, io.dcache.r.ready, io.icache.r.ready)
  // mux r based on rid }
}
