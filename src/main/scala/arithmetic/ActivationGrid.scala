package arithmetic

import chisel3._

class ActivationGrid(n: Int, inW: Int, outW: Int) extends Module {
  val io = IO(new Bundle() {
    val in  = Input(Vec(n, SInt(inW.W)))
    val sel = Input(Bool())
    val out = Output(Vec(n, SInt(outW.W)))
  })

  // Generating activation units
  val activationUnits = VecInit(Seq.fill(n) {
    Module(new ActivationUnit(inW = inW, outW = outW)).io
  })

  // Connecting activation units
  for (i <- 0 until n) {
    activationUnits(i).in  := io.in(i)
    activationUnits(i).sel := io.sel
    io.out(i) := activationUnits(i).out
  }


  // Helper functions
  def getN = n
  def getOutW = outW
}
