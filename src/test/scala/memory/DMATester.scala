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
  val outputMemSize = 256
  val dummyData     = 42
  val numOfConfigs  = 3  // Should match the size of the arrays below
  val sysMemOffsets = Array(3, 142, 99)
  val busDelays     = Array(10, 0, 23)    // How many clock cycles the DMA have to wait
  val localOffsets  = Array(32, 80, 272)
  val burstLengths  = Array(16, 64, 129)
  val targetMemIsA  = Array(false, true, false)
  // Further explanation for bus delay: The FSM inside the DMA has a "request" state
  // where it waits for the bus to respond. If the bus has 0-cycle delay it means
  // that the DMA will be in "request" state for only 1 clock cycle, then move on
  // to read state. If the bus has e.g. 17-cycle delay, the DMA will be in "request"
  // state for 18 clock cycles.

  // Useful constants
  val localMemSize = 1 << dut.getLocalAddrW
  val maxData = 1 << dut.getBusDataW
  val maxLocalData = 1 << dut.getLocalDataW
  val MSBs = Array(7, 15, 23, 31)
  val LSBs = Array(0, 8, 16, 24)

  // Memory models
  val systemMemory = Array.tabulate(sysMemSize)( i => (i - sysMemSize/2)%maxData )
  val localMemory  = Array.tabulate(outputMemSize)( i => (i - localMemSize/2)%maxLocalData)

  // Checking DUT parameters
  if (dut.getChannels != 4)
    throw new IllegalArgumentException("Tests are defined only for 4 channels!")
  for (i <- 0 until numOfConfigs)
    if ((burstLengths(i) % localMemSize) != burstLengths(i))
      throw new ConfigurationException("Read bursts cannot be longer than local memory size!")

  // Checking initial values on interfaces
  // -------------------------------------
  println("[DMATester] Checking initial values on interfaces")
  expect(dut.io.bus.busAddr, 0.U)           // Initial bus address is 0
  expect(dut.io.bus.busBurstLen, 0.U)       // Initial burst length is 0
  expect(dut.io.bus.busRdWrN, true.B)       // Initial Rd/WrN control is "read" (true)
  expect(dut.io.bus.dmaReady, false.B)      // Initially the DMA is not ready with read info
  expect(dut.io.ctrl.done, true.B)           // But it is ready to receive config from Controller
  for (i <- 0 until dut.getChannels) {
    expect(dut.io.addr(i), i.U)       // Initial wr. address is 0-1-2-3 (in case of 4 channels)
    expect(dut.io.wrData(i), 0.S)       // Initial wr. data is 0 (on all channels)
  }
  expect(dut.io.wrEn, false.B)          // Initially write is disabled on local mem. ports
  expect(dut.io.memSel, true.B)         // Initially, memory A is selected


  // Testing DMA loads with different configs
  // ----------------------------------------
  println("[DMATester] Testing DMA loads with different configs")
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
    poke(dut.io.bus.busDataIn, dummyData.U)
    poke(dut.io.bus.busValid, false.B)
    poke(dut.io.bus.busReady, false.B)

    // Driving inputs from Controller
    poke(dut.io.ctrl.localBaseAddr, (localOffsets(config) % localMemSize).U)
    poke(dut.io.ctrl.busBaseAddr, sysMemOffsets(config).U)
    poke(dut.io.ctrl.burstLen, burstLengths(config).U)
    poke(dut.io.ctrl.rdWrN, true.B)
    poke(dut.io.ctrl.sel, targetMemIsA(config).B)
    poke(dut.io.ctrl.start, true.B)

    step(1)
    poke(dut.io.ctrl.start, false.B)

    // Outputs driven from internal registers
    // are expected to have new values now
    // - Bus interface:
    expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
    expect(dut.io.bus.busBurstLen, burstLengths(config).U)
    // - Local memory interface:
    expect(dut.io.memSel, targetMemIsA(config).B)
    for (ch <- 0 until dut.getChannels) {
      expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
      expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
    }
    // - Controller interface:
    expect(dut.io.ctrl.done, false.B)

    // But some other values should not be changed yet
    // - Bus interface:
    expect(dut.io.bus.busRdWrN, true.B)   // Always reading
    expect(dut.io.bus.dmaReady, false.B)  // Not ready yet
    // - Local memory interface
    expect(dut.io.wrEn, false.B)  // Bus data is not valid yet

    step(1)

    // DMA is just after it's "check" state
    // where it does nothing but checks the burst length
    // But at this point, testing has two possible ways to go
    breakable {
      if (burstLengths(config) == 0) {
        expect(dut.io.bus.dmaReady, false.B)

        // In this case, the DMA goes back to it's initial state
        step(1)
        expect(dut.io.ctrl.done, true.B)
        break  // We can start testing with the next config
      } else {

        // If burst length is not 0, DMA should assert it's ready signal
        expect(dut.io.bus.dmaReady, true.B)

        // Other signals do not change
        // - Bus interface:
        expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
        expect(dut.io.bus.busBurstLen, burstLengths(config).U)
        expect(dut.io.bus.busRdWrN, true.B)   // Always reading
        // - Local memory interface:
        expect(dut.io.memSel, targetMemIsA(config).B)
        expect(dut.io.wrEn, false.B)  // Bus data is not valid yet
        for (ch <- 0 until dut.getChannels) {
          expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
          expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
        }
        // - Controller interface:
        expect(dut.io.ctrl.done, false.B)


        // DMA is frozen until bus signals valid
        for (_ <- 0 until busDelays(config) ) {
          step(1)
          expect(dut.io.bus.dmaReady, true.B)

          // - Bus interface:
          expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
          expect(dut.io.bus.busBurstLen, burstLengths(config).U)
          expect(dut.io.bus.busRdWrN, true.B)   // Always reading
          // - Local memory interface:
          expect(dut.io.memSel, targetMemIsA(config).B)
          expect(dut.io.wrEn, false.B)  // Bus data is not valid yet
          for (ch <- 0 until dut.getChannels) {
            expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
            expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
          }
          // - Controller interface:
          expect(dut.io.ctrl.done, false.B)
        }

        // Driving the bus interface with valid data
        // DMA should propagate the data towards local
        // memories, but to addresses from localBaseAddr
        // and up and split into 4 separate bytes
        poke(dut.io.bus.busValid, true.B)
        for (cycle <- 0 until burstLengths(config)) {

          // Emulating memory read
          val address = (peek(dut.io.bus.busAddr).toInt + cycle) % sysMemSize
          poke(dut.io.bus.busDataIn, systemMemory(address).U)

          step(1)

          // Expecting DMA not to be done until the end of burst
          // Expecting DMA to be ready until the end of burst
          expect(dut.io.ctrl.done, false.B)
          expect(dut.io.bus.dmaReady, true.B)

          // Expecting mem. select to be still correct
          // Expecting write enable to be active during burst
          // Expecting write address to start from localBaseAddr, stepping 4 at a time
          // Expecting 32-bit data to appear in 4 pieces, in little endian order
          expect(dut.io.memSel, targetMemIsA(config).B)
          expect(dut.io.wrEn, true.B)
          for (ch <- 0 until dut.getChannels) {
            expect(dut.io.addr(ch), ((localOffsets(config) + cycle*4 + ch) % localMemSize).U)
            expect(dut.io.wrData(ch), split(systemMemory(address), MSBs(ch), LSBs(ch)).S)
          }
        }

        // Expecting
        // - done to be asserted,
        // - DMA ready to be deasserted
        // - and write enable to be inactive
        // after the last cycle of the burst write
        step(1)
        expect(dut.io.ctrl.done, true.B)
        expect(dut.io.bus.dmaReady, false.B)
        expect(dut.io.wrEn, false.B)

        // And now everything is inactive, DMA is waiting
        // for the next start signal

      }
    }
  }


  // Testing DMA stores with different configs
  // -----------------------------------------
  println("[DMATester] Testing DMA stores with different configs")

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
    poke(dut.io.bus.busDataIn, dummyData.U)
    poke(dut.io.bus.busValid, false.B)
    poke(dut.io.bus.busReady, false.B)

    // Driving inputs from Controller
    poke(dut.io.ctrl.localBaseAddr, (localOffsets(config) % localMemSize).U)
    poke(dut.io.ctrl.busBaseAddr, sysMemOffsets(config).U)
    poke(dut.io.ctrl.burstLen, burstLengths(config).U)
    poke(dut.io.ctrl.rdWrN, false.B)
    poke(dut.io.ctrl.sel, targetMemIsA(config).B)  // Not used for stores
    poke(dut.io.ctrl.start, true.B)

    step(1)
    poke(dut.io.ctrl.start, false.B)

    // Outputs driven from internal registers
    // are expected to have new values now
    // - Bus interface:
    expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
    expect(dut.io.bus.busBurstLen, burstLengths(config).U)
    expect(dut.io.bus.busRdWrN, false.B)   // Always reading
    // - Local memory interface:
    expect(dut.io.memSel, targetMemIsA(config).B)  // Not used for stores
    for (ch <- 0 until dut.getChannels) {
      expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
      expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
    }
    // - Controller interface:
    expect(dut.io.ctrl.done, false.B)

    // But some other values should not be changed yet
    // - Bus interface:
    expect(dut.io.bus.dmaValid, false.B)   // Data is not valid yet
    expect(dut.io.bus.dmaReady, false.B)   // Should remain this way for stores
    // - Local memory interface
    expect(dut.io.wrEn, false.B)           // Should remain this way for stores

    step(1)

    // DMA is just after it's "check" state
    // where it does nothing but checks the burst length
    // But at this point, testing has two possible ways to go
    breakable {
      if (burstLengths(config) == 0) {
        expect(dut.io.bus.dmaReady, false.B)

        // In this case, the DMA goes back to it's initial state
        step(1)
        expect(dut.io.ctrl.done, true.B)
        break // We can start testing with the next config
      } else {

        // If burst length is not 0, DMA should assert it's write request signal
        expect(dut.io.bus.dmaValid, true.B)

        // Other signals do not change
        // - Bus interface:
        expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
        expect(dut.io.bus.busBurstLen, burstLengths(config).U)
        expect(dut.io.bus.busRdWrN, false.B)  // Should remain this way for stores
        expect(dut.io.bus.dmaReady, false.B)  // Should remain this way for stores
        // - Local memory interface:
        expect(dut.io.wrEn, false.B)          // Should remain this way for stores
        for (ch <- 0 until dut.getChannels) {
          expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
          expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
        }
        // - Controller interface:
        expect(dut.io.ctrl.done, false.B)

        // DMA is frozen until bus signals ready
        for (_ <- 0 until busDelays(config)) {
          step(1)
          expect(dut.io.bus.dmaValid, true.B)

          // - Bus interface:
          expect(dut.io.bus.busAddr, sysMemOffsets(config).U)
          expect(dut.io.bus.busBurstLen, burstLengths(config).U)
          expect(dut.io.bus.busRdWrN, false.B)  // Should remain this way for stores
          expect(dut.io.bus.dmaReady, false.B)  // Should remain this way for stores
          // - Local memory interface:
          expect(dut.io.wrEn, false.B) // Should remain this way for stores
          for (ch <- 0 until dut.getChannels) {
            expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
            expect(dut.io.wrData(ch), split(dummyData, MSBs(ch), LSBs(ch)).S)
          }
          // - Controller interface:
          expect(dut.io.ctrl.done, false.B)
        }

        // Driving bus ready
        poke(dut.io.bus.busReady, true.B)
        step(1)
        for (cycle <- 0 until burstLengths(config)-1) {

          // DMA holds valid high until the burst ends
          expect(dut.io.bus.dmaValid, true.B)

          // Dropping busReady signal for a while mid-burst
          if (cycle == 9) poke(dut.io.bus.busReady, false.B)
          if (cycle == 10) {
            // And holding it down for 5 clock cycles
            // Expecting that everything remains the same
            for (_ <- 0 until 5) {
              expect(dut.io.state, dut.write)
              for (ch <- 0 until dut.getChannels) {
                expect(dut.io.addr(ch), ((localOffsets(config) + cycle * 4 + ch) % localMemSize).U)
              }
              expect(dut.io.bus.dmaValid, true.B)
              expect(dut.io.ctrl.done, false.B)
              step(1)
            }
            poke(dut.io.bus.busReady, true.B)
            step(1)
          }

          // Expecting read address to start from localBaseAddr, stepping 4 at a time
          if (cycle == 0) {
            expect(dut.io.state, dut.request)
            for (ch <- 0 until dut.getChannels) {
              expect(dut.io.addr(ch), ((localOffsets(config) + ch) % localMemSize).U)
            }
          } else {
            expect(dut.io.state, dut.write)
            for (ch <- 0 until dut.getChannels) {
              expect(dut.io.addr(ch), ((localOffsets(config) + cycle*4 + ch) % localMemSize).U)
            }
          }

          val readAddresses = Array.fill(dmaChannels) { 0 }
          for (ch <- 0 until dmaChannels)
            readAddresses(ch) = peek(dut.io.addr(ch)).toInt

          step(1)

          expect(dut.io.state, dut.write)

          // Emulating read from local memory
          for (ch <- 0 until dmaChannels) {
            poke(dut.io.rdData(ch), localMemory(readAddresses(ch)).S)
          }

          // Expecting DMA not to be done until the end of burst
          // Expecting merged data word on bus interface
          expect(dut.io.ctrl.done, false.B)
          var busData: BigInt = toByte(localMemory(readAddresses(dmaChannels-1)))
          for (ch <- dmaChannels-2 to 0 by -1) {
            busData = busData << dut.getLocalDataW
            busData += toByte(localMemory(readAddresses(ch)))
          }
          expect(dut.io.bus.busDataOut, busData)
        }

        // Expecting
        // - done to be asserted,
        // - DMA ready to be deasserted
        // - and write enable to be inactive
        // after the last cycle of the burst write
        step(1)
        expect(dut.io.state, dut.init)
        expect(dut.io.ctrl.done, true.B)
        expect(dut.io.bus.dmaValid, false.B)

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
  def toByte(in: Int) = in.toChar.toInt % 256
}
