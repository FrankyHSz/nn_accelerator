
import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random
import _root_.memory.dmaChannels

class ToplevelTester(dut: NNAccelerator) extends PeekPokeTester(dut) {

  // DUT parameters
  val memoryWidth = dut.getAddrW
  val dataWidth   = dut.getDataW

  // Useful constants
  val memoryDepth = 1 << memoryWidth
  val maxData     = 1 << dataWidth

  // Test constants
  // - testLength: sweeping through local memory twice
  val testLength = 2 * memoryDepth / dut.getN

  // Creating random values
  var memA = Array.fill(memoryDepth){ Random.nextInt(maxData) - maxData/2 }
  var memB = Array.fill(memoryDepth){ Random.nextInt(maxData) - maxData/2 }

  // Initializing memories
  // ---------------------
  println("Initializing local memories...")
  println("------------------------------")
  var tenPercent = -1
  var onePercent = -1
  // Loading local memories by doing 4 DMA bursts,
  // each having memoryDepth/2 length
  for (loadStep <- 0 until 4) {

    // Programming the DMA
    val mem = if (loadStep%2 == 0) "A" else "B"
    val baseAddr = (loadStep/2) * (memoryDepth/2)  // Loading to 0-0-mem/2-mem/2
    val burstLen = (memoryDepth/2) / dmaChannels
    println("Programming DMA: mem. is " + mem + ", base address is " + baseAddr)
    poke(dut.io.ctrl.localBaseAddr, baseAddr.U)
    poke(dut.io.ctrl.busBaseAddr, baseAddr.U)
    poke(dut.io.ctrl.burstLen, burstLen.U)
    poke(dut.io.ctrl.sel, (loadStep%2 == 0).B)  // Loading to A-B-A-B

    // Start pulse
    poke(dut.io.ctrl.start, true.B)
    step(1)
    poke(dut.io.ctrl.start, false.B)

    // Waiting for bus request
    print("Waiting for bus request... ")
    while (!toBoolean(peek(dut.io.bus.dmaReady)))
      step(1)
    print("received.\n")

    // Emulating data burst from bus
    println("Emulating data burst from bus...")
    val currMem = if (loadStep%2 == 0) memA else memB
    tenPercent = burstLen / 10
    onePercent = burstLen / 100
    for (stepOffset <- 0 until (memoryDepth/2) by dmaChannels) {

      // Composing the bus data from neighboring bytes of system memory
      var sysData = toByte(currMem(baseAddr + stepOffset + dmaChannels-1))
      for (subStepOffset <- dmaChannels-2 to 0 by -1) {
        sysData = sysData << dataWidth
        sysData = sysData + toByte(currMem(baseAddr + stepOffset + subStepOffset))
      }
      poke(dut.io.bus.busDataIn, sysData.S)
      poke(dut.io.bus.busValid, true.B)
      step(1)

      // A sloppy progressbar
      if ((tenPercent != 0) && (stepOffset % tenPercent == 0)) print("|")
      else if ((onePercent != 0) && (stepOffset % onePercent == 0)) print(".")
    }
    // End of burst
    print(" Done.\n")
    poke(dut.io.bus.busValid, false.B)
    step(1)

    // Waiting for done signal
    print("Waiting for done signal... ")
    while (!toBoolean(peek(dut.io.ctrl.done)))
      step(1)
    print("received.\n")

    // A few additional cycles before next activity (not needed)
    step(3)
  }

  // Starting operation by enabling load unit
  poke(dut.io.ldEn, true.B)

  // Expecting MAC operations on mem. A and B
  // with accumulator resetting after wrap-around
  println("Running MAC operations...")
  println("-------------------------")
  var mult = 0
  var accu = Array.fill(dut.getN){ 0 }
  var addr = 0

  // Loop for clock cycles
  tenPercent = testLength / 10
  onePercent = testLength / 100
  for (t <- 0 until (testLength + dut.computeUnit.getDelay)) {

    // Stepping simulation first to get data from synchronous memory
    step(1)
    if (t >= dut.computeUnit.getDelay) {

      // Loop for compute units
      for (k <- 0 until dut.getN) {
        addr = ((t-dut.computeUnit.getDelay) * dut.getN + k) % memoryDepth
        mult = memA(addr) * memB(addr)

        // Testing and updating reference accumulator
        // Expecting MAC operations on mem. A and B
        // with accumulator resetting after wrap-around
        if (((t-dut.computeUnit.getDelay) * dut.getN) % memoryDepth == 0) {
          expect(dut.io.mac(k), mult)
          accu(k) = mult
        } else {
          expect(dut.io.mac(k), accu(k) + mult)
          accu(k) += mult
        }
      }
    }

    // A sloppy progressbar
    if ((tenPercent != 0) && (t % tenPercent == 0)) print("|")
    else if ((onePercent != 0) && (t % onePercent == 0)) print(".")
  }
  print("\n")


  // Helper functions
  def toBoolean(in: BigInt) = (in % 2) == 1
  def toByte(in: Int) = in.toChar.toInt % 256
}