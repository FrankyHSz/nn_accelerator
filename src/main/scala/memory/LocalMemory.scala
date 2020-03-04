package memory

import chisel3._

class LocalMemory(addrW: Int, bankAddrW: Int, dataW: Int) extends Module {
  val io = IO(new Bundle() {

    // DMA interface
    val wrAddr = Input(UInt(addrW.W))
    val wrData = Input(UInt(dataW.W))
    val wrEn   = Input(Bool())

    // Arithmetic Grid interface
    val rdAddr = Input(UInt(addrW.W))
    val rdData = Output(UInt(dataW.W))
  })

  // Generating memory banks
  val numberOfBanks = 1 << bankAddrW
  val memBanks = VecInit(Seq.fill(numberOfBanks) {
    Module(new MemoryBank(addrW = (addrW - bankAddrW), dataW = dataW)).io
  })

  // Connecting memory banks
  // Banks are addressed with the upper half of read/write address,
  // and indexed with the lower half, because this way neighboring
  // data words will be stored in neighboring banks providing high
  // bandwidth for sequential reading.
  io.rdData := 42.U
  for (i <- 0 until numberOfBanks) {
    memBanks(i).wrAddr := io.wrAddr(addrW-1, bankAddrW)
    memBanks(i).rdAddr := io.rdAddr(addrW-1, bankAddrW)
    memBanks(i).wrData := io.wrData
    memBanks(i).wrEn   := (io.wrAddr(bankAddrW-1, 0) === i.U) && io.wrEn
    when (io.rdAddr(bankAddrW-1, 0) === i.U) {
      io.rdData := memBanks(i).rdData
    }
  }

  def getAddrW = addrW
  def getBankAddrW = bankAddrW
  def getDataW = dataW
}