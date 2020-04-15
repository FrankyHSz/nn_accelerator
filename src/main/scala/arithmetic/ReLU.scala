package arithmetic

import chisel3._

class ReLU(inW: Int, outW: Int) extends Module {
  val io = IO(new Bundle() {
    val in  = Input(SInt(inW.W))
    val out = Output(SInt(outW.W))
  })

  // Useful constants
  val scale = 1 << (outW - 1)
  val msb   = (3 + outW) - 1

  // Control signals
  val sign       = io.in(inW-1)
  val saturation = io.in >= scale.S  // If in >= 1.0 then ReLU saturates
  val maxValue   = (scale - 1).S     // Max number representable is ~0.999 (for now)

  // ReLU is in-fact saturation logic for bounds [0,1)
  io.out := RegNext(Mux(sign, 0.S, Mux(saturation, maxValue, io.in)))
}

object ReLU {
  def apply(in: SInt, outW: Int): SInt = {
    val module = Module(new ReLU(in.getWidth, outW))
    module.io.in := in
    module.io.out
  }
}
