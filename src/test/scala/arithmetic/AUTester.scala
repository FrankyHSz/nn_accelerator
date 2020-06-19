package arithmetic

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class AUTester(dut: ArithmeticUnit) extends PeekPokeTester(dut) {

  // Test constants
  val range = 1 << dut.getInputW

  // Creating 1024 random values
  var inputsA = new Array[Int](1024)
  var inputsB = new Array[Int](1024)
  for (i <- 0 until 1024) {
    inputsA(i) = Random.nextInt(range) - range/2
    inputsB(i) = Random.nextInt(range) - range/2
  }


  // ---------
  // MAC tests
  // ---------
  poke(dut.io.pooling, false.B)
  poke(dut.io.maxPool, false.B)

  // Testing MAC without clear
  // -------------------------
  println("[AUTester] Testing MAC without clear")
  // Feeding random values into the Arithmetic Unit and
  // expecting accumulated values after pipeline delay
  var accu = 0
  poke(dut.io.clr, false.B)
  poke(dut.io.en, true.B)
  for (i <- 0 until 1024+dut.getDelay) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).S)
      poke(dut.io.b, inputsB(i).S)
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

  // Testing MAC with clear
  // ----------------------
  println("[AUTester] Testing MAC with clear")
  // Feeding random values into the Arithmetic Unit and
  // expecting accumulated values after pipeline delay
  // Accumulator is cleared after every 128th input
  accu = 0
  poke(dut.io.en, true.B)
  for (i <- 0 until 1024+dut.getDelay) {

    // Providing new input values
    if (i < 1024) {
      poke(dut.io.a, inputsA(i).S)
      poke(dut.io.b, inputsB(i).S)
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

  // Testing MAC with enable
  // -----------------------
  println("[AUTester] Testing MAC with enable")
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
      poke(dut.io.a, inputsA(i).S)
      poke(dut.io.b, inputsB(i).S)
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


  // -------------
  // Pooling tests
  // -------------
  val numOfPoolTests = 20  // Per pooling type, per pool size
  poke(dut.io.en, true.B)
  poke(dut.io.pooling, true.B)

  // Testing max. pooling
  // --------------------
  println("[AUTester] Testing max. pooling")
  // Presenting multiple pools of 4-9-16-25 elements
  // and expecting the maximum value as output at the
  // end of each pool.
  // Clear pulses are used to separate pools.
  // Enable is always active.
  poke(dut.io.maxPool, true.B)  // Max./avg. pooling
  poke(dut.io.b, 0.S)           // Unused input

  var clear = false
  var refOut = 0
  for (poolSize <- Array(4, 9, 16, 25)) {
    for (t <- 0 until numOfPoolTests*poolSize+dut.getDelay) {

      // Sending a clear pulse
      // alongside the first element of pool
      clear = (t % poolSize) == 0
      poke(dut.io.clr, clear.B)

      // Driving input port with small pause
      // between different pool sizes
      if (t < numOfPoolTests*poolSize)
        poke(dut.io.a, inputsA(t).S)

      // Testing output
      if (t >= dut.getDelay) {
        val delayedInput = inputsA(t-dut.getDelay)
        val delayedClear = (t-dut.getDelay) % poolSize == 0

        // First element of pool will be temporally the max
        // following elements will be temporal or true maxes
        refOut = if (delayedClear || delayedInput > refOut) delayedInput else refOut
        expect(dut.io.mac, refOut)
      }

      step(1)
    }
  }

  // Testing average pooling
  // -----------------------
  println("[AUTester] Testing average pooling")
  // Presenting multiple pools of 4-9-16-25 elements
  // and expecting the summed value (unscaled average)
  // as output at the end of each pool.
  // Clear pulses are used to separate pools.
  // Enable is always active.
  poke(dut.io.maxPool, false.B)  // Max./avg. pooling
  poke(dut.io.b, 0.S)            // Unused input

  clear = false
  refOut = 0
  for (poolSize <- Array(4, 9, 16, 25)) {
    for (t <- 0 until numOfPoolTests*poolSize+dut.getDelay) {

      // Sending a clear pulse
      // alongside the first element of pool
      clear = (t % poolSize) == 0
      poke(dut.io.clr, clear.B)

      // Driving input port with small pause
      // between different pool sizes
      if (t < numOfPoolTests*poolSize)
        poke(dut.io.a, inputsA(t).S)

      // Testing output
      if (t >= dut.getDelay) {
        val delayedInput = inputsA(t-dut.getDelay)
        val delayedClear = (t-dut.getDelay) % poolSize == 0

        // First element of pool will be temporally the average
        // following elements will be added to the unscaled avg.
        refOut = if (delayedClear) delayedInput else refOut + delayedInput
        expect(dut.io.mac, refOut)
      }

      step(1)
    }
  }

  // Testing max. pooling with enable
  // --------------------------------
  println("[AUTester] Testing max. pooling with enable")
  // Presenting multiple pools of 4-9-16-25 elements
  // and expecting the maximum value as output at the
  // end of each pool.
  // Clear pulses are used to separate pools.
  // Enable is toggled after every pool.
  poke(dut.io.maxPool, true.B)  // Max./avg. pooling
  poke(dut.io.b, 0.S)           // Unused input

  enable = false
  var delayedEnable = false
  clear = false
  refOut = 0
  for (poolSize <- Array(4, 9, 16, 25)) {

    enable = false
    delayedEnable = false

    for (t <- 0 until numOfPoolTests*poolSize+dut.getDelay) {

      // Toggling enable at the beginning of every pool
      if ((t % poolSize) == 0) enable = !enable
      poke(dut.io.en, enable.B)

      // Sending a clear pulse
      // alongside the first element of pool
      clear = (t % poolSize) == 0
      poke(dut.io.clr, clear.B)

      // Driving input port with small pause
      // between different pool sizes
      if (t < numOfPoolTests*poolSize)
        poke(dut.io.a, inputsA(t).S)

      // Testing output
      if (t >= dut.getDelay) {
        val delayedInput = inputsA(t-dut.getDelay)
        val delayedClear = (t-dut.getDelay) % poolSize == 0
        if (delayedClear) delayedEnable = !delayedEnable

        // First element of pool will be temporally the max
        // following elements will be temporal or true maxes
        // if enable was active
        if (delayedEnable)
          refOut = if (delayedClear || delayedInput > refOut) delayedInput else refOut
        expect(dut.io.mac, refOut)
      }

      step(1)
    }
  }

  // Testing average pooling with enable
  // -----------------------------------
  println("[AUTester] Testing average pooling with enable")
  // Presenting multiple pools of 4-9-16-25 elements
  // and expecting the summed value (unscaled average)
  // as output at the end of each pool.
  // Clear pulses are used to separate pools.
  // Enable is toggled at the beginning of every pool.
  poke(dut.io.maxPool, false.B)  // Max./avg. pooling
  poke(dut.io.b, 0.S)            // Unused input

  clear = false
  refOut = 0
  for (poolSize <- Array(4, 9, 16, 25)) {

    enable = false
    delayedEnable = false

    for (t <- 0 until numOfPoolTests*poolSize+dut.getDelay) {

      // Toggling enable at the beginning of every pool
      if ((t % poolSize) == 0) enable = !enable
      poke(dut.io.en, enable.B)

      // Sending a clear pulse
      // alongside the first element of pool
      clear = (t % poolSize) == 0
      poke(dut.io.clr, clear.B)

      // Driving input port with small pause
      // between different pool sizes
      if (t < numOfPoolTests*poolSize)
        poke(dut.io.a, inputsA(t).S)

      // Testing output
      if (t >= dut.getDelay) {
        val delayedInput = inputsA(t-dut.getDelay)
        val delayedClear = (t-dut.getDelay) % poolSize == 0
        if (delayedClear) delayedEnable = !delayedEnable

        // First element of pool will be temporally the average
        // following elements will be added to the unscaled avg.
        // if enable was active
        if (delayedEnable)
          refOut = if (delayedClear) delayedInput else refOut + delayedInput
        expect(dut.io.mac, refOut)
      }

      step(1)
    }
  }
}
