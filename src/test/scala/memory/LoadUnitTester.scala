package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester

class LoadUnitTester(dut: LoadUnit) extends PeekPokeTester(dut) {

  val maxAddress = 1 << dut.getAddrW
  val n = dut.getN

  // Testing addresses for matrix multiplication
  println("[LoadUnit] Testing addresses for matrix multiplication")
  poke(dut.io.en, true.B)
  poke(dut.io.mulConvN, true.B)
  poke(dut.io.widthA, n.U)
  poke(dut.io.widthB, n.U)
  poke(dut.io.heightA, n.U)
  poke(dut.io.heightB, n.U)
  step(1)
  for (t <- 0 until maxAddress) {
    expect(dut.io.addrA, t % maxAddress)
    expect(dut.io.addrB, (t * n) % maxAddress)
    for (i <- 0 until n)
      expect(dut.io.auEn(i), true)
    expect(dut.io.auClr, (t % maxAddress) == 0)
    step(1)
  }
  for (i <- 0 until n)
    expect(dut.io.auEn(i), false.B)
  expect(dut.io.done, true.B)
  step(1)
  poke(dut.io.en, false.B)
  for (i <- 0 until n)
    expect(dut.io.auEn(i), false.B)

  // Resetting address registers
  poke(dut.io.en, false.B)
  step(1)

  // Testing addresses for convolution
  println("[LoadUnit] Testing addresses for convolution")

  // Testing for all supported kernel sizes
  print("Tested kernel sizes: ")
  for (kernelSize <- 3 to 8) {
    var expB = -1
    print(kernelSize + " ")
    poke(dut.io.en, true.B)
    poke(dut.io.mulConvN, false.B)
    poke(dut.io.widthA, kernelSize.U)
    poke(dut.io.widthB, n.U)
    poke(dut.io.heightB, n.U)
    step(1)
    for (t <- 0 until ((n-kernelSize+1) * (kernelSize*kernelSize))) {

      // AddrA sweeps through the kernel continuously
      expect(dut.io.addrA, t % (kernelSize*kernelSize))

      // AddrB follows the first kernel in the row
      if (t != 0 && t%(kernelSize*kernelSize) == 0) {
        if ((expB+1)/n == n-1) {
          // If we are in the last row of image, we are done (starting again from 0)
          expB = 0
        } else {
          // If a kernel is done, do a "carriage return" and go up (kernelSize-2) rows
          expB = expB - kernelSize + 1 - (kernelSize-2) * n
        }
      } else {
        if ((expB+1)%n == kernelSize) {
          // If the next column would be out of the kernel, we jump to the beginning of next row
          expB += n - kernelSize + 1
        } else {
          // Otherwise just step to the next column
          expB += 1
        }
      }
      expB = expB % maxAddress
      expect(dut.io.addrB, expB)

      for (i <- 0 until n)
        expect(dut.io.auEn(i), (i < (n-kernelSize-1)))
      expect(dut.io.auClr, (t % (kernelSize*kernelSize)) == 0)
      step(1)
    }
    for (i <- 0 until n)
      expect(dut.io.auEn(i), false.B)
    expect(dut.io.done, true.B)
    step(1)
    poke(dut.io.en, false.B)
    for (i <- 0 until n)
      expect(dut.io.auEn(i), false.B)

    // Resetting address registers
    poke(dut.io.en, false.B)
    step(1)
  }
  print("\n")
}