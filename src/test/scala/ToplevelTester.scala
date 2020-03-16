
import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class ToplevelTester(dut: NNAccelerator) extends PeekPokeTester(dut) {

  // DUT parameters
  val memoryWidth = dut.getAddrW
  val dataWidth   = dut.getDataW

  // Useful constants
  val memoryDepth = 1 << memoryWidth
  val maxData     = 1 << dataWidth


  // Creating random values
  var memA = Array.fill(memoryDepth){ Random.nextInt(maxData) }
  var memB = Array.fill(memoryDepth){ Random.nextInt(maxData) }

  // Initializing memories
  println("Initializing local memories...")
  var tenPercent = memoryDepth / 10
  var onePercent = memoryDepth / 100
  poke(dut.io.wrEn(0), true.B)
  poke(dut.io.wrEn(1), true.B)
  for (i <- 0 until memoryDepth) {
    poke(dut.io.wrAddr(0), i.U)
    poke(dut.io.wrAddr(1), i.U)
    poke(dut.io.wrData(0), memA(i).U)
    poke(dut.io.wrData(1), memB(i).U)
    step(1)

    // A sloppy progressbar
    if ((tenPercent != 0) && (i % tenPercent == 0)) print("|")
    else if ((onePercent != 0) && (i % onePercent == 0)) print(".")
  }
  print("\n")
  poke(dut.io.wrEn(0), false.B)
  poke(dut.io.wrEn(1), false.B)

  // Starting operation by enabling load unit
  poke(dut.io.ldEn, true.B)

  // Expecting MAC operations on mem. A and B
  // with accumulator resetting after wrap-around
  println("Running MAC operations, sweeping through local memory twice...")
  var mult = 0
  var accu = Array.fill(dut.getN){ 0 }
  var addr = 0

  // Loop for clock cycles
  val testLength = 2 * memoryDepth / dut.getN
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
}