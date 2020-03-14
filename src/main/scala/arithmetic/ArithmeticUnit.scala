package arithmetic

import chisel3._

class ArithmeticUnit(inputW: Int, accuW: Int) extends Module {
  val io = IO(new Bundle() {
    val a   = Input(UInt(inputW.W))
    val b   = Input(UInt(inputW.W))
    val en  = Input(Bool())
    val clr = Input(Bool())
    val mac = Output(UInt(accuW.W))
  })

  // Registers to hold data
  val aReg = RegInit(0.U(inputW.W))
  val bReg = RegInit(0.U(inputW.W))
  val mul  = RegInit(0.U((2*inputW).W))
  val accu = RegInit(0.U(accuW.W))

  // Delay lines for control signals
  val enDelay  = Reg(Vec(2, Bool()))
  val clrDelay = Reg(Vec(2, Bool()))

  // First stage
  aReg := io.a
  bReg := io.b
  enDelay(0) := io.en
  clrDelay(0) := io.clr

  // Second stage
  mul  := aReg * bReg
  enDelay(1) := enDelay(0)
  clrDelay(1) := clrDelay(0)

  // Third stage
  when (enDelay(1)) {
    accu := Mux(clrDelay(1), mul, accu + mul)
  }

  io.mac := accu

  def getInputW = inputW
  def getAccuW = accuW
}
