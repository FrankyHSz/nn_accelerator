package memory

import chisel3._
import _root_.arithmetic.gridSize

class LoadUnit extends Module {
  val io = IO(new Bundle() {

    // Control interface
    val en = Input(Bool())

    // Local memory interfaces
    val addrA = Output(UInt(localAddrWidth.W))
    val addrB = Output(UInt(localAddrWidth.W))

    // Arithmetic Grid interface
    val auEn  = Output(Bool())
    val auClr = Output(Bool())
  })

  // Free-running address generators (counters)
  val addrAReg = RegInit(0.U(localAddrWidth.W))
  val addrBReg = RegInit(0.U(localAddrWidth.W))
  when (io.en) {
    addrAReg := addrAReg + gridSize.U
    addrBReg := addrBReg + gridSize.U
  } .otherwise {
    addrAReg := 0.U
    addrAReg := 0.U
  }

  io.addrA := addrAReg
  io.addrB := addrBReg
  io.auEn  := RegNext(io.en)
  io.auClr := RegNext(io.en && (addrAReg === 0.U))  // Clearing AU accumulators after wrap-around

  def getAddrW = localAddrWidth
  def getN = gridSize
}
