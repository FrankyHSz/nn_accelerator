package arithmetic

import chisel3._
import scala.math.exp

class Sigmoid extends Module {
  val io = IO(new Bundle() {
    val in  = Input(accuType)
    val out = Output(baseType)
  })

  // Input number is expected to have n integer bits and m fractional bits,
  // while output number is expected to have 1 integer bit and m fractional bits.
  // Because sigmoid is approximately saturated if (in < -4) or (in > +4), only
  // numbers with at max. 2+1 useful integer bits are considered.
  val range = 1 << (2 + getOutW)  // n = 3, m = outW-1
  val msb   = (2 + getOutW) - 1   // MSB index of "3.m"-format fixed point numbers

  // Creating the sigmoid table between -4 and +4
  val array = new Array[Int](range)
  for (i <- 0 until range) {

    // For lower half of table (input starts with 0, hence it is positive)
    // positive values are generated.
    // For upper half of table (input starts with 1, hence it is negative)
    // negative values are generated.
    val x = if (i < range/2) i.toFloat / scale
            else             (i - range).toFloat / scale

    // Sigmoid function
    val sigmoidFloat = 1 / (1 + exp(-x))

    // Scaling and converting back activation to be integer
    val sigmoidFixedPoint = (sigmoidFloat * scale).toInt
    array(i) = sigmoidFixedPoint
  }
  // The values in table are interpreted as signed fixed point numbers
  val table = VecInit(array.map(_.S(getOutW.W)))

  // Sigmoid value indexed by lower bits of input
  val sigmoidValue: SInt = RegNext(table(io.in(msb, 0)))

  // Saturation detection
  // - positive overflow: the input is positive and has 1s above msb bit position
  // - negative overflow: the input is negative and has 0s above msb bit position
  val sign = io.in(getInW-1)
  val upperBits = io.in(getInW-2, msb+1)
  val posOverflow = !sign && upperBits.orR()
  val negOverflow = sign && !upperBits.andR()
  val saturation  = posOverflow || negOverflow

  // Saturation floor and ceiling
  val maxValue = (1 << (getOutW-1)) - 1  // 01111...1 = 0.9999
  val minValue = 0                    // 00000...0 = 0.0

  // Assigning output
  io.out := Mux(
    saturation,                                                // If saturation is needed
    Mux(posOverflow, maxValue.S(getOutW.W), minValue.S(getOutW.W)),  // then assign ceiling or floor,
    sigmoidValue                                               // otherwise propagate table value
  )

  def getInW = accuType.getWidth
  def getOutW = baseType.getWidth
  def getDelay = Sigmoid.getDelay
  def getRange = Sigmoid.getRange
}

object Sigmoid {
  def apply(in: accuType_t): dataType_t = {
    val module = Module(new Sigmoid)
    module.io.in := in
    module.io.out
  }

  def getDelay = 1
  def getRange = 8  // -4 to +4
}
