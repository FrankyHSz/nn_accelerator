package arithmetic

import chisel3._

class ReLU extends Module {
  val io = IO(new Bundle() {
    val in  = Input(accuType)
    val out = Output(baseType)
  })

  // Useful constants
  val scale = 1 << (getOutW - 1)
  val msb   = (3 + getOutW) - 1

  // Control signals
  val sign       = io.in(getInW-1)
  val saturation = io.in >= scale.S  // If in >= 1.0 then ReLU saturates
  val maxValue   = (scale - 1).S     // Max number representable is ~0.999 (for now)

  // ReLU is in-fact saturation logic for bounds [0,1)
  io.out := RegNext(Mux(sign, 0.S, Mux(saturation, maxValue, io.in)))

  def getInW = accuType.getWidth
  def getOutW = baseType.getWidth
}

object ReLU {
  def apply(in: accuType_t): dataType_t = {
    val module = Module(new ReLU)
    module.io.in := in
    module.io.out
  }
}
