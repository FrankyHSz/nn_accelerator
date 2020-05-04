package memory

import chisel3._
import chisel3.util.{Cat, Enum, log2Up}
import _root_.arithmetic.{baseType, gridSize}

class DMA extends Module {
  val io = IO(new Bundle() {

    // DMA <-> Bus Interface
    val bus = new Bundle() {
      val busAddr = Output(UInt(busAddrWidth.W))
      val busBurstLen = Output(UInt(busAddrWidth.W))
      val busDataIn = Input(UInt(busDataWidth.W))
      val busDataOut  = Output(UInt(busDataWidth.W))
      // Handshake signals: Bus -> DMA (read)
      val busValid = Input(Bool())
      val dmaReady = Output(Bool())
      // Handshake signals: DMA -> Bus (write)
      val dmaValid = Output(Bool())
      val busReady = Input(Bool())
    }

    // DMA <-> Controller
    val ctrl = new Bundle() {
      val busBaseAddr = Input(UInt(busAddrWidth.W))
      val ldHeight = Input(UInt(log2Up(gridSize+1).W))
      val ldWidth  = Input(UInt(log2Up(gridSize+1).W))
      val rdWrN = Input(Bool())
      val sel   = Input(Bool())
      val kernel = Input(Bool())
      val start = Input(Bool())
      val done  = Output(Bool())
    }

    // DMA <-> Local Memories
    val addr   = Output(Vec(dmaChannels, UInt(localAddrWidth.W)))
    // - DMA <-> Local Memories A and B
    val wrData = Output(Vec(dmaChannels, baseType))
    val wrEn   = Output(Bool())
    val memSel = Output(Bool())
    // - DMA <-> Local Memory C (output mem.)
    val rdData = Input(Vec(dmaChannels, baseType))

    // Test
    val state = Output(UInt(3.W))
  })

  // Registers
  // ---------

  // Data registers
  val localAddrReg   = RegInit(0.U(localAddrWidth.W))  // Not just container but also an up-counter
  val busBaseAddrReg = RegInit(0.U(busAddrWidth.W))
  val burstLenReg    = RegInit(0.U(localAddrWidth.W))  // Not just container but also a down-counter
  val rowLengthReg   = RegInit(0.U(log2Up(gridSize+1).W))
  val columnCounter  = RegInit(0.U(log2Up(gridSize+1).W))
  val rowCounter     = RegInit(0.U(log2Up(gridSize+1).W))
  val busDataInReg   = RegNext(io.bus.busDataIn)
  val busDataOutReg  = RegInit(0.U(busDataWidth.W))

  // Control/handshake registers
  val selReg      = RegInit(true.B)
  val rdWrNReg    = RegInit(true.B)
  val doneReg     = RegInit(true.B)
  val wrReqReg    = RegInit(false.B)
  val dmaReadyReg = RegInit(false.B)
  val busValidReg = RegNext(io.bus.busValid)
  val busReadyReg = RegNext(io.bus.busReady)
  val dmaValidReg = RegInit(false.B)

  val randomWrEnReg = RegInit(false.B)

  // Control FSM
  // -----------

  // State enum
  // - init: DMA waits on Controller to give read information and start signal
  // - check: burstLen =/= 0 checking, setting up request
  // - request: sending read request to bus interface then waiting on busValid
  // - read: reading until burst counter reaches 0
  val init :: check :: request :: read :: write :: Nil = Enum(5)

  // State register
  val stateReg = RegInit(init)

  // Next state logic and (some of the) output driving
  io.wrEn := false.B  // Default value, see "read" state
  when (stateReg === init) {
    when (randomWrEnReg) {
      io.wrEn := true.B
      randomWrEnReg := false.B
    }
    when (io.ctrl.start) {
      stateReg := check
      localAddrReg   := 0.U
      busBaseAddrReg := io.ctrl.busBaseAddr
      when (io.ctrl.rdWrN) {
        burstLenReg := io.ctrl.ldWidth * io.ctrl.ldHeight >> (log2Up(dmaChannels))
      } .otherwise {
        burstLenReg := (io.ctrl.ldWidth + dmaChannels.U) * io.ctrl.ldHeight >> (log2Up(dmaChannels))
      }
      rowLengthReg   := Mux(io.ctrl.kernel, io.ctrl.ldWidth * io.ctrl.ldHeight, io.ctrl.ldWidth)
      columnCounter  := Mux(io.ctrl.kernel, io.ctrl.ldWidth * io.ctrl.ldHeight, io.ctrl.ldWidth)
      rowCounter     := Mux(io.ctrl.kernel, 1.U, io.ctrl.ldHeight)
      selReg  := io.ctrl.sel
      rdWrNReg := io.ctrl.rdWrN
      doneReg := false.B
    }
  } .elsewhen (stateReg === check) {
    when (burstLenReg === 0.U) {
      stateReg := init
      doneReg := true.B
    } .otherwise {
      stateReg := request
      dmaReadyReg := rdWrNReg
      dmaValidReg := !rdWrNReg
    }
  } .elsewhen (stateReg === request) {
    dmaValidReg := !rdWrNReg
    when (rdWrNReg && busValidReg) {
      stateReg := read
      when (columnCounter > dmaChannels.U) {
        columnCounter := columnCounter - dmaChannels.U
        localAddrReg := localAddrReg + dmaChannels.U
      } .otherwise {
        rowCounter := rowCounter - 1.U
        localAddrReg := localAddrReg + gridSize.U
      }
      burstLenReg := burstLenReg - 1.U
      io.wrEn := true.B
    } .elsewhen (!rdWrNReg && io.bus.busReady) {
      stateReg := write
      burstLenReg := burstLenReg - 1.U
      when (columnCounter > dmaChannels.U) {
        columnCounter := columnCounter - dmaChannels.U
        localAddrReg := localAddrReg + dmaChannels.U
      } .otherwise {
        rowCounter := rowCounter - 1.U
        localAddrReg := localAddrReg + gridSize.U
      }
    }
  }. elsewhen (stateReg === read) {
    when (busValidReg) {
      when (burstLenReg > 1.U) {
        when (columnCounter > dmaChannels.U) {
          columnCounter := columnCounter - dmaChannels.U
          localAddrReg := localAddrReg + dmaChannels.U
        } .elsewhen (rowCounter > 1.U) {
          columnCounter := rowLengthReg
          rowCounter := rowCounter - 1.U
          when (columnCounter =/= dmaChannels.U) {
            localAddrReg := localAddrReg + (gridSize.U - columnCounter - rowLengthReg + dmaChannels.U)
          } .otherwise {
            localAddrReg := localAddrReg + (gridSize.U - rowLengthReg + dmaChannels.U)
          }
        }  // otherwise burstLenReg would be 1 or 0
        burstLenReg := burstLenReg - 1.U
        io.wrEn := true.B
      } .otherwise {
        stateReg := init
        burstLenReg   := 0.U
        rowLengthReg  := 0.U
        columnCounter := 0.U
        rowCounter    := 0.U
        localAddrReg := localAddrReg + dmaChannels.U
        doneReg     := true.B
        dmaReadyReg := false.B
        io.wrEn := true.B
        when (io.ctrl.kernel && burstLenReg === 1.U) {
          randomWrEnReg := true.B
        }
      }
    }
  } .elsewhen (stateReg === write) {
    when (io.bus.busReady) {
      when (burstLenReg > 1.U) {
        when (columnCounter > dmaChannels.U) {
          columnCounter := columnCounter - dmaChannels.U
          localAddrReg := localAddrReg + dmaChannels.U
        } .elsewhen (rowCounter > 1.U) {
          columnCounter := rowLengthReg
          rowCounter := rowCounter - 1.U
          when (columnCounter =/= dmaChannels.U) {
            localAddrReg := localAddrReg + (gridSize.U - columnCounter - rowLengthReg + dmaChannels.U)
          } .otherwise {
            localAddrReg := localAddrReg + (gridSize.U - rowLengthReg + dmaChannels.U)
          }
        }
        burstLenReg := burstLenReg - 1.U
        dmaValidReg := true.B
      } .otherwise {
        stateReg := init
        burstLenReg   := 0.U
        rowLengthReg  := 0.U
        columnCounter := 0.U
        rowCounter    := 0.U
        doneReg     := true.B
        dmaValidReg := false.B
        wrReqReg := false.B
        // io.wrEn := true.B
      }
    }
  }

  // Output assignments
  // ------------------

  // Test
  io.state := stateReg

  // DMA <-> Bus Interface
  io.bus.busAddr     := busBaseAddrReg
  io.bus.busBurstLen := burstLenReg
  io.bus.dmaReady := dmaReadyReg
  io.bus.dmaValid := dmaValidReg

  // DMA <-> Controller
  io.ctrl.done := doneReg

  // DMA <-> Local Memories
  io.memSel := selReg
  val byteMsb = Array(7, 15, 23, 31)
  val byteLsb = Array(0, 8, 16, 24)
  for (port <- 0 until dmaChannels) {

    // Turning the 32-bit-aligned addressing into byte addresses
    io.addr(port) := localAddrReg + port.U
    // printf("[DMA] Port %d has address %d\n", port.U, localAddrReg + port.U)

    // Splitting 4-byte data into bytes
    io.wrData(port) := busDataInReg(byteMsb(port), byteLsb(port)).asSInt()
    // printf("[DMA] Port %d has data %d\n", port.U, busDataInReg(byteMsb(port), byteLsb(port)).asSInt())
    // printf("[DMA] Port %d has data %d\n", port.U, io.rdData(port))
  }
  // Merging data bytes into one 4-byte word
  io.bus.busDataOut := io.rdData.asUInt()


  // Helper functions
  def getBusAddrW = busAddrWidth
  def getBusDataW = busDataWidth
  def getLocalAddrW = localAddrWidth
  def getLocalDataW = baseType.getWidth
  def getChannels = dmaChannels
}
