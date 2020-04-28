package ocp

import chisel3._

class BusInterface extends Module {

  // Helper constants
  val IP_BASE_ADDRESS_UPPER_BITS = IP_BASE_ADDRESS / (1 << IP_ADDRESS_WIDTH)

  val io = IO(new Bundle() {

    // Port towards OCP bus
    val ocpSlave = new OcpCoreSlavePort(addrWidth = addrWidth, dataWidth = dataWidth)

    // Signals from and toward Controller
    // - RD/WR interface for register files
    val addr   = Output(UInt(addrWidth.W))   // Decoded address (offset inside IP address space)
    val wrData = Output(UInt(dataWidth.W))
    val rdData = Input(UInt(dataWidth.W))
    val rdWrN  = Output(Bool())
    // - Decoded block select signals from Bus Interface
    val statusSel  = Output(Bool())
    val errCauseSel = Output(Bool())
    val commandSel = Output(Bool())
    val ldAddrSel  = Output(Bool())
    val ldSizeSel  = Output(Bool())

    // Signals from and toward DMA
    // ...
  })

  // Recognizing if this module is addressed or not
  val moduleSelect = WireDefault(false.B)
  when (io.ocpSlave.M.Addr(addrWidth-1, IP_ADDRESS_WIDTH) === IP_BASE_ADDRESS_UPPER_BITS.U) {
    moduleSelect := true.B
  }

  // Decoding block select signals
  // -----------------------------
  io.statusSel   := false.B
  io.errCauseSel := false.B
  io.commandSel  := false.B
  io.ldAddrSel   := false.B
  io.ldSizeSel   := false.B
  // When this module is selected and the block address is 0...
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 0.U) {
    // ... then it is either the control/status register's address...
    when (io.ocpSlave.M.Addr(0) === 0.U) {
      io.statusSel := true.B
    // ... or the error cause register's address.
    } .otherwise {
      io.errCauseSel := true.B
    }
  }
  // When this module is selected and the block address
  // is 1 then the command register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 1.U) {
    io.commandSel := true.B
  }
  // When this module is selected and the block address
  // is 2 then the load address register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 2.U) {
    io.ldAddrSel := true.B
  }
  // When this module is selected and the block address
  // is 3 then the load address register file is addressed
  when (moduleSelect && io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-1, IP_ADDRESS_WIDTH-2) === 3.U) {
    io.ldSizeSel := true.B
  }

  // Handling RD/WR signals
  // ----------------------
  // Propagate address if this module is selected and a read or write operation is requested
  io.addr := 0.U
  when (moduleSelect && (io.ocpSlave.M.Cmd === OcpCmd.WR || io.ocpSlave.M.Cmd === OcpCmd.RD)) {
    io.addr := io.ocpSlave.M.Addr(IP_ADDRESS_WIDTH-3, 0)
  }
  // Translate read/write signals
  io.rdWrN := true.B
  when (moduleSelect && io.ocpSlave.M.Cmd === OcpCmd.WR) {
    io.rdWrN := false.B
  }
  // Propagate write data if this module is selected and it is a write operation
  io.wrData := 0.U
  when (moduleSelect && io.ocpSlave.M.Cmd === OcpCmd.WR) {
    io.wrData := io.ocpSlave.M.Data
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
    io.ocpSlave.S.Data := io.rdData                                  // to match with data from registers
  }
}
