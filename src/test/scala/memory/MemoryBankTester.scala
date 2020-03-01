package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.math.pow

class MemoryBankTester(dut: MemoryBank) extends PeekPokeTester(dut) {

  // Writing to every memory location its address
  // and reading it back at next clock cycle
  var memOut = 0
  for (i <- 0 until pow(2, dut.getAddrW).toInt) {
    poke(dut.io.addr, i.U)
    poke(dut.io.wrData, i.U)
    poke(dut.io.RdWrN, false.B)
    step(1)
    expect(dut.io.rdData, i.U)
  }
}
