package memory

import chisel3._

class LoadUnit(addrW: Int) extends Module {
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
    addrAReg := addrAReg + 1.U
    addrBReg := addrBReg + 1.U
  } .otherwise {
    addrAReg := 0.U
    addrAReg := 0.U
  }

  io.addrA := addrAReg
  io.addrB := addrBReg
  io.auEn  := RegNext(io.en)
  io.auClr := RegNext(io.en && (addrAReg === 0.U))  // Clearing AU accumulators after wrap-around

  def getAddrW = addrW

  // Debug
  // printf("[LoadUnit] AU clear is %b when en is %b and addr is %d\n", io.auClr, io.en, addrAReg)

}
