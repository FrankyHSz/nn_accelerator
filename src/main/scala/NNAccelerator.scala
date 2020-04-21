
import chisel3._
import chisel3.util.log2Up
import arithmetic._
import memory._

class NNAccelerator extends Module {

  // Internal parameters and useful constants
  val n = 32      // Final NNA will have 256
  val addrW = 12  // Final NNA will have 16 bits (64 kB)
  val bankAddrW = log2Up(n)
  val dataW = 8
  val accuW = 2 * dataW + accuExt

  def getN = n
  def getAddrW = addrW
  def getBankAddrW = bankAddrW
  def getDataW = dataW

  val io = IO(new Bundle() {
    // OCPcore slave interface for the processor
    // ...

    // OCPcore master interface for the system memory and arbiter
    // ...

    // Test interface
    val wrAddr = Input(Vec(2, UInt(addrW.W)))
    val wrData = Input(Vec(2, SInt(dataW.W)))
    val wrEn   = Input(Vec(2, Bool()))
    val ldEn   = Input(Bool())
    val mac    = Output(Vec(n, SInt(accuW.W)))
  })

  // Submodules
  val localMemoryA = Module(new LocalMemory(addrW = addrW, bankAddrW = bankAddrW, dataW = dataW))
  val localMemoryB = Module(new LocalMemory(addrW = addrW, bankAddrW = bankAddrW, dataW = dataW))
  val loadUnit     = Module(new LoadUnit(addrW = addrW, n = n))
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
}
