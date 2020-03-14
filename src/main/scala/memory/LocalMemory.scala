package memory

import chisel3._
import _root_.util.BarrelShifter

class LocalMemory(addrW: Int, bankAddrW: Int, dataW: Int) extends Module {

  val numberOfBanks = 1 << bankAddrW

  val io = IO(new Bundle() {

    // DMA interface
    val wrAddr = Input(UInt(addrW.W))
    val wrData = Input(UInt(dataW.W))
    val wrEn   = Input(Bool())

    // Arithmetic Grid interface
    val rdAddr = Input(UInt(addrW.W))
    val rdData = Output(Vec(numberOfBanks, UInt(dataW.W)))
  })

  // Generating memory banks
  val memBanks = VecInit(Seq.fill(numberOfBanks) {
    Module(new MemoryBank(addrW = (addrW - bankAddrW), dataW = dataW)).io
  })

  // Generating barrel shifter to connect banks to output ports
  val outputConnect = Module(new BarrelShifter(numberOfBanks, UInt(dataW.W), pipelined = false))

  // Connecting memory banks
  // Banks are addressed with the upper half of read/write address,
  // and indexed with the lower half, because this way neighboring
  // data words will be stored in neighboring banks providing high
  // bandwidth for sequential reading.
  for (i <- 0 until numberOfBanks) {

    // Connecting DMA interface
    memBanks(i).wrAddr := io.wrAddr(addrW - 1, bankAddrW)
    memBanks(i).wrData := io.wrData
    memBanks(i).wrEn   := (io.wrAddr(bankAddrW - 1, 0) === i.U) && io.wrEn

    // Connecting Arithmetic Grid interface
    // - Hardware support for unaligned read
    memBanks(i).rdAddr := Mux(
      io.rdAddr(bankAddrW - 1, 0) > i.U,      // If the offset is greater than index of this bank
      io.rdAddr(addrW - 1, bankAddrW) + 1.U,  // then this banks should provide value from next row
      io.rdAddr(addrW - 1, bankAddrW)         // otherwise it provide data as requested
    )
    // - Connecting output of memory banks to barrel shifter
    outputConnect.io.in(i) := memBanks(i).rdData
  }

  // Connecting barrel shifter to module output
  outputConnect.io.sh := io.rdAddr(bankAddrW - 1, 0)
  io.rdData := outputConnect.io.out

  def getAddrW = addrW
  def getBankAddrW = bankAddrW
  def getDataW = dataW
}