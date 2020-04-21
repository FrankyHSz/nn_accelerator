package arithmetic

import chisel3._

class ActivationGrid extends Module {
  val io = IO(new Bundle() {
    val in  = Input(Vec(gridSize, accuType))
    val sel = Input(Bool())
    val out = Output(Vec(gridSize, baseType))
  })

  // Generating activation units
  val activationUnits = VecInit(Seq.fill(gridSize) {
    Module(new ActivationUnit).io
  })

  // Connecting activation units
  for (i <- 0 until gridSize) {
    activationUnits(i).in  := io.in(i)
    activationUnits(i).sel := io.sel
    io.out(i) := activationUnits(i).out
  }


  // Helper functions
  def getN = gridSize
  def getOutW = baseType.getWidth
}
