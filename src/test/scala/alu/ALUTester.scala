package alu

import chisel3._
import chisel3.iotesters.PeekPokeTester

class ALUTester(dut: ALU) extends PeekPokeTester(dut) {
  poke(dut.io.a, 1.U)
  poke(dut.io.b, 1.U)
  step(1)
  expect(dut.io.c, 2)
}
