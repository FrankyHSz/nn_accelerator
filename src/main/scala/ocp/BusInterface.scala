package ocp

import chisel3._
import chisel3.util.{Enum, log2Up}

class BusInterface extends Module {

  // Helper constants
  val IP_BASE_ADDRESS_UPPER_BITS = IP_BASE_ADDRESS / (1 << IP_ADDRESS_WIDTH)

  val io = IO(new Bundle() {

    // Port towards OCP bus
    val ocpSlave = new OcpCoreSlavePort(addrWidth, dataWidth)
    val ocpMaster = new OcpBurstMasterPort(addrWidth, dataWidth, burstLen)

    val ctrl = new Bundle {
      // Signals from and toward Controller
      // - RD/WR interface for register files
      val addr = Output(UInt(addrWidth.W)) // Decoded address (offset inside IP address space)
      val wrData = Output(UInt(dataWidth.W))
      val rdData = Input(UInt(dataWidth.W))
      val rdWrN = Output(Bool())
      // - Decoded block select signals from Bus Interface
      val statusSel = Output(Bool())
      val errCauseSel = Output(Bool())
      val commandSel = Output(Bool())
      val ldAddrSel = Output(Bool())
      val ldSizeSel = Output(Bool())
    }

    // Signals from and toward DMA
    val dma = new Bundle() {
      val busAddr = Input(UInt(addrWidth.W))
      val busBurstLen = Input(UInt(addrWidth.W))
      val busDataIn = Output(UInt(dataWidth.W))
      val busDataOut = Input(UInt(dataWidth.W))
      // Handshake signals: Bus -> DMA (read)
      val busValid = Output(Bool())
      val dmaReady = Input(Bool())
      // Handshake signals: DMA -> Bus (write)
      val dmaValid = Input(Bool())
      val busReady = Output(Bool())
    }
  })

  // Recognizing if this module is addressed or not
  val moduleSelect = WireDefault(false.B)
  when (io.ocpSlave.M.Addr(addrWidth-1, IP_ADDRESS_WIDTH) === IP_BASE_ADDRESS_UPPER_BITS.U) {
    moduleSelect := true.B
  }

  // Decoding block select signals
  // -----------------------------
  io.ctrl.statusSel   := false.B
  io.ctrl.errCauseSel := false.B
  io.ctrl.commandSel  := false.B
  io.ctrl.ldAddrSel   := false.B
  io.ctrl.ldSizeSel   := false.B
  // When this module is selected and the block address is 0...
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 0.U) {
    // ... then it is either the control/status register's address...
    when (io.ocpSlave.M.Addr(0) === 0.U) {
      io.ctrl.statusSel := true.B
    // ... or the error cause register's address.
    } .otherwise {
      io.ctrl.errCauseSel := true.B
    }
  }
  // When this module is selected and the block address
  // is 1 then the command register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 1.U) {
    io.ctrl.commandSel := true.B
  }
  // When this module is selected and the block address
  // is 2 then the load address register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 2.U) {
    io.ctrl.ldAddrSel := true.B
  }
  // When this module is selected and the block address
  // is 3 then the load address register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 3.U) {
    io.ctrl.ldSizeSel := true.B
  }

  // Handling slave signals (Controller interface)
  // ---------------------------------------------
  // Propagate address if this module is selected and a read or write operation is requested
  io.ctrl.addr := 0.U
  when (moduleSelect && (io.ocpSlave.M.Cmd === OcpCmd.WR || io.ocpSlave.M.Cmd === OcpCmd.RD)) {
    io.ctrl.addr := io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-3, 0)
  }
  // Translate read/write signals
  io.ctrl.rdWrN := true.B
  when (moduleSelect && io.ocpSlave.M.Cmd === OcpCmd.WR) {
    io.ctrl.rdWrN := false.B
  }
  // Propagate write data if this module is selected and it is a write operation
  io.ctrl.wrData := 0.U
  when (moduleSelect && io.ocpSlave.M.Cmd === OcpCmd.WR) {
    io.ctrl.wrData := io.ocpSlave.M.Data
  }
  // Generate OCP Slave response: response is delayed by respReg
  val respReg = RegInit(OcpResp.NULL)  // Response register
  when (moduleSelect && (io.ocpSlave.M.Cmd === OcpCmd.WR || io.ocpSlave.M.Cmd === OcpCmd.RD)) {
    respReg := OcpResp.DVA
  } .otherwise {
    respReg := OcpResp.NULL            // Default register value
  }
  io.ocpSlave.S.Resp := respReg        // Driving bus from register
  // Driving slave data (read data)
  io.ocpSlave.S.Data := 0.U
  when (RegNext(moduleSelect && io.ocpSlave.M.Cmd === OcpCmd.RD)) {  // Condition signal is delayed
    io.ocpSlave.S.Data := io.ctrl.rdData                                  // to match with data from registers
  }

  // Handling master signals (DMA interface)
  // ---------------------------------------

  // Registering burst slave signals
  val busValidReg = RegNext(io.ocpMaster.S.Resp === OcpResp.DVA)
  val burstSData = RegNext(io.ocpMaster.S.Data)
  val burstSCmdAccept  = RegNext(io.ocpMaster.S.CmdAccept)
  val burstSDataAccept = RegNext(io.ocpMaster.S.DataAccept)

  // Burst master signal registers
  val burstCmdReg  = RegInit(0.U(3.W))  // IDLE
  val baseAddress  = RegInit(0.U(addrWidth.W))
  val nextBaseAddr = RegInit(0.U(addrWidth.W))
  val burstDataReg = RegInit(0.U(dataWidth.W))
  val byteEnReg    = RegInit(0.U(4.W))
  val dataValidReg = RegInit(false.B)
  val burstCounter = RegInit(0.U(log2Up(burstLen).W))

  // States of burst FSM
  val idle :: read :: write :: Nil = Enum(3)
  val stateReg = RegInit(idle)

  // Default values of combinatorial outputs
  io.dma.busValid := false.B
  io.dma.busReady := false.B

  // Continue signal: if DMA request a longer burst
  // that what is supported by the bus, this register
  // signals if the next RD/WR burst should continue
  // operation from the address in baseAddress register
  // or read a new address from the DMA
  val continue = RegInit(false.B)
  // printf("Next base address is %d\n", nextBaseAddr)
  // printf("Continue is %b\n", continue)

  // State machine
  when (stateReg === idle) {

    // When DMA signals ready while the interface
    // is IDLE, a read command is issued
    when (io.dma.dmaReady) {
      stateReg := read
      burstCmdReg := OcpCmd.RD
      baseAddress := Mux(continue, nextBaseAddr, io.dma.busAddr)
      when (!continue) {
        nextBaseAddr := io.dma.busAddr
      }

    // When DMA signals valid while the interface
    // is IDLE, a write command is issued
    } .elsewhen (io.dma.dmaValid) {
      stateReg := write
      burstCmdReg  := OcpCmd.WR
      baseAddress  := Mux(continue, nextBaseAddr, io.dma.busAddr)
      when (!continue) {
        nextBaseAddr := io.dma.busAddr
      }
      burstDataReg := io.dma.busDataOut
      byteEnReg    := 15.U
      dataValidReg := true.B
      when(io.dma.busBurstLen > burstLen.U) {
        burstCounter := (burstLen - 1).U
      } .otherwise {
        burstCounter := io.dma.busBurstLen - 1.U
      }
    }

  } .elsewhen (stateReg === read) {

    // When a command is on the bus and the slave signals
    // that it accepted it, we should go back to IDLE
    when (io.ocpMaster.S.CmdAccept(0)) {
      burstCmdReg := OcpCmd.IDLE
      baseAddress := 0.U

    // When the slave responds with DVA, the received data
    // can be forwarded to the DMA (no need for counting words)
    } .elsewhen(io.ocpMaster.S.Resp === OcpResp.DVA) {
      nextBaseAddr := nextBaseAddr + 1.U

    } .otherwise {
      stateReg := idle
      when (io.dma.busBurstLen =/= 0.U) {
        continue := true.B
      } .otherwise {
        continue := false.B
      }
    }

  } .elsewhen (stateReg === write) {

    // When a command is on the bus and the slave signals
    // that it accepted it, we should go back to IDLE
    when (io.ocpMaster.S.CmdAccept(0)) {
      burstCmdReg := OcpCmd.IDLE
      baseAddress := 0.U
    }

    // When slave signals that it accepted the data
    // we should provide it with a new one or end burst
    when (io.ocpMaster.S.DataAccept(0)) {
      when (burstCounter =/= 0.U) {
        nextBaseAddr := nextBaseAddr + 1.U
        burstCounter := burstCounter - 1.U
        burstDataReg := io.dma.busDataOut
        byteEnReg    := Mux(io.dma.dmaValid, 15.U, 0.U)
        dataValidReg := io.dma.dmaValid
        io.dma.busReady := true.B

      // If burstCounter is 0, the last data word is on bus
      } .otherwise {
        nextBaseAddr := nextBaseAddr + 1.U
        stateReg := idle
        burstDataReg := 0.U
        byteEnReg    := 0.U
        dataValidReg := false.B
        io.dma.busReady := false.B
        when (io.dma.busBurstLen =/= 0.U) {
          continue := true.B
        } .otherwise {
          continue := false.B
        }
      }

    // Data accept can be low if slave needs some
    // time mid-burst: hold last data and control
    // but don't accept new data from DMA
    } .otherwise {
      io.dma.busReady := false.B
    }

  } .otherwise {
    stateReg := idle
  }

  // Driving outputs from registers
  // - OCP bus
  io.ocpMaster.M.Cmd  := burstCmdReg
  io.ocpMaster.M.Addr := baseAddress
  io.ocpMaster.M.Data := burstDataReg
  io.ocpMaster.M.DataByteEn := byteEnReg
  io.ocpMaster.M.DataValid  := dataValidReg
  // - DMA interface
  io.dma.busValid  := busValidReg
  io.dma.busDataIn := burstSData
}
