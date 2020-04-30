package arithmetic

import chisel3._

class ArithmeticGrid extends Module {
  val io = IO(new Bundle() {
    val opA = Input(Vec(gridSize, baseType))
    val opB = Input(Vec(gridSize, baseType))
    val en  = Input(Bool())
    val clr = Input(Bool())
    val mac = Output(Vec(gridSize, accuType))
    val vld = Output(Bool())
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

  // Signaling if accumulators are holding valid data
  // which is right before clearing them
  val len = ArithmeticUnit.getDelay - 1
  val clrDelay = Reg(Vec(len, Bool()))
  for (i <- len-1 to 1 by -1)
    clrDelay(i) := clrDelay(i-1)
  clrDelay(0) := io.clr
  io.vld := clrDelay(len-1)

  // Helper functions
  def getN = gridSize
  def getInputW = baseType.getWidth
  def getDelay = ArithmeticUnit.getDelay
}
