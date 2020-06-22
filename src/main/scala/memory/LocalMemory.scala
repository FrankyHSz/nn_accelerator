package memory

import chisel3._
import _root_.arithmetic.baseType
import _root_.util.BarrelShifter

class LocalMemory extends Module {

  val numberOfBanks = 1 << bankAddrWidth

  val io = IO(new Bundle() {

    // Write port
    val wrAddr = Input(UInt(localAddrWidth.W))
    val wrData = Input(Vec(numberOfBanks, baseType))
    val wrEn = Input(Bool())

    // Read port
    val rdAddr = Input(UInt(localAddrWidth.W))
    val rdData = Output(Vec(numberOfBanks, baseType))
  })

  // Generating memory banks
  val memBanks = VecInit(Seq.fill(numberOfBanks) {
    Module(new MemoryBank).io
  })

  // Generating barrel shifter to connect banks to output ports
  val vectorConnect = Module(new BarrelShifter(numberOfBanks, baseType, pipelined = false))

  // Connecting memory banks
  // Banks are addressed with the upper half of read/write address,
  // and indexed with the lower half, because this way neighboring
  // data words will be stored in neighboring banks providing high
  // bandwidth for sequential reading.
  for (i <- 0 until numberOfBanks) {

    // Connecting write interface
    memBanks(i).wrAddr := io.wrAddr(localAddrWidth-1, bankAddrWidth)
    memBanks(i).wrData := io.wrData(i)
    memBanks(i).wrEn := io.wrEn

    // Connecting read interface
    memBanks(i).rdAddr := io.rdAddr(localAddrWidth-1, bankAddrWidth)
    vectorConnect.io.sh := io.rdAddr(bankAddrWidth-1, 0)
    vectorConnect.io.in(i) := memBanks(i).rdData
    io.rdData(i) := vectorConnect.io.out(i)
  }

  def getAddrW = localAddrWidth
  def getBankAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}