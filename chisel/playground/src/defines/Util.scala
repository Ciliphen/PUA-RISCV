package cpu.defines

import chisel3._
import chisel3.util._

object HasExcInt {
  def apply(ex: ExceptionInfo) = {
    ex.exception.asUInt.orR || ex.interrupt.asUInt.orR
  }
}

object IsMret {
  def apply(info: Info) = {
    info.fusel === FuType.csr && info.op === CSROpType.mret
  }
}

object IsSret {
  def apply(info: Info) = {
    info.fusel === FuType.csr && info.op === CSROpType.sret
  }
}

object HasRet {
  def apply(info: Info) = {
    IsMret(info) || IsSret(info)
  }
}

object SignedExtend {
  def apply(a: UInt, len: Int) = {
    val aLen    = a.getWidth
    val signBit = a(aLen - 1)
    if (aLen >= len) a(len - 1, 0) else Cat(Fill(len - aLen, signBit), a)
  }
}

object ZeroExtend {
  def apply(a: UInt, len: Int) = {
    val aLen = a.getWidth
    if (aLen >= len) a(len - 1, 0) else Cat(0.U((len - aLen).W), a)
  }
}
object LookupTree {
  def apply[T <: Data](key: UInt, mapping: Iterable[(UInt, T)]): T =
    Mux1H(mapping.map(p => (p._1 === key, p._2)))
}

object LookupTreeDefault {
  def apply[T <: Data](key: UInt, default: T, mapping: Iterable[(UInt, T)]): T =
    MuxLookup(key, default)(mapping.toSeq)
}

object MaskData {
  def apply(oldData: UInt, newData: UInt, fullmask: UInt) = {
    require(oldData.getWidth == newData.getWidth)
    require(oldData.getWidth == fullmask.getWidth)
    (newData & fullmask) | (oldData & ~fullmask)
  }
}

object RegMap {
  def Unwritable = null
  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)) = (addr, (reg, wfn))
  def generate(
    mapping: Map[Int, (UInt, UInt => UInt)],
    raddr:   UInt,
    rdata:   UInt,
    waddr:   UInt,
    wen:     Bool,
    wdata:   UInt,
    wmask:   UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, w)) => (a.U, r, w) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, w) => (a, r) })
    chiselMapping.map {
      case (a, r, w) =>
        if (w != null) when(wen && waddr === a) { r := w(MaskData(r, wdata, wmask)) }
    }
  }
  def generate(
    mapping: Map[Int, (UInt, UInt => UInt)],
    addr:    UInt,
    rdata:   UInt,
    wen:     Bool,
    wdata:   UInt,
    wmask:   UInt
  ): Unit = generate(mapping, addr, rdata, addr, wen, wdata, wmask)
}

object MaskedRegMap extends CoreParameter {
  def Unwritable = null
  def NoSideEffect: UInt => UInt = (x => x)
  def WritableMask   = Fill(XLEN, true.B)
  def UnwritableMask = 0.U(XLEN.W)
  def apply(
    addr:  Int,
    reg:   UInt,
    wmask: UInt         = WritableMask,
    wfn:   UInt => UInt = (x => x),
    rmask: UInt         = WritableMask
  ) = (addr, (reg, wmask, wfn, rmask))
  def generate(
    mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)],
    raddr:   UInt,
    rdata:   UInt,
    waddr:   UInt,
    wen:     Bool,
    wdata:   UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) => (a.U, r, wm, w, rm) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, wm, w, rm) => (a, r & rm) })
    chiselMapping.map {
      case (a, r, wm, w, rm) =>
        if (w != null && wm != UnwritableMask) when(wen && waddr === a) { r := w(MaskData(r, wdata, wm)) }
    }
  }
  def isIllegalAddr(mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)], addr: UInt): Bool = {
    val illegalAddr   = Wire(Bool())
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) => (a.U, r, wm, w, rm) }
    illegalAddr := LookupTreeDefault(addr, true.B, chiselMapping.map { case (a, r, wm, w, rm) => (a, false.B) })
    illegalAddr
  }
  def generate(
    mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)],
    addr:    UInt,
    rdata:   UInt,
    wen:     Bool,
    wdata:   UInt
  ): Unit = generate(mapping, addr, rdata, addr, wen, wdata)
}
