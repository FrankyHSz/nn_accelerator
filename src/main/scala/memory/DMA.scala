package memory

import chisel3._
import chisel3.util.Enum
import _root_.arithmetic.baseType

class DMA extends Module {
  val io = IO(new Bundle() {

    // DMA <-> Bus Interface
    val busAddr     = Output(UInt(busAddrWidth.W))
    val busBurstLen = Output(UInt(busAddrWidth.W))
    val busDataIn   = Input(UInt(busDataWidth.W))
    // val busDataOut  = Output(UInt(busDataW.W))
    val busRdWrN    = Output(Bool())
    // Handshake signals: Bus -> DMA (read)
    val busValid = Input(Bool())
    val dmaReady = Output(Bool())
    // Handshake signals: DMA -> Bus (write)
    // val dmaValid = Output(Bool())
    // val busReady = Input(Bool())

    // DMA <-> Controller
    val localBaseAddr = Input(UInt(localAddrWidth.W))
    val busBaseAddr   = Input(UInt(busAddrWidth.W))
    val burstLen      = Input(UInt(localAddrWidth.W))
    val sel   = Input(Bool())
    val start = Input(Bool())
    val done  = Output(Bool())

    // DMA <-> Local Memories
    val wrAddr = Output(Vec(dmaChannels, UInt(localAddrWidth.W)))
    val wrData = Output(Vec(dmaChannels, baseType))
    val wrEn   = Output(Bool())
    val memSel = Output(Bool())
  })

  // Registers
  // ---------

  // Data registers
  val localAddrReg   = RegInit(0.U(localAddrWidth.W))  // Not just container but also an up-counter
  val busBaseAddrReg = RegInit(0.U(busAddrWidth.W))
  val burstLenReg    = RegInit(0.U(localAddrWidth.W))  // Not just container but also a down-counter
  val busDataInReg   = RegNext(io.busDataIn)

  // Control/handshake registers
  val selReg      = RegInit(true.B)
  val doneReg     = RegInit(true.B)
  val dmaReadyReg = RegInit(false.B)
  val busValidReg = RegNext(io.busValid)


  // Control FSM
  // -----------

  // State enum
  // - init: DMA waits on Controller to give read information and start signal
  // - check: burstLen =/= 0 checking, setting up request
  // - request: sending read request to bus interface then waiting on busValid
  // - read: reading until burst counter reaches 0
  val init :: check :: request :: read :: Nil = Enum(4)

  // State register
  val stateReg = RegInit(init)

  // Next state logic and (some of the) output driving
  io.wrEn := false.B  // Default value, see "read" state
  when (stateReg === init) {
    when (io.start) {
      stateReg := check
      localAddrReg   := io.localBaseAddr
      busBaseAddrReg := io.busBaseAddr
      burstLenReg    := io.burstLen
      selReg  := io.sel
      doneReg := false.B
    }
  } .elsewhen (stateReg === check) {
    when (burstLenReg === 0.U) {
      stateReg := init
      doneReg := true.B
    } .otherwise {
      stateReg := request
      dmaReadyReg := true.B
    }
  } .elsewhen (stateReg === request) {
    when (busValidReg) {
      stateReg := read
      burstLenReg := burstLenReg - 1.U
      localAddrReg := localAddrReg + dmaChannels.U
      io.wrEn := true.B
    }
  }. elsewhen (stateReg === read) {
    when (busValidReg) {
      when (burstLenReg > 1.U) {
        burstLenReg := burstLenReg - 1.U
        localAddrReg := localAddrReg + dmaChannels.U
        io.wrEn := true.B
      } .otherwise {
        stateReg := init
        burstLenReg := 0.U
        doneReg     := true.B
        dmaReadyReg := false.B
        io.wrEn := true.B
      }
    }
  }

  // Output assignments
  // ------------------

  // DMA <-> Bus Interface
  io.busAddr     := busBaseAddrReg
  io.busBurstLen := burstLenReg
  io.dmaReady := dmaReadyReg
  io.busRdWrN := true.B  // Reading only (for now)

  // DMA <-> Controller
  io.done := doneReg

  // DMA <-> Local Memories
  io.memSel := selReg
  for (port <- 0 until dmaChannels) {

    // Turning the 32-bit-aligned addressing into byte addresses
    io.wrAddr(port) := localAddrReg + port.U

    // Splitting 4-byte data into bytes
    val byteMsb = (port+1)*8 - 1
    val byteLsb = port*8
    io.wrData(port) := busDataInReg(byteMsb, byteLsb).asSInt()
  }


  // Helper functions
  def getBusAddrW = busAddrWidth
  def getBusDataW = busDataWidth
  def getLocalAddrW = localAddrWidth
  def getLocalDataW = baseType.getWidth
  def getChannels = dmaChannels
}
