package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester

class MemoryBankTester(dut: MemoryBank) extends PeekPokeTester(dut) {

  // Test constants
  val range = 1 << dut.getAddrW

  // Writing to every memory location its address
  // and reading it back at next clock cycle
  println("[MemoryBankTester] Simple write-and-read-back test")
  for (i <- 0 until range) {
    poke(dut.io.wrAddr, i.U)
    poke(dut.io.wrData, (i - range/2).S)
    poke(dut.io.wrEn, true.B)
    step(1)
    poke(dut.io.wrEn, false.B)

    poke(dut.io.rdAddr, i.U)
    step(1)
    expect(dut.io.rdData, (i - range/2).S)
  }
}
