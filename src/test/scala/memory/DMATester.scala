package memory

import chisel3._
import chisel3.iotesters.PeekPokeTester
import javax.naming.ConfigurationException

import scala.util.control.Breaks._

@throws(classOf[IllegalArgumentException])
@throws(classOf[ConfigurationException])
class DMATester(dut: DMA) extends PeekPokeTester(dut) {

  // Test constants
  val logConfig     = false
  val sysMemSize    = 256
  val dummyData     = 42
  val numOfConfigs  = 3  // Should match the size of the arrays below
  val sysMemOffsets = Array(3, 142, 99)
  val busDelays     = Array(10, 0, 23)    // How many clock cycles the DMA have to wait
  val localOffsets  = Array(32, 80, 272)
  val burstLengths  = Array(16, 64, 256)
  val targetMemIsA  = Array(false, true, false)
  // Further explanation for bus delay: The FSM inside the DMA has a "request" state
  // where it waits for the bus to respond. If the bus has 0-cycle delay it means
  // that the DMA will be in "request" state for only 1 clock cycle, then move on
  // to read state. If the bus has e.g. 17-cycle delay, the DMA will be in "request"
  // state for 18 clock cycles.

  // Useful constants
  val localMemSize = 1 << dut.getLocalAddrW
  val maxData = 1 << dut.getBusDataW
  val MSBs = Array(7, 15, 23, 31)
  val LSBs = Array(0, 8, 16, 24)

  // System memory model
  val systemMemory = Array.tabulate(sysMemSize)( i => (i - sysMemSize/2)%maxData )

  // Checking DUT parameters
  if (dut.getChannels != 4)
    throw new IllegalArgumentException("Tests are defined only for 4 channels!")
  for (i <- 0 until numOfConfigs)
    if ((burstLengths(i) % localMemSize) != burstLengths(i))
      throw new ConfigurationException("Read bursts cannot be longer than local memory size!")

  // Checking initial values on interfaces
  // -------------------------------------
  println("[DMATester] Checking initial values on interfaces")
  expect(dut.io.busAddr, 0.U)           // Initial bus address is 0
  expect(dut.io.busBurstLen, 0.U)       // Initial burst length is 0
  expect(dut.io.busRdWrN, true.B)       // Initial Rd/WrN control is "read" (true)
  expect(dut.io.dmaReady, false.B)      // Initially the DMA is not ready with read info
  expect(dut.io.done, true.B)           // But it is ready to receive config from Controller
  for (i <- 0 until dut.getChannels) {
    expect(dut.io.wrAddr(i), i.U)       // Initial wr. address is 0-1-2-3 (in case of 4 channels)
    expect(dut.io.wrData(i), 0.S)       // Initial wr. data is 0 (on all channels)
  }
  expect(dut.io.wrEn, false.B)          // Initially write is disabled on local mem. ports
  expect(dut.io.memSel, true.B)         // Initially, memory A is selected


  // Testing DMA with different configs
  // ----------------------------------
  println("[DMATester] Testing DMA with different configs")
  for (config <- 0 until numOfConfigs) {

    if (logConfig) {
      println("[DMATester] Active config is:")
      println("[DMATester]   sys. mem. offset : " + sysMemOffsets(config))
      println("[DMATester]   bus delay        : " + busDelays(config))
      println("[DMATester]   local offset     : " + localOffsets(config))
      println("[DMATester]   burst length     : " + burstLengths(config))
      println("[DMATester]   target mem. is A : " + targetMemIsA(config))
    }

    // Driving inputs from bus interface
    // (to avoid unknown register values)
    poke(dut.io.busDataIn, dummyData.U)
    poke(dut.io.busValid, false.B)

    // Driving inputs from Controller
    poke(dut.io.localBaseAddr, (localOffsets(config) % localMemSize).U)
    poke(dut.io.busBaseAddr, sysMemOffsets(config).U)
    poke(dut.io.burstLen, burstLengths(config).U)
    poke(dut.io.sel, targetMemIsA(config).B)
    poke(dut.io.start, true.B)

    step(1)
    poke(dut.io.start, false.B)

    // Outputs driven from internal registers
    // are expected to have new values now
    // - Bus interface:
    expect(dut.io.busAddr, sysMemOffsets(config).U)
    expect(dut.io.busBurstLen, burstLengths(config).U)
    // - Local memory interface:
    expect(dut.io.memSel, targetMemIsA(config).B)
    for (ch <- 0 until dut.getChannels) {
      expect(dut.io.wrAddr(ch), ((localOffsets(config) + ch) % localMemSize).U)
      expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
    }
    // - Controller interface:
    expect(dut.io.done, false.B)

    // But some other values should not be changed yet
    // - Bus interface:
    expect(dut.io.busRdWrN, true.B)   // Always reading
    expect(dut.io.dmaReady, false.B)  // Not ready yet
    // - Local memory interface
    expect(dut.io.wrEn, false.B)  // Bus data is not valid yet

    step(1)

    // DMA is just after it's "check" state
    // where it does nothing but checks the burst length
    // But at this point, testing has two possible ways to go
    breakable {
      if (burstLengths(config) == 0) {
        expect(dut.io.dmaReady, false.B)

        // In this case, the DMA goes back to it's initial state
        step(1)
        expect(dut.io.done, true.B)
        break  // We can start testing with the next config
      } else {

        // If burst length is not 0, DMA should assert it's ready signal
        expect(dut.io.dmaReady, true.B)

        // Other signals do not change
        // - Bus interface:
        expect(dut.io.busAddr, sysMemOffsets(config).U)
        expect(dut.io.busBurstLen, burstLengths(config).U)
        expect(dut.io.busRdWrN, true.B)   // Always reading
        // - Local memory interface:
        expect(dut.io.memSel, targetMemIsA(config).B)
        expect(dut.io.wrEn, false.B)  // Bus data is not valid yet
        for (ch <- 0 until dut.getChannels) {
          expect(dut.io.wrAddr(ch), ((localOffsets(config) + ch) % localMemSize).U)
          expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
        }
        // - Controller interface:
        expect(dut.io.done, false.B)


        // DMA is frozen until bus signals valid
        for (_ <- 0 until busDelays(config) ) {
          step(1)
          expect(dut.io.dmaReady, true.B)

          // - Bus interface:
          expect(dut.io.busAddr, sysMemOffsets(config).U)
          expect(dut.io.busBurstLen, burstLengths(config).U)
          expect(dut.io.busRdWrN, true.B)   // Always reading
          // - Local memory interface:
          expect(dut.io.memSel, targetMemIsA(config).B)
          expect(dut.io.wrEn, false.B)  // Bus data is not valid yet
          for (ch <- 0 until dut.getChannels) {
            expect(dut.io.wrAddr(ch), ((localOffsets(config) + ch) % localMemSize).U)
            expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
          }
          // - Controller interface:
          expect(dut.io.done, false.B)
        }

        // Driving the bus interface with valid data
        // DMA should propagate the data towards local
        // memories, but to addresses from localBaseAddr
        // and up and split into 4 separate bytes
        poke(dut.io.busValid, true.B)
        for (cycle <- 0 until burstLengths(config)) {

          // Emulating memory read
          val address = (peek(dut.io.busAddr).toInt + cycle) % sysMemSize
          poke(dut.io.busDataIn, systemMemory(address).U)

          step(1)

          // Expecting DMA not to be done until the end of burst
          // Expecting DMA to be ready until the end of burst
          expect(dut.io.done, false.B)
          expect(dut.io.dmaReady, true.B)

          // Expecting mem. select to be still correct
          // Expecting write enable to be active during burst
          // Expecting write address to start from localBaseAddr, stepping 4 at a time
          // Expecting 32-bit data to appear in 4 pieces, in little endian order
          expect(dut.io.memSel, targetMemIsA(config).B)
          expect(dut.io.wrEn, true.B)
          for (ch <- 0 until dut.getChannels) {
            expect(dut.io.wrAddr(ch), ((localOffsets(config) + cycle*4 + ch) % localMemSize).U)
            expect(dut.io.wrData(ch), split(systemMemory(address), MSBs(ch), LSBs(ch)).S)
          }
        }

        // Expecting
        // - done to be asserted,
        // - DMA ready to be deasserted
        // - and write enable to be inactive
        // after the last cycle of the burst write
        step(1)
        expect(dut.io.done, true.B)
        expect(dut.io.dmaReady, false.B)
        expect(dut.io.wrEn, false.B)

        // And now everything is inactive, DMA is waiting
        // for the next start signal

      }
    }
  }


  // Helper functions
  def split(data: Int, msb: Int, lsb: Int): Int = {
    if (lsb > msb) throw new IllegalArgumentException("LSB cannot be larger than MSB!")
    (data >> lsb) % (1 << msb)
  }
}
