package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random
import scala.math.abs
import _root_.util.NumericHelper._

class ActivationGridTester(dut: ActivationGrid) extends PeekPokeTester(dut) {

  // Number representation
  val fractionalBits = dut.getOutW - 1
  val scale = 1 << fractionalBits
  val range = getSigmoidRange

  // Test constants
  val testLength = 256
  val lsbValue = 1.0 / scale
  val errorThreshold = 2 * lsbValue
  val n = dut.getN

  // Generating random inputs
  var inputs = new Array[Double](testLength*n)
  for (i <- 0 until testLength*n) {
    inputs(i) = (Random.nextDouble - 0.5) * range
  }

  // Testing with switching
  // ----------------------
  println("[ActivationGridTester] Testing with switching between activations")
  var sel = true
  for (i <- 0 until testLength) {

    // Switching activation function
    sel = !sel

    // Driving inputs
    for (port <- 0 until n) {
      val dataIdx = i * n + port
      poke(dut.io.sel, sel.B)
      poke(dut.io.in(port), doubleToFixed(inputs(dataIdx), scale).S)
    }

    step(1)

    // Checking outputs
    for (port <- 0 until n) {
      val dataIdx = i * n + port
      val refOut = if (sel) refReLU(inputs(dataIdx)) else refSigmoid(inputs(dataIdx))
      val dutOut = fixedToDouble(peek(dut.io.out)(port).toInt, scale)

      if (abs(refOut - dutOut) > errorThreshold) {
        expect(true.B, false.B)
        println("Numerical error (" + abs(refOut - dutOut) +
          ") is greater than threshold (" + errorThreshold + ")!")
        println("Sel: " + sel + ", refOut: " + refOut + ", dutOut: " + dutOut)
      } else expect(true.B, true.B)
    }

  }
}
