package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester

class MemoryBankTester(dut: MemoryBank) extends PeekPokeTester(dut) {

  // Writing to every memory location its address
  // and reading it back at next clock cycle
  println("[MemoryBankTester] Simple write-and-read-back test")
  for (i <- 0 until (1 << dut.getAddrW)) {
    poke(dut.io.wrAddr, i.U)
    poke(dut.io.wrData, i.U)
    poke(dut.io.wrEn, true.B)
    step(1)
    poke(dut.io.wrEn, false.B)

    poke(dut.io.rdAddr, i.U)
    step(1)
    expect(dut.io.rdData, i.U)
  }
}
