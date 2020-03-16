package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester

class LoadUnitTester(dut: LoadUnit) extends PeekPokeTester(dut) {

  val maxAddress = 1 << dut.getAddrW
  val n = dut.getN

  poke(dut.io.en, true.B)
  for (i <- 0 until 2*maxAddress by n) {
    expect(dut.io.addrA, i % maxAddress)
    expect(dut.io.addrB, i % maxAddress)
    expect(dut.io.auEn, i > 0)
    expect(dut.io.auClr, (i % maxAddress) == n)
    step(1)
  }
}