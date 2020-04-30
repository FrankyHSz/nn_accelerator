package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class AGTester(dut: ArithmeticGrid) extends PeekPokeTester(dut) {

  // Useful constants
  val numberOfUnits = dut.getN
  val maxInputNumber = 1 << dut.getInputW
  val testLength = 1024
  val tenPercent = testLength / 10  // For a sloppy progressbar
  val onePercent = testLength / 100 // For a sloppy progressbar

  // Creating testLength random vectors with size of numberOfUnits
  val inputsA = Array.fill(numberOfUnits * testLength) { Random.nextInt(maxInputNumber) - maxInputNumber/2 }
  val inputsB = Array.fill(numberOfUnits * testLength) { Random.nextInt(maxInputNumber) - maxInputNumber/2 }

  // Testing without clear
  // ---------------------
  println("[AGTester] Testing without clear")
  // Feeding random values into the Arithmetic Grid and
  // expecting accumulated values after pipeline delay
  var accu = Array.fill(dut.getN) { 0 }
  poke(dut.io.clr, false.B)
  poke(dut.io.en, true.B)

  // Loop for clock cycles
  for (t <- 0 until testLength+dut.getDelay) {
    // Loop for vector ports / units
    for (k <- 0 until numberOfUnits) {

      val currentInputIdx = t * numberOfUnits + k
      val delayedInputIdx = (t-dut.getDelay) * numberOfUnits + k

      // Providing new input values
      if (t < testLength) {
        // println("Inputs for " + k + ": " + inputsA(currentInputIdx) + ", " + inputsB(currentInputIdx))
        poke(dut.io.opA(k), inputsA(currentInputIdx).S)
        poke(dut.io.opB(k), inputsB(currentInputIdx).S)
      }

      // Testing the output
      if (t >= dut.getDelay) {
        expect(dut.io.mac(k), accu(k) + inputsA(delayedInputIdx) * inputsB(delayedInputIdx))
        accu(k) += inputsA(delayedInputIdx) * inputsB(delayedInputIdx)
      }
    }
    expect(dut.io.vld, false)
    // Stepping forward one clock cycle
    step(1)

    // A sloppy progressbar
    if (t % tenPercent == 0) print("|")
    else if (t % onePercent == 0) print(".")
  }
  print("\n")


  // Testing with clear
  // ------------------
  println("[AGTester] Testing with clear")
  // Feeding random values into the Arithmetic Grid and
  // expecting accumulated values after pipeline delay
  // Accumulators are cleared after every 128th input
  accu = Array.fill(dut.getN) { 0 }
  poke(dut.io.en, true.B)

  // Loop for clock cycles
  for (t <- 0 until testLength+dut.getDelay) {

    // Clearing after every 128 inputs
    if (t % 128 == 0) {
      poke(dut.io.clr, true.B)
    } else {
      poke(dut.io.clr, false.B)
    }

    // Loop for vector ports / units
    for (k <- 0 until numberOfUnits) {

      val currentInputIdx = t * numberOfUnits + k
      val delayedInputIdx = (t-dut.getDelay) * numberOfUnits + k

      // Providing new input values
      if (t < testLength) {
        poke(dut.io.opA(k), inputsA(currentInputIdx).S)
        poke(dut.io.opB(k), inputsB(currentInputIdx).S)
      }

      // Testing the output
      if (t >= dut.getDelay) {
        if ((t-dut.getDelay) % 128 == 0) accu(k) = inputsA(delayedInputIdx) * inputsB(delayedInputIdx)
        else accu(k) += inputsA(delayedInputIdx) * inputsB(delayedInputIdx)
        expect(dut.io.mac(k), accu(k))
        expect(dut.io.vld, ((t-dut.getDelay)%128 == 127))
      }
    }
    if (t == 2) expect(dut.io.vld, true)
    // Stepping forward one clock cycle
    step(1)

    // A sloppy progressbar
    if (t % tenPercent == 0) print("|")
    else if (t % onePercent == 0) print(".")
  }
  print("\n")


  // Testing with enable
  // -------------------
  println("[AGTester] Testing with enable")
  // Feeding random values into the Arithmetic Grid and
  // expecting accumulated values after pipeline delay
  // Accumulators' enable is toggled after every 128th input
  accu = Array.fill(dut.getN) { 0 }
  var enable = false

  // Loop for clock cycles
  for (t <- 0 until testLength+dut.getDelay) {

    // Clearing content of accumulator only at first cycle
    if (t == 0) {
      poke(dut.io.clr, true.B)
    } else {
      poke(dut.io.clr, false.B)
    }

    // Toggling enable after every 128 inputs
    if (t % 128 == 0) {
      enable = !enable
      poke(dut.io.en, enable.B)
    }

    // Loop for vector ports / units
    for (k <- 0 until numberOfUnits) {

      val currentInputIdx = t * numberOfUnits + k
      val delayedInputIdx = (t-dut.getDelay) * numberOfUnits + k

      // Providing new input values
      if (t < testLength) {
        poke(dut.io.opA(k), inputsA(currentInputIdx).S)
        poke(dut.io.opB(k), inputsB(currentInputIdx).S)
      }

      // Testing the output
      if (t >= dut.getDelay) {
        if ((t - dut.getDelay) == 0)
          accu(k) = inputsA(k) * inputsB(k)
        else if ((t - dut.getDelay) / 128 % 2 == 0)
          accu(k) += inputsA(delayedInputIdx) * inputsB(delayedInputIdx)
        // else : Accumulator is not enabled so it holds its value
        expect(dut.io.mac(k), accu(k))
      }
    }
    // Stepping forward one clock cycle
    step(1)

    // A sloppy progressbar
    if (t % tenPercent == 0) print("|")
    else if (t % onePercent == 0) print(".")
  }
  print("\n")
}
