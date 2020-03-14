package util

import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.log2Up
import scala.util.Random

class BarrelShifterTester[T <: Data](dut: BarrelShifter[T]) extends PeekPokeTester(dut) {

  val n = dut.getN
  val w = dut.getType.getWidth

  if (dut.getType.toString == "UInt<" + w + ">") {

    // Generating random inputs
    val input = Array.ofDim[UInt](n)
    for (i <- 0 until n) input(i) = Random.nextInt(1 << w).U(w.W)

    // "Connecting" the random inputs to the DUT
    for (i <- 0 until n) poke(dut.io.in(i).asUInt(), input(i))

    // Sweeping through every possible shift setting
    // Expecting properly shifted array
    for (offset <- 0 until n) {
      poke(dut.io.sh, offset.U)
      if (dut.isPipelined) step(log2Up(dut.getN))
      for (i <- 0 until n) {
        expect(dut.io.out(i).asUInt(), input((i + offset) % n))
      }
    }

  } else println("Tests are not implemented for this data type!")
}
