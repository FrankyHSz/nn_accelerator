package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class AUTester(dut: ArithmeticUnit) extends PeekPokeTester(dut) {

  // Creating 1024 random values
  var inputsA = new Array[Int](1024)
  var inputsB = new Array[Int](1024)
  for (i <- 0 until 1023) {
    inputsA(i) = Random.nextInt(1 << dut.getInputW)
    inputsB(i) = Random.nextInt(1 << dut.getInputW)
  }


  // Testing without clear
  // ---------------------
  println("[AUTester] Testing without clear")
  // Feeding random values into the Arithmetic Unit and
  // expecting accumulated values after pipeline delay
  var accu = 0
  poke(dut.io.clr, false.B)
  poke(dut.io.en, true.B)
  for (i <- 0 until 1024+dut.getDelay) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).U)
      poke(dut.io.b, inputsB(i).U)
    }

    // Testing the output
    // The output has a pipeline delay of 3 clock cycles
    if (i >= dut.getDelay) {
      expect(dut.io.mac, accu + inputsA(i-dut.getDelay) * inputsB(i-dut.getDelay))
      accu += inputsA(i-dut.getDelay) * inputsB(i-dut.getDelay)
    }

    // Stepping forward one clock cycle
    step(1)
  }

  // Testing with clear
  // ------------------
  println("[AUTester] Testing with clear")
  // Feeding random values into the Arithmetic Unit and
  // expecting accumulated values after pipeline delay
  // Accumulator is cleared after every 128th input
  accu = 0
  poke(dut.io.en, true.B)
  for (i <- 0 until 1024+dut.getDelay) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).U)
      poke(dut.io.b, inputsB(i).U)
    }

    // Clearing after every 128 inputs
    if (i % 128 == 0) {
      poke(dut.io.clr, true.B)
    } else {
      poke(dut.io.clr, false.B)
    }

    // Testing the output
    // The output has a pipeline delay of 3 clock cycles
    if (i >= dut.getDelay) {
      if ((i-3) % 128 == 0) accu = inputsA(i-dut.getDelay) * inputsB(i-dut.getDelay)
      else                  accu = accu + inputsA(i-dut.getDelay) * inputsB(i-dut.getDelay)
      expect(dut.io.mac, accu)
    }

    // Stepping forward one clock cycle
    step(1)
  }

  // Testing with enable
  // -------------------
  println("[AUTester] Testing with enable")
  // Feeding random values into the Arithmetic
  // Unit and expecting accumulated values after
  // 3 cycles of delay
  // Accumulator's enable is toggled after every
  // 128th input
  accu = 0
  var enable = false
  for (i <- 0 until 1024+dut.getDelay) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).U)
      poke(dut.io.b, inputsB(i).U)
    }

    // Clearing content of accumulator only at first cycle
    if (i == 0) {
      poke(dut.io.clr, true.B)
    } else {
      poke(dut.io.clr, false.B)
    }

    // Toggling enable after every 128 inputs
    if (i % 128 == 0) {
      enable = !enable
      poke(dut.io.en, enable)
    }

    // Testing the output
    if (i >= dut.getDelay) {
      if ((i-dut.getDelay) == 0)
        accu = inputsA(0) * inputsB(0)
      else if ((i-dut.getDelay)/128 % 2 == 0)
        accu = accu + inputsA(i-dut.getDelay) * inputsB(i-dut.getDelay)
      // else : Accumulator is not enabled so it holds its value
      expect(dut.io.mac, accu)
    }

    // Stepping forward one clock cycle
    step(1)
  }
}
