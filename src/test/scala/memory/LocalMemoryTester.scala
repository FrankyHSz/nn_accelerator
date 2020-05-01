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
  val burst_test = (numberOfBanks != 1)
  val bank_test  = true

  poke(dut.io.dmaWrEn, false.B)
  poke(dut.io.agWrEn, false.B)

  if (burst_test) {
    // Writing to every memory location its address
    // and reading it back at next clock cycle
    println("[LocalMemoryTester] Burst write-and-read-back test")

    if (dut.ifFlipped) {

      val burst_len  = 16
      val tenPercent = burst_len / 10
      val onePercent = burst_len / 100

      for (_ <- 1 to 3) {
        val randomBaseAddress = Random.nextInt(maxAddress/numberOfBanks) * numberOfBanks
        println("Base address: " + randomBaseAddress.toString +
          ", end of burst: " + (randomBaseAddress + burst_len).toString)
        println("Writing...")

        // Determining address range of burst_len (e.g. 16) overlapping bursts
        val writeRange = if (burst_len % numberOfBanks == 0) burst_len/numberOfBanks else burst_len/numberOfBanks + 1

        // Setting write enable to be active
        poke(dut.io.agWrEn, true.B)

        // Writing memory content, numberOfBanks (e.g. 64) bytes per clock
        // Loop for the whole write process
        for (stepOffset <- 0 until writeRange by numberOfBanks) {

          // Determining address
          val address = (randomBaseAddress + stepOffset) % maxAddress
          poke(dut.io.agAddr, address.U)

          // Loop for driving individual inputs
          for (subStepOffset <- 0 until numberOfBanks) {

            // Data to be assigned to the input
            val data = (randomBaseAddress + stepOffset + subStepOffset) % maxData

            // Input driving
            poke(dut.io.agData.asInstanceOf[Vec[UInt]](subStepOffset), data.S)

            // A sloppy progressbar
            if ((stepOffset + subStepOffset) % tenPercent == 0) print("|")
            else if ((stepOffset + subStepOffset) % onePercent == 0) print(".")
          }
          step(1)
        }
        print("\n")

        println("Reading...")
        poke(dut.io.agWrEn, false.B)
        for (i <- 0 until burst_len by dmaChannels) {
          for (ch <- 0 until dmaChannels) {
            poke(dut.io.dmaAddr.asInstanceOf[Vec[UInt]](ch), ((randomBaseAddress + i + ch) % maxAddress).U)
          }
          step(1)

          for (outIdx <- 0 until dmaChannels)
            expect(dut.io.dmaData.asInstanceOf[Vec[UInt]](outIdx), (randomBaseAddress + i + outIdx) % maxData)

          // A sloppy progressbar
          if (i % tenPercent == 0) print("|")
          else if (i % onePercent == 0) print(".")
        }
        print("\n")
      }

    } else {

      val burst_len  = 16
      val tenPercent = burst_len / 10
      val onePercent = burst_len / 100

      for (_ <- 1 to 3) {
        val randomBaseAddress = Random.nextInt(maxAddress)
        println("Base address: " + randomBaseAddress.toString +
          ", end of burst: " + (randomBaseAddress + burst_len).toString)
        println("Writing...")

        // Determining address range of burst_len (e.g. 16) overlapping bursts
        val writeRange = if ((burst_len + numberOfBanks - 1) % dmaChannels == 0) (burst_len + numberOfBanks - 1)
        else (burst_len + numberOfBanks - 1) + 1

        // Setting write enable to be active
        poke(dut.io.dmaWrEn, true.B)

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
            poke(dut.io.dmaAddr.asInstanceOf[Vec[UInt]](chIdx), address.U)
            poke(dut.io.dmaData.asInstanceOf[Vec[UInt]](chIdx), data.S)

            // A sloppy progressbar
            if ((stepOffset + subStepOffset) % tenPercent == 0) print("|")
            else if ((stepOffset + subStepOffset) % onePercent == 0) print(".")
            if ((stepOffset + subStepOffset) == (burst_len - 1 + numberOfBanks - 1)) print("\n")
          }
          step(1)
        }

        println("Reading...")
        poke(dut.io.dmaWrEn, false.B)
        for (i <- 0 until burst_len) {
          poke(dut.io.agAddr.asInstanceOf[UInt], ((randomBaseAddress + i) % maxAddress).U)
          step(1)

          for (outIdx <- 0 until numberOfBanks)
            expect(dut.io.agData.asInstanceOf[Vec[UInt]](outIdx), (randomBaseAddress + i + outIdx) % maxData)

          // A sloppy progressbar
          if (i % tenPercent == 0) print("|")
          else if (i % onePercent == 0) print(".")
          if (i == (burst_len - 1)) print("\n")
        }
      }
    }
  }

  if (bank_test) {
    // Reading back contents of only one, randomly selected bank
    println("[LocalMemoryTester] Write-and-read-back test for only one bank at a time")

    val tests = if (numberOfBanks != 1) 3 else 1
    val tenPercentBank = maxAddressSingleBank / 10
    val onePercentBank = maxAddressSingleBank / 100

    if (dut.ifFlipped) {

      for (_ <- 1 to tests) {
        val randomBankIndex = Random.nextInt(numberOfBanks)
        println("Bank index: " + randomBankIndex.toString)

        for (i <- 0 until maxAddressSingleBank) {

          // Write address = {i, bankIdx}
          val catAddress = (i << dut.getBankAddrW) + randomBankIndex
          poke(dut.io.agAddr, catAddress.U)
          poke(dut.io.agData.asInstanceOf[Vec[UInt]](0), (i - maxAddressSingleBank / 2).S)
          poke(dut.io.agWrEn, true.B)
          step(1)

          // Read address = i * nBanks + offset
          // should be the same as write address = {i, bankIdx}
          poke(dut.io.agWrEn, false.B)
          val chIdx = if (numberOfBanks != 1) catAddress % dmaChannels else 0
          poke(dut.io.dmaAddr(chIdx), ((randomBankIndex + i * numberOfBanks) % maxAddress).U)
          step(1)
          expect(dut.io.dmaData.asInstanceOf[Vec[UInt]](chIdx), (i - maxAddressSingleBank / 2))

          // A sloppy progressbar
          if (i % tenPercentBank == 0) print("|")
          else if (i % onePercentBank == 0) print(".")
        }
        print("\n")
      }

    } else {

      for (_ <- 1 to tests) {
        val randomBankIndex = Random.nextInt(numberOfBanks)
        println("Bank index: " + randomBankIndex.toString)
        for (i <- 0 until maxAddressSingleBank) {

          // Write address = {i, bankIdx}
          val catAddress = (i << dut.getBankAddrW) + randomBankIndex
          val chIdx = if (numberOfBanks != 1) catAddress % dmaChannels else 0
          poke(dut.io.dmaAddr(chIdx), catAddress.U)
          poke(dut.io.dmaData.asInstanceOf[Vec[UInt]](chIdx), (i - maxAddressSingleBank / 2).S)
          poke(dut.io.dmaWrEn, true.B)
          step(1)

          // Read address = i * nBanks + offset
          // should be the same as write address = {i, bankIdx}
          poke(dut.io.dmaWrEn, false.B)
          poke(dut.io.agAddr, ((randomBankIndex + i * numberOfBanks) % maxAddress).U)
          step(1)
          expect(dut.io.agData.asInstanceOf[Vec[UInt]](0), (i - maxAddressSingleBank / 2))

          // A sloppy progressbar
          if (i % tenPercentBank == 0) print("|")
          else if (i % onePercentBank == 0) print(".")
          if (i == (maxAddressSingleBank - 1)) print("\n")
        }
      }
    }
  }
}
