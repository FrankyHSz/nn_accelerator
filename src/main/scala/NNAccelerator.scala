
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
    val wrAddr = Input(Vec(2, UInt(8.W)))
    val wrData = Input(Vec(2, UInt(8.W)))
    val wrEn   = Input(Vec(2, Bool()))
    val ldEn   = Input(Bool())
    val mac    = Output(UInt(32.W))
  })

  // Submodules
  val localMemoryA = Module(new LocalMemory(addrW = 8, bankAddrW = 0, dataW = 8))
  val localMemoryB = Module(new LocalMemory(addrW = 8, bankAddrW = 0, dataW = 8))
  val loadUnit     = Module(new LoadUnit(addrW = 8))
  val computeUnit  = Module(new ArithmeticUnit(inputW = 8, accuW = 32))

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
  computeUnit.io.a   := localMemoryA.io.rdData(0)
  computeUnit.io.b   := localMemoryB.io.rdData(0)
  computeUnit.io.en  := loadUnit.io.auEn
  computeUnit.io.clr := loadUnit.io.auClr

  // Outputting accumulator value for testing
  io.mac := computeUnit.io.mac
}
