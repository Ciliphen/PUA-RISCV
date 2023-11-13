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
  io.axi.aw.id       := io.dcache.aw.id
  io.axi.aw.addr     := io.dcache.aw.addr
  io.axi.aw.len      := io.dcache.aw.len
  io.axi.aw.size     := io.dcache.aw.size
  io.axi.aw.burst    := io.dcache.aw.burst
  io.axi.aw.valid    := io.dcache.aw.valid
  io.axi.aw.prot     := io.dcache.aw.prot
  io.axi.aw.cache    := io.dcache.aw.cache
  io.axi.aw.lock     := io.dcache.aw.lock
  io.dcache.aw.ready := io.axi.aw.ready
  // pass-through aw }

  // pass-through w {
  io.axi.w.id       := io.dcache.w.id
  io.axi.w.data     := io.dcache.w.data
  io.axi.w.strb     := io.dcache.w.strb
  io.axi.w.last     := io.dcache.w.last
  io.axi.w.valid    := io.dcache.w.valid
  io.dcache.w.ready := io.axi.w.ready
  // pass-through aw }

  // pass-through b {
  io.dcache.b.id    := io.axi.b.id
  io.dcache.b.valid := io.axi.b.valid
  io.dcache.b.resp  := io.axi.b.resp
  io.axi.b.ready    := io.dcache.b.ready
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
  io.axi.ar.id       := Cat(0.U(3.W), ar_sel)
  io.axi.ar.addr     := Mux(ar_sel, io.dcache.ar.addr, io.icache.ar.addr)
  io.axi.ar.len      := Mux(ar_sel, io.dcache.ar.len, io.icache.ar.len)
  io.axi.ar.size     := Mux(ar_sel, io.dcache.ar.size, io.icache.ar.size)
  io.axi.ar.burst    := Mux(ar_sel, io.dcache.ar.burst, io.icache.ar.burst)
  io.axi.ar.valid    := Mux(ar_sel, io.dcache.ar.valid, io.icache.ar.valid)
  io.axi.ar.prot     := Mux(ar_sel, io.dcache.ar.prot, io.icache.ar.prot)
  io.axi.ar.cache    := Mux(ar_sel, io.dcache.ar.cache, io.icache.ar.cache)
  io.axi.ar.lock     := Mux(ar_sel, io.dcache.ar.lock, io.icache.ar.lock)
  io.icache.ar.ready := !ar_sel && io.axi.ar.ready
  io.dcache.ar.ready := ar_sel && io.axi.ar.ready
  // mux ar }

  // mux r based on rid {
  val r_sel = io.axi.r.id(0)
  io.icache.r.id    := io.axi.r.id
  io.icache.r.data  := io.axi.r.data
  io.icache.r.resp  := io.axi.r.resp
  io.icache.r.last  := io.axi.r.last
  io.icache.r.valid := !r_sel && io.axi.r.valid
  io.dcache.r.id    := io.axi.r.id
  io.dcache.r.data  := io.axi.r.data
  io.dcache.r.resp  := io.axi.r.resp
  io.dcache.r.last  := io.axi.r.last
  io.dcache.r.valid := r_sel && io.axi.r.valid
  io.axi.r.ready    := Mux(r_sel, io.dcache.r.ready, io.icache.r.ready)
  // mux r based on rid }
}
