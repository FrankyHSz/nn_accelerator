package memory

import chisel3._
import scala.math.pow

class MemoryBank(addrW : Int, dataW : Int) extends Module {
  val io = IO(new Bundle() {

    // Write port for DMA
    val wrAddr = Input(UInt(addrW.W))
    val wrData = Input(UInt(dataW.W))
    val wrEn = Input(Bool())

    // Read port for Arithmetic Grid
    val rdAddr = Input(UInt(addrW.W))
    val rdData = Output(UInt(dataW.W))
  })

  val memory = Reg(Vec((1 << addrW), UInt(dataW.W)))

  io.rdData := memory(io.rdAddr)
  when (io.wrEn) {
    memory(io.wrAddr) := io.wrData
  }

  // Helper functions for testing
  def getAddrW = addrW
  def getDataW = dataW
}
