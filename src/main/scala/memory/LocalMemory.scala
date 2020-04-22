package memory

import chisel3._
import _root_.arithmetic.baseType
import _root_.util.BarrelShifter

class LocalMemory extends Module {

  val numberOfBanks = 1 << bankAddrWidth

  val io = IO(new Bundle() {

    // DMA interface
    val wrAddr = Input(Vec(dmaChannels, UInt(localAddrWidth.W)))
    val wrData = Input(Vec(dmaChannels, baseType))
    val wrEn   = Input(Bool())

    // Arithmetic Grid interface
    val rdAddr = Input(UInt(localAddrWidth.W))
    val rdData = Output(Vec(numberOfBanks, baseType))
  })

  // Generating memory banks
  val memBanks = VecInit(Seq.fill(numberOfBanks) {
    Module(new MemoryBank).io
  })

  // Generating barrel shifter to connect banks to output ports
  val outputConnect = Module(new BarrelShifter(numberOfBanks, baseType, pipelined = false))

  // Connecting memory banks
  // Banks are addressed with the upper half of read/write address,
  // and indexed with the lower half, because this way neighboring
  // data words will be stored in neighboring banks providing high
  // bandwidth for sequential reading.
  for (i <- 0 until numberOfBanks) {

    // Connecting DMA interface
    memBanks(i).wrAddr := io.wrAddr(i%dmaChannels)(localAddrWidth - 1, bankAddrWidth)
    memBanks(i).wrData := io.wrData(i%dmaChannels)
    if (bankAddrWidth == 0)
      memBanks(0).wrEn   := io.wrEn
    else
      memBanks(i).wrEn   := (io.wrAddr(i%dmaChannels)(bankAddrWidth - 1, 0) === i.U) && io.wrEn

    // Connecting Arithmetic Grid interface
    // - Hardware support for unaligned read
    if (bankAddrWidth == 0)
      memBanks(0).rdAddr := io.rdAddr
    else
      memBanks(i).rdAddr := Mux(
        io.rdAddr(bankAddrWidth - 1, 0) > i.U,      // If the offset is greater than index of this bank
        io.rdAddr(localAddrWidth - 1, bankAddrWidth) + 1.U,  // then this banks should provide value from next row
        io.rdAddr(localAddrWidth - 1, bankAddrWidth)         // otherwise it provide data as requested
      )
    // - Connecting output of memory banks to barrel shifter
    if (bankAddrWidth != 0) outputConnect.io.in(i) := memBanks(i).rdData
  }

  if (bankAddrWidth == 0) {
    io.rdData(0) := memBanks(0).rdData
    // Unused interconnect
    outputConnect.io.in(0) := 0.U
    outputConnect.io.sh    := 0.U
  } else {
    // Connecting barrel shifter to module output
    outputConnect.io.sh := io.rdAddr(bankAddrWidth - 1, 0)
    io.rdData := outputConnect.io.out
  }

  def getAddrW = localAddrWidth
  def getBankAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}