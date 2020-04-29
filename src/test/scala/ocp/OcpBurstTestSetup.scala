package ocp

import chisel3._

class OcpBurstTestSetup extends Module {
  val io = IO(new Bundle() {
    val masterSignals = Output(new OcpBurstMasterSignals(addrWidth, dataWidth))
    val slaveSignals  = Output(new OcpBurstSlaveSignals(dataWidth))
  })

  // Modules under test
  val master = Module(new OcpBurstMasterBFM)
  val slave  = Module(new OcpBurstMemory)

  // Connecting them together
  slave.io.ocpSlave.M := master.io.ocpMaster.M
  master.io.ocpMaster.S := slave.io.ocpSlave.S

  // Connecting bus signals to output for monitoring
  io.masterSignals := master.io.ocpMaster.M
  io.slaveSignals  := slave.io.ocpSlave.S
}
