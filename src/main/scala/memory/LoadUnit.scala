package memory

import chisel3._

class LoadUnit(addrW: Int) extends Module {
  val io = IO(new Bundle() {

    // Local memory interfaces
    val addrA = Output(UInt(addrW.W))
    val addrB = Output(UInt(addrW.W))

    // Arithmetic Grid interface
    val en  = Output(Bool())
    val clr = Output(Bool())
  })

  // Free-running address generators (counters)
  val addrAReg = RegInit(0.U(addrW.W))
  val addrBReg = RegInit(0.U(addrW.W))
  addrAReg := addrAReg + 1.U
  addrBReg := addrBReg + 1.U

  io.addrA := addrAReg
  io.addrB := addrBReg
  io.en    := true.B               // Always enable (for now)
  io.clr   := (addrAReg == 0.U).B  // Clearing AU accumulators after wrap-around

  def getAddrW = addrW
}
