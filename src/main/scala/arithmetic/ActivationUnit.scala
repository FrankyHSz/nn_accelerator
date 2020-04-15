package arithmetic

import chisel3._

class ActivationUnit(inW: Int, outW: Int) extends Module {
  val io = IO(new Bundle() {
    val in  = Input(SInt(inW.W))
    val sel = Input(Bool())
    val out = Output(SInt(outW.W))
  })

  // Propagating the selected activation
  io.out := Mux(
    io.sel,
    ReLU(io.in, outW),
    Sigmoid(io.in, outW)
  )

  def getInW = inW
  def getOutW = outW
}
