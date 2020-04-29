package ocp

import chisel3._
import chisel3.util.log2Up

class OcpBurstMemory extends Module {
  val io = IO(new Bundle() {
    val ocpSlave = new OcpBurstSlavePort(addrWidth, dataWidth, burstLen)
  })

  val memory = SyncReadMem(sysMemSize, UInt(dataWidth.W))

  // Helper registers
  // ----------------

  // Register to hold the last meaningful (!) command of master throughout bursts
  val lastCmd = RegInit(0.U(3.W))
  when (io.ocpSlave.M.Cmd === OcpCmd.RD) {
    lastCmd := OcpCmd.RD
  } .elsewhen (io.ocpSlave.M.Cmd === OcpCmd.WR) {
    lastCmd := OcpCmd.WR
  }

  // In case of a read or write operation, burst counter is set to burstLen.
  // then it is decremented until the end of burst
  val burstCounter = RegInit(0.U(log2Up(burstLen+1).W))
  when (io.ocpSlave.M.Cmd === OcpCmd.RD || io.ocpSlave.M.Cmd === OcpCmd.WR) {
    burstCounter := burstLen.U
  } .elsewhen (burstCounter =/= 0.U) {
    burstCounter := burstCounter - 1.U
  }

  // Slave control signal generation
  // -------------------------------

  // Slave response, based on burstCounter and lastCmd
  val respReg = RegInit(OcpResp.NULL)
  respReg := OcpResp.NULL                   // Default value for S.Resp
  when (io.ocpSlave.M.Cmd === OcpCmd.RD) {  // If a read is issued,
    respReg := OcpResp.DVA                  // then S.Resp should be DVA in the next cycle
  } .elsewhen(lastCmd === OcpCmd.RD) {      // If the last command was read
    when (burstCounter > 1.U) {             // and it is still not the last word of the burst
      respReg := OcpResp.DVA                // then S.Resp should still be DVA in the next cycle
    }
  }
  when (lastCmd === OcpCmd.WR) {            // If the last command was write
    when (burstCounter === 2.U) {           // and the master writes the last word
      respReg := OcpResp.DVA                // (4: second word, 2: last word, 1: S.Resp=DVA)
    }                                       // then S.Resp should be DVA in the next cycle
  }
  io.ocpSlave.S.Resp := respReg

  // Slave command accept: immediate for both read and write
  io.ocpSlave.S.CmdAccept := (io.ocpSlave.M.Cmd === OcpCmd.RD) || (io.ocpSlave.M.Cmd === OcpCmd.WR)

  // Slave data accept: immediate for every word in burst
  io.ocpSlave.S.DataAccept := io.ocpSlave.M.DataValid

  // Read/write logic
  // ----------------

  // Address generation for both read and write bursts
  val addressReg = RegInit(0.U(addrWidth.W))
  addressReg := 0.U
  when (io.ocpSlave.M.Cmd === OcpCmd.RD || io.ocpSlave.M.Cmd === OcpCmd.WR) {
    addressReg := io.ocpSlave.M.Addr + 1.U  // Address reg. is loaded with the address of second word
  } .elsewhen (burstCounter > 1.U) {
    addressReg := addressReg + 1.U
  }

  // Handling read bursts, driving S.Data
  // - Read address selection
  val address = Wire(UInt(addrWidth.W))
  address := io.ocpSlave.M.Addr
  when(lastCmd === OcpCmd.RD || lastCmd === OcpCmd.WR) {
    when (burstCounter > 1.U) {
      address := addressReg
    }
  }
  val memOutDataWire = Wire(UInt(dataWidth.W))
  memOutDataWire := memory.read(address)
  // Driving the S.Data only if necessary
  io.ocpSlave.S.Data := Mux(lastCmd === OcpCmd.RD && burstCounter =/= 0.U, memOutDataWire, 0.U)

  // Handling write bursts (there is no need for burstCounter because of M.DataValid)
  // - If dataValid goes low before end of burst ("partial write burst") then it
  //   is not a problem. If DataValid is active after predefined burst length, then
  //   those words will all be written to address 0, but this is a wrong behavior anyway.
  // - Byte enables are not supported (ignored) for this module, only whole 32-bit words
  //   can be written. The programmer could work around this by reading the original
  //   32-bit and masking the write data by [him/her]self.
  when (io.ocpSlave.M.DataValid === true.B) {
    memory.write(address, io.ocpSlave.M.Data)
  }
}
