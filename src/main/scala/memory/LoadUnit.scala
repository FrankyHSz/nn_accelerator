package memory

import chisel3._

class LoadUnit(addrW: Int, n: Int) extends Module {
  val io = IO(new Bundle() {

    // Control interface
    val en = Input(Bool())

    // Local memory interfaces
    val addrA = Output(UInt(addrW.W))
    val addrB = Output(UInt(addrW.W))

    // Arithmetic Grid interface
    val auEn  = Output(Bool())
    val auClr = Output(Bool())
  })

  // Free-running address generators (counters)
  val addrAReg = RegInit(0.U(addrW.W))
  val addrBReg = RegInit(0.U(addrW.W))
  when (io.en) {
    addrAReg := addrAReg + n.U
    addrBReg := addrBReg + n.U
  } .otherwise {
    addrAReg := 0.U
    addrAReg := 0.U
  }

  io.addrA := addrAReg
  io.addrB := addrBReg
  io.auEn  := RegNext(io.en)
  io.auClr := RegNext(io.en && (addrAReg === 0.U))  // Clearing AU accumulators after wrap-around

  def getAddrW = addrW
  def getN = n
}
