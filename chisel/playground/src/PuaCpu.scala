import chisel3._
import chisel3.util._
import cache._
import cpu._
import cpu.defines._

class PuaCpu extends Module {
  implicit val cpuConfig = new CpuConfig()
  val io = IO(new Bundle {
    val ext_int = Input(new ExtInterrupt())
    val axi     = new AXI()
    val debug   = new DEBUG()
    val mmu_debug = Output(new MMU_DEBUG())
  })
  val core  = Module(new Core())
  val cache = Module(new Cache())

  core.io.inst <> cache.io.inst
  core.io.data <> cache.io.data

  io.ext_int <> core.io.ext_int
  io.debug <> core.io.debug
  io.mmu_debug := cache.io.debug
  io.mmu_debug.immu_state := core.io.mmu_debug.immu_state
  io.mmu_debug.dmmu_state := core.io.mmu_debug.dmmu_state
  io.mmu_debug.req_ptw := core.io.mmu_debug.req_ptw
  io.mmu_debug.choose_icache := core.io.mmu_debug.choose_icache
  io.axi <> cache.io.axi
}
