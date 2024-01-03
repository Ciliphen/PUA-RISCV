package cache.mmu

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.TLBConfig

class TlbL2_TlbL1 extends Bundle {
  val page_fault = Output(Bool())
}

class TlbL2 extends Module with HasTlbConst {
  val io = IO(new Bundle {
    val itlb = new TlbL2_TlbL1()
    val dtlb = new TlbL2_TlbL1()
  })

  // tlb l2
  val tlb = RegInit(VecInit(Seq.fill(nindex)(VecInit(Seq.fill(nway)(0.U.asTypeOf(tlbBundle))))))

}
