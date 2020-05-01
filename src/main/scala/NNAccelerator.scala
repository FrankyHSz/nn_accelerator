
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
    val vld = Output(Bool())
  })

  // Submodules
  val dma          = Module(new DMA)
  val localMemoryA = Module(new LocalMemory(banked = false, flippedInterface = false))
  val localMemoryB = Module(new LocalMemory(banked = true, flippedInterface = false))
  val loadUnit     = Module(new LoadUnit)
  val computeUnit  = Module(new ArithmeticGrid)
  val outputMemory = Module(new LocalMemory(banked = true, flippedInterface = true))

  // Connecting DMA to external test interface
  dma.io.bus <> io.bus
  dma.io.ctrl <> io.ctrl

  // Connecting memories to DMA
  localMemoryA.io.dmaAddr := dma.io.addr
  localMemoryA.io.dmaData.asInstanceOf[Vec[UInt]] := dma.io.wrData
  io.wrData := dma.io.wrData
  localMemoryA.io.dmaWrEn := (dma.io.memSel && dma.io.wrEn)
  localMemoryB.io.dmaAddr := dma.io.addr
  localMemoryB.io.dmaData.asInstanceOf[Vec[UInt]] := dma.io.wrData
  localMemoryB.io.dmaWrEn := (!dma.io.memSel && dma.io.wrEn)
  localMemoryA.io.agWrEn := false.B
  localMemoryB.io.agWrEn := false.B

  // Connecting "load enable" control signal to load unit
  loadUnit.io.en := io.ldEn

  // Local memory addressing
  localMemoryA.io.agAddr := loadUnit.io.addrA
  localMemoryB.io.agAddr := loadUnit.io.addrB

  // Connecting the compute unit
  computeUnit.io.opA := localMemoryA.io.agData.asInstanceOf[Vec[UInt]]
  computeUnit.io.opB := localMemoryB.io.agData.asInstanceOf[Vec[UInt]]
  computeUnit.io.en  := loadUnit.io.auEn
  computeUnit.io.clr := loadUnit.io.auClr

  // Outputting accumulator value for testing
  io.mac := computeUnit.io.mac
  io.vld := computeUnit.io.vld

  // Register to store address for output memory
  // Write address for output memory is what was
  // the first address for input memory
  val outWrAddrReg = RegInit(0.U(localAddrWidth.W))
  when (loadUnit.io.auClr) {
    outWrAddrReg := loadUnit.io.addrA
  }

  // Storing accumulator values into output memory
  outputMemory.io.agAddr := outWrAddrReg
  outputMemory.io.agData := computeUnit.io.mac
  outputMemory.io.agWrEn := computeUnit.io.vld
  for (i <- 0 until dmaChannels) {
    outputMemory.io.dmaAddr(i) := 0.U
  }
  outputMemory.io.dmaWrEn := false.B


  // Helper functions
  def getN = gridSize
  def getAddrW = localAddrWidth
  def getBankAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}
