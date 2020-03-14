package memory

import chisel3.iotesters.PeekPokeTester

class LoadUnitTester(dut: LoadUnit) extends PeekPokeTester(dut) {

  val maxAddress = 1 << dut.getAddrW

  for (i <- 0 until 2*maxAddress) {
    expect(dut.io.addrA, i % maxAddress)
    expect(dut.io.addrB, i % maxAddress)
    expect(dut.io.en, true)
    expect(dut.io.clr, i == 0)
    step(1)
  }

}
