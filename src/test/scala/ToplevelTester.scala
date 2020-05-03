
import chisel3._
import chisel3.iotesters.PeekPokeTester
import scala.util.Random
import _root_.memory.dmaChannels
import _root_.arithmetic.gridSize

class ToplevelTester(dut: NNAccelerator) extends PeekPokeTester(dut) {

  // DUT parameters
  val memoryWidth = dut.getAddrW
  val dataWidth   = dut.getDataW

  // Useful constants
  val memoryDepth = 1 << memoryWidth
  val maxData     = 1 << dataWidth

  println("Let the integration begin...")


  // Helper functions
  def toBoolean(in: BigInt) = (in % 2) == 1
  def toByte(in: Int) = in.toChar.toInt % 256
}