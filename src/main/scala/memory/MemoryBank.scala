package memory

import chisel3._
import _root_.arithmetic.baseType

class MemoryBank extends Module {
  val io = IO(new Bundle() {

    // Write port for DMA
    val wrAddr = Input(UInt(bankAddrWidth.W))
    val wrData = Input(baseType)
    val wrEn = Input(Bool())

    // Read port for Arithmetic Grid
    val rdAddr = Input(UInt(bankAddrWidth.W))
    val rdData = Output(baseType)
  })

  val memory = Reg(Vec((1 << bankAddrWidth), baseType))

  io.rdData := RegNext(memory(io.rdAddr))
  when (io.wrEn) {
    memory(io.wrAddr) := io.wrData
  }

  // Helper functions for testing
  def getAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}
