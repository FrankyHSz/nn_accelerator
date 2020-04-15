package arithmetic

import chisel3._

class ArithmeticGrid(n: Int, inW: Int) extends Module {

  // Internal parameters and useful constants
  val accuExt = ArithmeticGrid.getAccuExt
  val outW = 2 * inW + accuExt

  val io = IO(new Bundle() {
    val opA = Input(Vec(n, SInt(inW.W)))
    val opB = Input(Vec(n, SInt(inW.W)))
    val en  = Input(Bool())
    val clr = Input(Bool())
    val mac = Output(Vec(n, SInt(outW.W)))
  })

  // Generating arithmetic units
  val arithmeticUnits = VecInit(Seq.fill(n) {
    Module(new ArithmeticUnit(inputW = inW, accuW = outW)).io
  })

  // Connecting arithmetic units
  for (i <- 0 until n) {
    arithmeticUnits(i).a   := io.opA(i)
    arithmeticUnits(i).b   := io.opB(i)
    arithmeticUnits(i).en  := io.en
    arithmeticUnits(i).clr := io.clr
    io.mac(i) := arithmeticUnits(i).mac
  }

  // Helper functions
  def getN = n
  def getInputW = inW
  def getDelay = ArithmeticUnit.getDelay
}

object ArithmeticGrid {
  def getAccuExt = 16
}
