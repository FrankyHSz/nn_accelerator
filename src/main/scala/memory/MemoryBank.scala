package memory

import chisel3._
import scala.math.pow

class MemoryBank(addrW : Int, dataW : Int) extends Module {
  val io = IO(new Bundle() {
    val addr = Input(UInt(addrW.W))
    val wrData = Input(UInt(dataW.W))
    val rdData = Output(UInt(dataW.W))
    val RdWrN = Input(Bool())
  })

  val memory = Reg(Vec(pow(2, addrW).toInt, UInt(dataW.W)))

  io.rdData := memory(io.addr)
  when (io.RdWrN === false.B) {
    memory(io.addr) := io.wrData
  }

  // Helper functions for testing
  def getAddrW = addrW
  def getDataW = dataW
}
