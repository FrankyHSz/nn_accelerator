package ocp

import chisel3._
import chisel3.util.log2Up

class OcpBurstMasterBFM extends Module {
  val io = IO(new Bundle() {
    val ocpMaster = new OcpBurstMasterPort(addrWidth, dataWidth, burstLen)
  })

  // This bus functional model issues a read or a write command periodically
  // And to do this, it has an counter to wait between requests
  val delayCounter = RegInit(0.U(log2Up(requestPeriod).W))
  delayCounter := delayCounter + 1.U

  // Issuing commands when counter equals to "offset"
  val nextCmd = RegInit("b001".U(3.W))
  when (delayCounter === offset.U) {
    nextCmd := Mux(nextCmd === OcpCmd.WR, OcpCmd.RD, OcpCmd.WR)
  }
  io.ocpMaster.M.Cmd := Mux(delayCounter === offset.U, nextCmd, OcpCmd.IDLE)

  // Providing address for both read and write commands
  val rdAddrReg = RegInit(0.U(addrWidth.W))
  when (delayCounter === offset.U && nextCmd === OcpCmd.RD) {
    rdAddrReg := rdAddrReg + 1.U
  }
  val wrAddrReg = RegInit(0.U(addrWidth.W))
  when (delayCounter === offset.U && nextCmd === OcpCmd.WR) {
    wrAddrReg := wrAddrReg + 1.U
  }
  val address = Mux(nextCmd === OcpCmd.RD, rdAddrReg, wrAddrReg)
  io.ocpMaster.M.Addr := Mux(delayCounter === offset.U, address, 0.U)

  // Providing data, byte enable and data valid for write bursts
  val dataReg = RegInit(0.U(dataWidth.W))
  io.ocpMaster.M.Data := 0.U
  io.ocpMaster.M.DataByteEn := 0.U
  io.ocpMaster.M.DataValid := false.B
  // - First data word is provided along the write command and address at time "offset"
  when ((delayCounter === offset.U && nextCmd === OcpCmd.WR) ||
  // - Successive words in write bursts are provided if the burst is not ended yet.
  //   Note that nextCmd is read, not write (see command generation).
    (delayCounter > offset.U) && (delayCounter < (offset+burstLen).U && nextCmd === OcpCmd.RD)) {
    io.ocpMaster.M.Data := dataReg
    dataReg := dataReg + 1.U
    io.ocpMaster.M.DataByteEn := 15.U  // "1111"
    io.ocpMaster.M.DataValid := true.B
  }
}
