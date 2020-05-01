package memory

import chisel3._
import _root_.arithmetic.baseType
import _root_.util.BarrelShifter
// import chisel3.util.Mux1H

class LocalMemory(banked: Boolean, flippedInterface: Boolean) extends Module {

  val numberOfBanks = if (banked) 1 << bankAddrWidth else 1

  def dirDma(T: Data) = if (flippedInterface) Output(T) else Input(T)
  def dirAg(T: Data)  = if (flippedInterface) Input(T)  else Output(T)

  val io = IO(new Bundle() {

    // DMA interface
    val dmaAddr = Input(Vec(dmaChannels, UInt(localAddrWidth.W)))
    val dmaData = dirDma(Vec(dmaChannels, baseType))
    val dmaWrEn = Input(Bool())

    // Arithmetic Grid interface
    val agAddr = Input(UInt(localAddrWidth.W))
    val agData = dirAg(Vec(numberOfBanks, baseType))
    val agWrEn = Input(Bool())
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
    if (flippedInterface) {
      if (!banked) {
        memBanks(0).wrAddr := io.agAddr(localAddrWidth - 1, 0)
        memBanks(0).wrData := io.agData.asInstanceOf[Vec[UInt]](0)
      } else {
        memBanks(i).wrAddr := io.agAddr(localAddrWidth - 1, bankAddrWidth)
        memBanks(i).wrData := vectorConnect.io.out(numberOfBanks-1-i)
      }
    } else {
      if (!banked) {
        memBanks(0).wrAddr := io.dmaAddr(0)(localAddrWidth - 1, 0)
        memBanks(0).wrData := io.dmaData.asInstanceOf[Vec[UInt]](0)
      } else {
        memBanks(i).wrAddr := io.dmaAddr(i%dmaChannels)(localAddrWidth - 1, bankAddrWidth)
        memBanks(i).wrData := io.dmaData.asInstanceOf[Vec[UInt]](i%dmaChannels)
      }
    }
    if (!banked)
      if (flippedInterface)
        memBanks(0).wrEn := io.agWrEn
      else
        memBanks(0).wrEn := io.dmaWrEn
    else {
      if (flippedInterface) {
        memBanks(i).wrEn := io.agWrEn
      } else {
        val thisBankIsSelected = (io.dmaAddr(i%dmaChannels)(bankAddrWidth - 1, 0) === i.U)
        memBanks(i).wrEn := thisBankIsSelected && io.dmaWrEn
      }
    }

    // Connecting read interface
    // - Hardware support for unaligned read
    if (!banked)
      if (flippedInterface)
        memBanks(0).rdAddr := io.dmaAddr(0)
      else
        memBanks(0).rdAddr := io.agAddr
    else {
      if (flippedInterface) {
        memBanks(i).rdAddr := io.dmaAddr(i%dmaChannels)(localAddrWidth-1, bankAddrWidth)
      } else {
        // If the offset is greater than index of this bank
        // then this banks should provide value from next row
        // otherwise it provide data as requested
        memBanks(i).rdAddr := Mux(
          io.agAddr(bankAddrWidth - 1, 0) > i.U,
          io.agAddr(localAddrWidth - 1, bankAddrWidth) + 1.U,
          io.agAddr(localAddrWidth - 1, bankAddrWidth)
        )
      }
    }
    if (flippedInterface) {
      // - Connecting output of arithmetic units to barrel shifter
      if (bankAddrWidth != 0) vectorConnect.io.in(numberOfBanks-1-i) := io.agData.asInstanceOf[Vec[UInt]](i)
    } else {
      // - Connecting output of memory banks to barrel shifter
      if (bankAddrWidth != 0) vectorConnect.io.in(i) := memBanks(i).rdData
    }
  }

  if (!banked) {
    if (flippedInterface) {
      io.dmaData.asInstanceOf[Vec[UInt]](0) := memBanks(0).rdData
      io.dmaData.asInstanceOf[Vec[UInt]](1) := 0.S
      io.dmaData.asInstanceOf[Vec[UInt]](2) := 0.S
      io.dmaData.asInstanceOf[Vec[UInt]](3) := 0.S
    } else {
      io.agData.asInstanceOf[Vec[UInt]](0) := memBanks(0).rdData
    }
    // Unused interconnect
    vectorConnect.io.in(0) := 0.S
    vectorConnect.io.sh    := 0.U
  } else {
    if (flippedInterface) {
      vectorConnect.io.sh := io.agAddr
      for (ch <- 0 until dmaChannels) {
        // Building multiplexer-trees for each DMA port
        val selector = Wire(Vec(numberOfBanks/dmaChannels, Bool()))
        val dataVec  = Wire(Vec(numberOfBanks/dmaChannels, baseType))
        for (i <- 0 until numberOfBanks/dmaChannels) {
          selector(i) := (io.dmaAddr(ch)(bankAddrWidth-1, 0) === (i*dmaChannels + ch).U)
          dataVec(i)  := memBanks(i*dmaChannels + ch).rdData
        }
        val muxOut = util.Mux1H(selector, dataVec)
        io.dmaData.asInstanceOf[Vec[UInt]](ch) := muxOut
      }
    } else {
      // Connecting barrel shifter to module output
      vectorConnect.io.sh := io.agAddr(bankAddrWidth - 1, 0)
      io.agData := vectorConnect.io.out
    }
  }

  def getAddrW = localAddrWidth
  def getBankAddrW = if (banked) bankAddrWidth else 0
  def getDataW = baseType.getWidth
  def ifFlipped = flippedInterface
}