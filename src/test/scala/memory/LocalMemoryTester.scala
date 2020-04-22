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
  val maxData = 1 << (dut.getDataW-1)

  // Tests to run
  val burst_test = true
  val bank_test  = true

  if (burst_test) {
    // Writing to every memory location its address
    // and reading it back at next clock cycle
    println("[LocalMemoryTester] Burst write-and-read-back test")
    val burst_len  = 16
    val tenPercent = burst_len / 10
    val onePercent = burst_len / 100
    for (_ <- 1 to 3) {
      val randomBaseAddress = Random.nextInt(maxAddress)
      println("Base address: " + randomBaseAddress.toString +
        ", end of burst: " + (randomBaseAddress + burst_len).toString)
      println("Writing...")

      // Determining address range of burst_len (e.g. 16) overlapping bursts
      val writeRange = if ((burst_len+numberOfBanks-1) % dmaChannels == 0) (burst_len+numberOfBanks-1)
                       else (burst_len+numberOfBanks-1) + 1

      // Setting write enable to be active
      poke(dut.io.wrEn, true.B)

      // Writing memory content, dmaChannels (e.g. 4) bytes per clock
      // Loop for the whole write process
      for (stepOffset <- 0 until writeRange by dmaChannels) {
        // Loop for driving individual channels
        for (subStepOffset <- 0 until dmaChannels) {

          // Address and data to be assigned to the channel
          val address = (randomBaseAddress + stepOffset + subStepOffset) % maxAddress
          val data = (randomBaseAddress + stepOffset + subStepOffset) % maxData

          // Determining which channel could it be assigned to (aligned memory)
          val chIdx = address % dmaChannels

          // Channel driving
          poke(dut.io.wrAddr(chIdx), address.U)
          poke(dut.io.wrData(chIdx), data.S)

          // A sloppy progressbar
          if ((stepOffset+subStepOffset) % tenPercent == 0) print("|")
          else if ((stepOffset+subStepOffset) % onePercent == 0) print(".")
          if ((stepOffset+subStepOffset) == (burst_len-1 + numberOfBanks-1)) print("\n")
        }
        step(1)
      }

      println("Reading...")
      poke(dut.io.wrEn, false.B)
      for (i <- 0 until burst_len) {
        poke(dut.io.rdAddr, ((randomBaseAddress + i) % maxAddress).U)
        step(1)

        for (outIdx <- 0 until numberOfBanks)
          expect(dut.io.rdData(outIdx), (randomBaseAddress + i + outIdx) % maxData)

        // A sloppy progressbar
        if (i % tenPercent == 0) print("|")
        else if (i % onePercent == 0) print(".")
        if (i == (burst_len - 1)) print("\n")
      }
    }
  }

  if (bank_test) {
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
        val chIdx = catAddress % dmaChannels
        poke(dut.io.wrAddr(chIdx), catAddress.U)
        poke(dut.io.wrData(chIdx), (i - maxAddressSingleBank/2).S)
        poke(dut.io.wrEn, true.B)
        step(1)

        // Read address = i * nBanks + offset
        // should be the same as write address = {i, bankIdx}
        poke(dut.io.wrEn, false.B)
        poke(dut.io.rdAddr, ((randomBankIndex + i * numberOfBanks) % maxAddress).U)
        step(1)
        expect(dut.io.rdData(0), (i - maxAddressSingleBank/2))

        // A sloppy progressbar
        if (i % tenPercentBank == 0) print("|")
        else if (i % onePercentBank == 0) print(".")
        if (i == (maxAddressSingleBank - 1)) print("\n")
      }
    }
  }
}
