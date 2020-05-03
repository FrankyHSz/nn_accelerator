
import chisel3._
import arithmetic._
import chisel3.util.{ShiftRegister, log2Up}
import memory._
import ocp.{BusInterface, OcpBurstMasterPort, OcpCoreSlavePort, burstLen}

class NNAccelerator extends Module {
  val io = IO(new Bundle() {
    // OCPcore slave interface for the processor
    val ocpSlavePort = new OcpCoreSlavePort(busAddrWidth, busDataWidth)

    // OCPcore master interface for the system memory and arbiter
    val ocpMasterPort = new OcpBurstMasterPort(busAddrWidth, busDataWidth, burstLen)

    // Test interface
    // - Bus emulation for DMA
//    val bus = new Bundle() {
//      val busAddr = Output(UInt(busAddrWidth.W))
//      val busBurstLen = Output(UInt(localAddrWidth.W))
//      val busDataIn = Input(UInt(busDataWidth.W))
//      val busDataOut  = Output(UInt(busDataWidth.W))
//      val busRdWrN = Output(Bool())
//      val busValid = Input(Bool())
//      val dmaReady = Output(Bool())
//      val dmaValid = Output(Bool())
//      val busReady = Input(Bool())
//    }
//    // - Controller emulation for DMA
//    val ctrl = new Bundle() {
//      val busBaseAddr = Input(UInt(busAddrWidth.W))
//      val ldHeight = Input(UInt(log2Up(gridSize+1).W))
//      val ldWidth  = Input(UInt(log2Up(gridSize+1).W))
//      val rdWrN = Input(Bool())
//      val sel   = Input(Bool())
//      val start = Input(Bool())
//      val done  = Output(Bool())
//    }
//    // - Controller emulation for Load Unit
//    val ldEn = Input(Bool())
    // - Output checking
    val wrData = Output(Vec(dmaChannels, baseType))
    val mac  = Output(Vec(gridSize, accuType))
    val vld = Output(Bool())
  })

  // Submodules
  val busInterface = Module(new BusInterface)
  val controller   = Module(new Controller(testInternals = false))
  val dma          = Module(new DMA)
  val localMemoryA = Module(new LocalMemory(banked = false, flippedInterface = false))
  val localMemoryB = Module(new LocalMemory(banked = true, flippedInterface = false))
  val loadUnit     = Module(new LoadUnit)
  val computeUnit  = Module(new ArithmeticGrid)
  val activation   = Module(new ActivationGrid)
  val outputMemory = Module(new LocalMemory(banked = true, flippedInterface = true))

  // Connecting Bus Interface to OCP ports
  io.ocpSlavePort <> busInterface.io.ocpSlave
  io.ocpMasterPort <> busInterface.io.ocpMaster

  // Connecting DMA to Bus Interface and Controller
  dma.io.bus <> busInterface.io.dma
  dma.io.ctrl <> controller.io.dma

  // Connecting Controller to Bus Interface
  controller.io.bus <> busInterface.io.ctrl

  // Connecting memories to DMA
  // Testing
  io.wrData := dma.io.wrData
  // Memory A: DMA -> MemA -> AG
  localMemoryA.io.dmaAddr := dma.io.addr
  localMemoryA.io.dmaData.asInstanceOf[Vec[UInt]] := dma.io.wrData
  localMemoryA.io.dmaWrEn := (dma.io.memSel && dma.io.wrEn)
  localMemoryA.io.agWrEn := false.B
  // Memory B: DMA -> MemB -> AG
  localMemoryB.io.dmaAddr := dma.io.addr
  localMemoryB.io.dmaData.asInstanceOf[Vec[UInt]] := dma.io.wrData
  localMemoryB.io.dmaWrEn := (!dma.io.memSel && dma.io.wrEn)
  localMemoryB.io.agWrEn := false.B
  // Output memory: AG -> MemOut -> DMA
  outputMemory.io.dmaAddr := dma.io.addr
  dma.io.rdData := outputMemory.io.dmaData
  outputMemory.io.dmaWrEn := false.B

  // Connecting Load Unit with Controller
  loadUnit.io.ctrl <> controller.io.ldunit

  // Local memory addressing
  localMemoryA.io.agAddr := loadUnit.io.addrA
  localMemoryB.io.agAddr := loadUnit.io.addrB

  // Connecting inputs of ArithmeticGrid
  for (i <- 0 until gridSize)
    computeUnit.io.opA(i) := localMemoryA.io.agData.asInstanceOf[Vec[SInt]](0)  // Scalar input port
  computeUnit.io.opB := localMemoryB.io.agData                                  // Vector input port
  computeUnit.io.en  := loadUnit.io.auEn
  computeUnit.io.clr := loadUnit.io.auClr

  // Connecting outputs of ArithmeticGrid to ActivationGrid
  activation.io.in := computeUnit.io.mac
  activation.io.sel := controller.io.actSel

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

  // Connecting output of ActivationGrid to Output Memory
  outputMemory.io.agAddr := outWrAddrReg
  outputMemory.io.agData := activation.io.out
  outputMemory.io.agWrEn := RegNext(computeUnit.io.vld)
  for (i <- 0 until dmaChannels) {
    outputMemory.io.dmaAddr(i) := 0.U
  }


  // Helper functions
  def getN = gridSize
  def getAddrW = localAddrWidth
  def getBankAddrW = bankAddrWidth
  def getDataW = baseType.getWidth
}

object NNAMain extends App {
  chisel3.Driver.execute(args, () => new NNAccelerator)
}
