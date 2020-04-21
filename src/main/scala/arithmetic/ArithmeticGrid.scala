package arithmetic

import chisel3._

class ArithmeticGrid extends Module {
  val io = IO(new Bundle() {
    val opA = Input(Vec(gridSize, baseType))
    val opB = Input(Vec(gridSize, baseType))
    val en  = Input(Bool())
    val clr = Input(Bool())
    val mac = Output(Vec(gridSize, accuType))
  })

  // Generating arithmetic units
  val arithmeticUnits = VecInit(Seq.fill(gridSize) {
    Module(new ArithmeticUnit).io
  })

  // Connecting arithmetic units
  for (i <- 0 until gridSize) {
    arithmeticUnits(i).a   := io.opA(i)
    arithmeticUnits(i).b   := io.opB(i)
    arithmeticUnits(i).en  := io.en
    arithmeticUnits(i).clr := io.clr
    io.mac(i) := arithmeticUnits(i).mac
  }

  // Helper functions
  def getN = gridSize
  def getInputW = baseType.getWidth
  def getDelay = ArithmeticUnit.getDelay
}
