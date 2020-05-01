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
  poke(dut.io.sizeA, n.U)
  poke(dut.io.sizeB, n.U)
  step(1)
  for (t <- 1 until 2*maxAddress) {
    expect(dut.io.addrA, t % maxAddress)
    expect(dut.io.addrB, (t * n) % maxAddress)
    for (i <- 0 until n)
      expect(dut.io.auEn(i), true)
    expect(dut.io.auClr, ((t-1) % maxAddress) == 0)
    step(1)
  }

  // Resetting address registers
  poke(dut.io.en, false.B)
  step(1)

  // Testing addresses for convolution
  println("[LoadUnit] Testing addresses for convolution")

  // Testing for all supported kernel sizes
  print("Tested kernel sizes: ")
  for (kernelSize <- 3 to 8) {
    print(kernelSize + " ")
    poke(dut.io.en, true.B)
    poke(dut.io.mulConvN, false.B)
    poke(dut.io.sizeA, kernelSize.U)
    poke(dut.io.sizeB, n.U)
    step(1)
    for (t <- 1 until 2 * (n * (n-kernelSize))) {
      expect(dut.io.addrA, t % (kernelSize*kernelSize))
      expect(dut.io.addrB, ((t/(n-kernelSize))*n + t%(n-kernelSize)) % maxAddress)
      for (i <- 0 until n)
        expect(dut.io.auEn(i), (i < (n-kernelSize-1)))
      expect(dut.io.auClr, ((t-1) % (kernelSize*kernelSize)) == 0)
      step(1)
    }

    // Resetting address registers
    poke(dut.io.en, false.B)
    step(1)
  }
  print("\n")
}