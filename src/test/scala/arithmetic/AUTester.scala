package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class AUTester(dut: ArithmeticUnit) extends PeekPokeTester(dut) {

  // Creating 1024 random 8-bit values
  var inputsA = new Array[Int](1024)
  var inputsB = new Array[Int](1024)
  for (i <- 0 until 1023) {
    inputsA(i) = Random.nextInt(256)
    inputsB(i) = Random.nextInt(256)
  }


  // Testing without clear
  // ---------------------
  // Feeding them into the Arithmetic Unit
  // and expecting accumulated values after
  // 3 cycles of delay
  var accu = 0
  poke(dut.io.clear, false.B)
  for (i <- 0 until 1024+3) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).U)
      poke(dut.io.b, inputsB(i).U)
    }

    // Testing the output
    // The output has a pipeline delay of 3 clock cycles
    if (i >= 3) {
      expect(dut.io.mac, accu + inputsA(i-3) * inputsB(i-3))
      accu += inputsA(i-3) * inputsB(i-3)
    }

    // Stepping forward one clock cycle
    step(1)
  }

  // Testing with clear
  // ------------------
  // Feeding them into the Arithmetic Unit
  // and expecting accumulated values after
  // 3 cycles of delay
  accu = 0
  for (i <- 0 until 1024+3) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).U)
      poke(dut.io.b, inputsB(i).U)
    }

    // Clearing after every 128 inputs
    if (i % 128 == 0) {
      poke(dut.io.clear, true.B)
    } else {
      poke(dut.io.clear, false.B)
    }

    // Testing the output
    // The output has a pipeline delay of 3 clock cycles
    if (i >= 3) {
      if ((i-3) % 128 == 0) accu = inputsA(i-3) * inputsB(i-3)
      else                  accu = accu + inputsA(i-3) * inputsB(i-3)
      expect(dut.io.mac, accu)
    }

    // Stepping forward one clock cycle
    step(1)
  }
}
