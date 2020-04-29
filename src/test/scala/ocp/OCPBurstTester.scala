package ocp

import chisel3.iotesters.PeekPokeTester

// This tester exercises both OcpBurstMasterBFM and OcpBurstMemory at the same time.
class OCPBurstTester(dut: OcpBurstTestSetup) extends PeekPokeTester(dut) {

  // Test parameters
  val testLength = 10 * requestPeriod

  // Variables to hold reference values
  var lastCmd = OcpCmd.IDLE
  var expCmd = OcpCmd.WR
  var lastRdAddr = 0
  var expRdAddrReg = 0
  var lastWrAddr = 0
  var expWrAddrReg = 0
  var expDataReg = 0
  var refMemory = Array.fill(testLength) { 0 }

  // Expecting write-and-read-back traffic
  for (i <- 0 until testLength) {

    // Checking signals at start of burst
    if (i%requestPeriod == offset) {

      expect(dut.io.masterSignals.Cmd, expCmd)
      if (expCmd == OcpCmd.RD) {

        // Checking signals
        // - Master signals
        expect(dut.io.masterSignals.Addr, expRdAddrReg)
        expect(dut.io.masterSignals.Data, 0)
        expect(dut.io.masterSignals.DataByteEn, 0)
        expect(dut.io.masterSignals.DataValid, false)
        // - Slave signals
        expect(dut.io.slaveSignals.Resp, OcpResp.NULL)
        expect(dut.io.slaveSignals.Data, 0)
        expect(dut.io.slaveSignals.CmdAccept, true)
        expect(dut.io.slaveSignals.DataAccept, false)

        // Updating references
        lastCmd =  OcpCmd.RD
        expCmd = OcpCmd.WR
        lastRdAddr = expRdAddrReg
        expRdAddrReg += 1

      } else if (expCmd == OcpCmd.WR) {

        // Checking signals
        // - Master signals
        expect(dut.io.masterSignals.Addr, expWrAddrReg)
        expect(dut.io.masterSignals.Data, expDataReg)
        expect(dut.io.masterSignals.DataByteEn, 15)
        expect(dut.io.masterSignals.DataValid, true)
        // - Slave signals
        expect(dut.io.slaveSignals.Resp, OcpResp.NULL)
        expect(dut.io.slaveSignals.Data, 0)
        expect(dut.io.slaveSignals.CmdAccept, true)
        expect(dut.io.slaveSignals.DataAccept, true)

        // Updating references
        lastCmd =  OcpCmd.WR
        expCmd = OcpCmd.RD
        refMemory(expWrAddrReg) = expDataReg
        lastWrAddr = expWrAddrReg
        expWrAddrReg += 1
        expDataReg += 1
      }
    }
    // Checking signals during burst
    else if ((i%requestPeriod > offset) && (i%requestPeriod <= offset+burstLen)) {

      val wordIdx = i%requestPeriod - offset  // 1-2-3-4

      expect(dut.io.masterSignals.Cmd, OcpCmd.IDLE)
      if (lastCmd == OcpCmd.RD) {

        // Checking signals
        // - Master signals
        expect(dut.io.masterSignals.Addr, 0)
        expect(dut.io.masterSignals.Data, 0)
        expect(dut.io.masterSignals.DataByteEn, 0)
        expect(dut.io.masterSignals.DataValid, false)
        // - Slave signals
        expect(dut.io.slaveSignals.Resp, OcpResp.DVA)
        expect(dut.io.slaveSignals.Data, refMemory(lastRdAddr+wordIdx-1))  // 0-1-2-3
        expect(dut.io.slaveSignals.CmdAccept, false)
        expect(dut.io.slaveSignals.DataAccept, false)

      } else if (lastCmd == OcpCmd.WR) {

        if (wordIdx != 4) {
          // Checking signals
          // - Master signals
          expect(dut.io.masterSignals.Addr, 0)
          expect(dut.io.masterSignals.Data, expDataReg)
          expect(dut.io.masterSignals.DataByteEn, 15)
          expect(dut.io.masterSignals.DataValid, true)
          // - Slave signals
          expect(dut.io.slaveSignals.Resp, OcpResp.NULL)
          expect(dut.io.slaveSignals.Data, 0)
          expect(dut.io.slaveSignals.CmdAccept, false)
          expect(dut.io.slaveSignals.DataAccept, true)

          // Updating references
          refMemory(lastWrAddr + wordIdx) = expDataReg
          expDataReg += 1
        } else {
          // Checking signals
          // - Master signals
          expect(dut.io.masterSignals.Addr, 0)
          expect(dut.io.masterSignals.Data, 0)
          expect(dut.io.masterSignals.DataByteEn, 0)
          expect(dut.io.masterSignals.DataValid, false)
          // - Slave signals
          expect(dut.io.slaveSignals.Resp, OcpResp.DVA)
          expect(dut.io.slaveSignals.Data, 0)
          expect(dut.io.slaveSignals.CmdAccept, false)
          expect(dut.io.slaveSignals.DataAccept, false)
        }

      }
    }
    // Checking signals between bursts
    else {

      // Checking signals
      // - Master signals
      expect(dut.io.masterSignals.Cmd, OcpCmd.IDLE)
      expect(dut.io.masterSignals.Addr, 0)
      expect(dut.io.masterSignals.Data, 0)
      expect(dut.io.masterSignals.DataByteEn, 0)
      expect(dut.io.masterSignals.DataValid, false)
      // - Slave signals
      expect(dut.io.slaveSignals.Resp, OcpResp.NULL)
      expect(dut.io.slaveSignals.Data, 0)
      expect(dut.io.slaveSignals.CmdAccept, false)
      expect(dut.io.slaveSignals.DataAccept, false)

    }
    step(1)
  }

}
