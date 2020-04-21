
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
    val wrAddr = Input(Vec(2, UInt(localAddrWidth.W)))
    val wrData = Input(Vec(2, baseType))
    val wrEn   = Input(Vec(2, Bool()))
    val ldEn   = Input(Bool())
    val mac    = Output(Vec(gridSize, accuType))
  })

  // Submodules
  val localMemoryA = Module(new LocalMemory)
  val localMemoryB = Module(new LocalMemory)
  val loadUnit     = Module(new LoadUnit)
  val computeUnit  = Module(new ArithmeticGrid)

  // Connecting memories to external write interface
  localMemoryA.io.wrAddr := io.wrAddr(0)
  localMemoryA.io.wrData := io.wrData(0)
  localMemoryA.io.wrEn   := io.wrEn(0)
  localMemoryB.io.wrAddr := io.wrAddr(1)
  localMemoryB.io.wrData := io.wrData(1)
  localMemoryB.io.wrEn   := io.wrEn(1)

  // Connecting "load enable" control signal to load unit
  loadUnit.io.en := io.ldEn

  // Local memory addressing
  localMemoryA.io.rdAddr := loadUnit.io.addrA
  localMemoryB.io.rdAddr := loadUnit.io.addrB

  // Connecting the arithmetic unit
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
