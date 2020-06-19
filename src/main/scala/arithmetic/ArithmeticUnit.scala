package arithmetic

import chisel3._

class ArithmeticUnit extends Module {
  val io = IO(new Bundle() {
    val a   = Input(baseType)
    val b   = Input(baseType)
    val en  = Input(Bool())
    val clr = Input(Bool())
    val pooling = Input(Bool())
    val maxPool = Input(Bool())
    val mac = Output(accuType)
  })

  // Registers to hold data
  val aReg = RegInit(0.S(getInputW.W))
  val bReg = RegInit(0.S(getInputW.W))
  val mul  = RegInit(0.S((2*getInputW).W))
  val accu = RegInit(0.S(getAccuW.W))

  // Delay lines for control signals
  val enDelay  = Reg(Vec(2, Bool()))
  val clrDelay = Reg(Vec(2, Bool()))
  val maxPoolDelay = Reg(Vec(2, Bool()))

  // First stage
  aReg := io.a
  bReg := io.b
  enDelay(0) := io.en
  clrDelay(0) := io.clr
  maxPoolDelay(0) := io.maxPool

  // Second stage
  mul  := Mux(RegNext(io.pooling), aReg, aReg * bReg)
  enDelay(1) := enDelay(0)
  clrDelay(1) := clrDelay(0)
  maxPoolDelay(1) := maxPoolDelay(0)

  // Third stage
  val loopback = Mux(clrDelay(1), 0.S, accu)
  val sum = loopback + mul
  val max = Mux(clrDelay(1), mul, Mux(loopback < mul, mul, loopback))
  when (enDelay(1)) {
    accu := Mux(maxPoolDelay(1), max, sum)
  }

  io.mac := accu

  def getInputW = baseType.getWidth
  def getAccuW = accuType.getWidth
  def getDelay = ArithmeticUnit.getDelay
}

object ArithmeticUnit {
  def getDelay = 3
}
