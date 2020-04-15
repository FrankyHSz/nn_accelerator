package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester

import scala.util.Random
import _root_.util.NumericHelper._

import scala.math.abs

class ActivationUnitTester(dut: ActivationUnit) extends PeekPokeTester(dut) {

  // Number representation
  val fractionalBits = dut.getOutW - 1
  val scale = 1 << fractionalBits
  val range = getSigmoidRange

  // Test constants
  val testLength = 256
  val lsbValue = 1.0 / scale
  val errorThreshold = 2 * lsbValue

  // Generating random inputs
  var inputs = new Array[Double](testLength)
  for (i <- 0 until testLength) {
    inputs(i) = (Random.nextDouble - 0.5) * range
  }

  // Testing ReLU
  // ------------
  println("[ActivationUnitTester] Testing ReLU activation")
  poke(dut.io.sel, true.B)
  for (i <- 0 until testLength) {
    poke(dut.io.in, doubleToFixed(inputs(i), scale).S)
    step(1)
    val refOut = refReLU(inputs(i))
    val dutOut = fixedToDouble(peek(dut.io.out).toInt, scale)
    if (abs(refOut - dutOut) > errorThreshold) {
      expect(true.B, false.B)
      println("Numerical error (" + abs(refOut - dutOut) +
        ") is greater than threshold (" + errorThreshold + ")!")
      println("in: " + inputs(i) + ", refOut: " + refOut + ", dutOut: " + dutOut)
    } else expect(true.B, true.B)
  }

  // Testing switching
  // -----------------
  println("[ActivationUnitTester] Testing switching between activations")
  var sel = true
  for (i <- 0 until testLength) {
    sel = !sel
    poke(dut.io.sel, sel.B)
    poke(dut.io.in, doubleToFixed(inputs(i), scale).S)

    step(1)

    val refOut = if (sel) refReLU(inputs(i)) else refSigmoid(inputs(i))
    val dutOut = fixedToDouble(peek(dut.io.out).toInt, scale)

    if (abs(refOut - dutOut) > errorThreshold) {
      expect(true.B, false.B)
      println("Numerical error (" + abs(refOut - dutOut) +
        ") is greater than threshold (" + errorThreshold + ")!")
      println("Sel: " + sel + ", refOut: " + refOut + ", dutOut: " + dutOut)
    } else expect(true.B, true.B)
  }
}
