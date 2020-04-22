
import chisel3._
import arithmetic._
import memory._

class NNAccelerator extends Module {
  val io = IO(new Bundle() {
    // OCPcore slave interface for the processor
    // ...

    // OCPcore master interface for the system memory and arbiter
    // ...

    // Test interface
    // - Bus emulation for DMA
    val bus = new Bundle() {
      val busAddr = Output(UInt(busAddrWidth.W))
      val busBurstLen = Output(UInt(localAddrWidth.W))
      val busDataIn = Input(UInt(busDataWidth.W))
      val busRdWrN = Output(Bool())
      val busValid = Input(Bool())
      val dmaReady = Output(Bool())
    }
    // - Controller emulation for DMA
    val ctrl = new Bundle() {
      val localBaseAddr = Input(UInt(localAddrWidth.W))
      val busBaseAddr = Input(UInt(busAddrWidth.W))
      val burstLen = Input(UInt(localAddrWidth.W))
      val sel = Input(Bool())
      val start = Input(Bool())
      val done = Output(Bool())
    }
    // - Controller emulation for Load Unit
    val ldEn = Input(Bool())
    // - Output checking
    val wrData = Output(Vec(dmaChannels, baseType))
    val mac  = Output(Vec(gridSize, accuType))
  })

  // Submodules
  val dma          = Module(new DMA)
  val localMemoryA = Module(new LocalMemory)
  val localMemoryB = Module(new LocalMemory)
  val loadUnit     = Module(new LoadUnit)
  val computeUnit  = Module(new ArithmeticGrid)

  // Connecting DMA to external test interface
  dma.io.bus <> io.bus
  dma.io.ctrl <> io.ctrl

  // Connecting memories to DMA
  localMemoryA.io.wrAddr := dma.io.wrAddr
  localMemoryA.io.wrData := dma.io.wrData
  io.wrData := dma.io.wrData
  localMemoryA.io.wrEn   := (dma.io.memSel && dma.io.wrEn)
  localMemoryB.io.wrAddr := dma.io.wrAddr
  localMemoryB.io.wrData := dma.io.wrData
  localMemoryB.io.wrEn   := (!dma.io.memSel && dma.io.wrEn)

  // Connecting "load enable" control signal to load unit
  loadUnit.io.en := io.ldEn

  // Local memory addressing
  localMemoryA.io.rdAddr := loadUnit.io.addrA
  localMemoryB.io.rdAddr := loadUnit.io.addrB

  // Connecting the compute unit
  computeUnit.io.opA := localMemoryA.io.rdData
  computeUnit.io.opB := localMemoryB.io.rdData
  computeUnit.io.en  := loadUnit.io.auEn
  computeUnit.io.clr := loadUnit.io.auClr

  // Outputting accumulator value for testing
  io.mac := computeUnit.io.mac


  // Helper functions
  def getN = gridSize
  def getAddrW = localAddrWidth
  def getBankAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}
