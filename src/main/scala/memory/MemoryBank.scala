package memory

import chisel3._
import _root_.arithmetic.baseType

class MemoryBank extends Module {
  val io = IO(new Bundle() {

    // Write port
    val wrAddr = Input(UInt(bankAddrWidth.W))
    val wrData = Input(baseType)
    val wrEn = Input(Bool())

    // Read port
    val rdAddr = Input(UInt(bankAddrWidth.W))
    val rdData = Output(baseType)
  })

  val memory = SyncReadMem((1 << bankAddrWidth), baseType)

  io.rdData := memory.read(io.rdAddr)
  when (io.wrEn) {
    memory.write(io.wrAddr, io.wrData)
  }

  // Helper functions for testing
  def getAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}
