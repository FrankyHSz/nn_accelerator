
import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random

class ToplevelTester(dut: NNAccelerator) extends PeekPokeTester(dut) {

  // Parameters (Should be the same as in DUT! These are just helper constants)
  val memoryWidth = 8
  val dataWidth   = 8

  // Useful constants
  val memoryDepth = 1 << memoryWidth
  val maxData     = 1 << dataWidth


  // Creating random values
  var memA = new Array[Int](memoryDepth)
  var memB = new Array[Int](memoryDepth)
  for (i <- 0 until memoryDepth) {
    memA(i) = Random.nextInt(maxData)
    memB(i) = Random.nextInt(maxData)
  }

  // Initializing memories
  for (i <- 0 until memoryDepth) {
    poke(dut.io.wrAddr(0), i.U)
    poke(dut.io.wrAddr(1), i.U)
    poke(dut.io.wrData(0), memA(i).U)
    poke(dut.io.wrData(1), memB(i).U)
    poke(dut.io.wrEn(0), true.B)
    poke(dut.io.wrEn(1), true.B)
    step(1)
  }
  poke(dut.io.wrEn(0), false.B)
  poke(dut.io.wrEn(1), false.B)

  // Starting operation by enabling load unit
  poke(dut.io.ldEn, true.B)

  // Expecting MAC operations on mem. A and B
  // with accumulator resetting after wrap-around
  var mult = 0
  var accu = 0
  var addr = 0
  for (i <- 0 until (2 * memoryDepth + dut.computeUnit.getDelay)) {

    // Stepping simulation first to get data from synchronous memory
    step(1)
    if (i >= dut.computeUnit.getDelay) {

      addr = (i-dut.computeUnit.getDelay) % memoryDepth
      mult = memA(addr) * memB(addr)

      // Testing and updating reference accumulator
      if ((i-dut.computeUnit.getDelay) % memoryDepth == 0) {
        expect(dut.io.mac, mult)
        accu = mult
      } else {
        expect(dut.io.mac, accu + mult)
        accu += mult
      }
    }
  }

}
