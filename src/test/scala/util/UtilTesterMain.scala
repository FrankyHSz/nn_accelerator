package util

import chisel3._
import chisel3.iotesters

object UtilTesterMain extends App {

  println("[UtilTesterMain] Testing non-pipelined barrel shifter, data type is UInt<8>")
  iotesters.Driver.execute(args, () => new BarrelShifter(n = 8, data = UInt(8.W), pipelined = false)) {
    c => new BarrelShifterTester[UInt](c)
  }

  println("[UtilTesterMain] Testing pipelined barrel shifter, data type is UInt<16>")
  iotesters.Driver.execute(args, () => new BarrelShifter(n = 8, data = UInt(16.W), pipelined = true)) {
    c => new BarrelShifterTester[UInt](c)
  }
}
