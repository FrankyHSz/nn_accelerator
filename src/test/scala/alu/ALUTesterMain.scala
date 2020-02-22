package alu

import chisel3._

object ALUTesterMain extends App {
  iotesters.Driver.execute(args, () => new ALU) {
    c => new ALUTester(c)
  }
}
