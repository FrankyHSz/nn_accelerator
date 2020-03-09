package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class LocalMemoryTester(dut: LocalMemory) extends PeekPokeTester(dut) {

  // Useful constants
  val addressWidthOfASingleBank = dut.getAddrW - dut.getBankAddrW
  val maxAddressSingleBank = 1 << addressWidthOfASingleBank
  val numberOfBanks = 1 << dut.getBankAddrW
  val maxAddress = 1 << dut.getAddrW
  val maxData = 1 << dut.getDataW

  // Writing to every memory location its address
  // and reading it back at next clock cycle
  println("[LocalMemoryTester] Burst write-and-read-back test")
  val tenPercent = maxAddress / 320
  val onePercent = maxAddress / 3200
  for (_ <- 1 to 3) {
    val randomBaseAddress = Random.nextInt(maxAddress)
    println("Base address: " + randomBaseAddress.toString +
            ", end of burst: " + (randomBaseAddress+maxAddress/32).toString)
    for (i <- 0 until maxAddress/32) {
      poke(dut.io.wrAddr, ((randomBaseAddress + i) % maxAddress).U)
      poke(dut.io.wrData, ((randomBaseAddress + i) % maxData).U)
      poke(dut.io.wrEn, true.B)
      step(1)

      poke(dut.io.wrEn, false.B)
      poke(dut.io.rdAddr, ((randomBaseAddress + i) % maxAddress).U)
      step(1)
      expect(dut.io.rdData, (randomBaseAddress + i) % maxData)

      // A sloppy progressbar
      if (i % tenPercent == 0) print("|")
      else if (i % onePercent == 0) print(".")
      if (i == (maxAddress/32 - 1)) print("\n")
    }
  }

  // Reading back contents of only one, randomly selected bank
  println("[LocalMemoryTester] Write-and-read-back test for only one bank at a time")
  val tenPercentBank = maxAddressSingleBank / 10
  val onePercentBank = maxAddressSingleBank / 100
  for (_ <- 1 to 3) {
    val randomBankIndex = Random.nextInt(numberOfBanks)
    println("Bank index: " + randomBankIndex.toString)
    for (i <- 0 until maxAddressSingleBank) {

      // Write address = {i, bankIdx}
      val catAddress = (i << dut.getBankAddrW) + randomBankIndex
      poke(dut.io.wrAddr, catAddress.U)
      poke(dut.io.wrData, i.U)
      poke(dut.io.wrEn, true.B)
      step(1)

      // Read address = i * nBanks + offset
      // should be the same as write address = {i, bankIdx}
      poke(dut.io.wrEn, false.B)
      poke(dut.io.rdAddr, ((randomBankIndex + i * numberOfBanks) % maxAddress).U)
      step(1)
      expect(dut.io.rdData, i.U)

      // A sloppy progressbar
      if (i % tenPercentBank == 0) print("|")
      else if (i % onePercentBank == 0) print(".")
      if (i == (maxAddressSingleBank - 1)) print("\n")
    }
  }
}
