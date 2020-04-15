package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random
import scala.math.abs
import _root_.util.NumericHelper._

class SigmoidTester(dut: Sigmoid) extends PeekPokeTester(dut) {

  // Number representation
  val fractionalBits = dut.getOutW - 1
  val scale = 1 << fractionalBits
  val range = dut.getRange

  // Test constants
  val testLength = 256
  val lsbValue = 1.0 / scale
  val errorThreshold = 2 * lsbValue


  // Generating random inputs
  var inputs = new Array[Double](testLength)
  for (i <- 0 until testLength) {
    inputs(i) = (Random.nextDouble - 0.5) * range
  }

  // Testing: comparing sigmoid values as doubles
  // --------------------------------------------
  println("[SigmoidTester] Testing sigmoid: comparing values as doubles")
  for (i <- 0 until testLength) {
    poke(dut.io.in, doubleToFixed(inputs(i), scale).S)
    step(1)
    val refOut = refSigmoid(inputs(i))
    val dutOut = fixedToDouble(peek(dut.io.out).toInt, scale)
    if (abs(refOut - dutOut) > errorThreshold) {
      expect(true.B, false.B)
      println("Numerical error (" + abs(refOut - dutOut) +
              ") is greater than threshold (" + errorThreshold + ")!")
    } else expect(true.B, true.B)
  }
}