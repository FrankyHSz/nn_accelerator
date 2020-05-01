package ocp

import Chisel._
import chisel3.iotesters.PeekPokeTester

class BusInterfaceTester(dut: BusInterface) extends PeekPokeTester(dut) {

  // Slave port tests
  // ----------------

  // Write tests
  println("[BusInterfaceTester] Write tests on slave port...")
  idleBus
  step(1)
  testRegisterWrite(MAIN_BLOCK, STATUS)
  step(1)
  idleBus
  step(1)
  testRegisterWrite(MAIN_BLOCK, ERR_CAUSE)
  step(1)
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterWrite(CMD_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterWrite(LD_ADDR_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterWrite(LD_SIZE_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }

  // Read tests
  println("[BusInterfaceTester] Read tests on slave port...")
  idleBus
  step(1)
  testRegisterRead(MAIN_BLOCK, STATUS)
  step(1)
  idleBus
  step(1)
  testRegisterRead(MAIN_BLOCK, ERR_CAUSE)
  step(1)
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterRead(CMD_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterRead(LD_ADDR_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }
  idleBus
  step(1)
  for (offset <- 0 until blockSize) {
    testRegisterRead(LD_SIZE_BLOCK, offset)
    step(1)
    idleBus
    step(1)
  }

  // Master port tests
  // -----------------

  var tuple2 = (0, 0)
  var tuple3 = (0, 0, 0)
  var firstData   = 0
  var expAddress  = 0
  var burstLength = 0

  // Write tests
  println("[BusInterfaceTester] Write tests on master port...")

  idleDMAAndSlave
  expectIdleBus
  step(1)
  tuple3 = requestShortWrite
  step(1)
  firstData   = tuple3._1
  expAddress  = tuple3._2
  burstLength = tuple3._3
  expectWriteBurst(firstData, expAddress, burstLength)
  expectIdleBus
  step(1)
  tuple3 = requestLongWrite
  firstData   = tuple3._1
  expAddress  = tuple3._2
  burstLength = tuple3._3
  step(1)
  expectMultipleWriteBursts(firstData, expAddress, burstLength)
  expectIdleBus

  // Read tests
  println("[BusInterfaceTester] Read tests on master port...")

  step(10)
  idleDMAAndSlave
  expectIdleBus
  step(1)
  tuple2 = requestShortRead
  expAddress  = tuple2._1
  burstLength = tuple2._2
  step(1)
  expectReadBurst(expAddress, burstLength)
  step(1)
  expectIdleBus
  step(1)
  tuple2 = requestLongRead
  expAddress  = tuple2._1
  burstLength = tuple2._2
  step(1)
  println("Multiple read bursts")
  expectMultipleReadBursts(expAddress, burstLength)
  expectIdleBus


  // Test steps in detail
  // --------------------

  def idleBus: Unit = {
    poke(dut.io.ocpSlave.M.Cmd, OcpCmd.IDLE)
    poke(dut.io.ocpSlave.M.Addr, 0.U)
    poke(dut.io.ocpSlave.M.Data, 0.U)
    // - RD/WR interface for register files
    expect(dut.io.addr, 0.U)
    expect(dut.io.wrData, 0.U)
    expect(dut.io.rdWrN, true.B)
    // - Decoded block select signals from Bus Interface
    expect(dut.io.statusSel, false.B)
    expect(dut.io.errCauseSel, false.B)
    expect(dut.io.commandSel, false.B)
    expect(dut.io.ldAddrSel, false.B)
    expect(dut.io.ldSizeSel, false.B)
    step(1)
    expect(dut.io.ocpSlave.S.Resp, OcpResp.NULL)
    expect(dut.io.ocpSlave.S.Data, 0.U)
  }

  def testRegisterWrite(block: Int, offset: Int): Unit = {
    poke(dut.io.ocpSlave.M.Cmd, OcpCmd.WR)
    val regAddress = IP_BASE_ADDRESS + block * blockSize + offset
    poke(dut.io.ocpSlave.M.Addr, regAddress.U)
    poke(dut.io.ocpSlave.M.Data, 42.U)
    // - RD/WR interface for register files
    expect(dut.io.addr, offset)
    expect(dut.io.wrData, 42.U)
    expect(dut.io.rdWrN, false.B)
    // - Decoded block select signals from Bus Interface
    expect(dut.io.statusSel, (block == MAIN_BLOCK && offset == STATUS).B)
    expect(dut.io.errCauseSel, (block == MAIN_BLOCK && offset == ERR_CAUSE).B)
    expect(dut.io.commandSel, (block == CMD_BLOCK).B)
    expect(dut.io.ldAddrSel, (block == LD_ADDR_BLOCK).B)
    expect(dut.io.ldSizeSel, (block == LD_SIZE_BLOCK).B)
    step(1)
    expect(dut.io.ocpSlave.S.Resp, OcpResp.DVA)
    expect(dut.io.ocpSlave.S.Data, 0.U)
  }

  def testRegisterRead(block: Int, offset: Int): Unit = {
    poke(dut.io.ocpSlave.M.Cmd, OcpCmd.RD)
    val regAddress = IP_BASE_ADDRESS + block * blockSize + offset
    poke(dut.io.ocpSlave.M.Addr, regAddress.U)
    poke(dut.io.ocpSlave.M.Data, 13.U)  // Ignored during read
    // - RD/WR interface for register files
    expect(dut.io.addr, offset)
    expect(dut.io.wrData, 0.U)  // Write data is ignored during read
    expect(dut.io.rdWrN, true.B)
    // - Decoded block select signals from Bus Interface
    expect(dut.io.statusSel, (block == MAIN_BLOCK && offset == STATUS).B)
    expect(dut.io.errCauseSel, (block == MAIN_BLOCK && offset == ERR_CAUSE).B)
    expect(dut.io.commandSel, (block == CMD_BLOCK).B)
    expect(dut.io.ldAddrSel, (block == LD_ADDR_BLOCK).B)
    expect(dut.io.ldSizeSel, (block == LD_SIZE_BLOCK).B)
    // - Before clock edge and read emulation
    expect(dut.io.ocpSlave.S.Resp, OcpResp.NULL)
    expect(dut.io.ocpSlave.S.Data, 0.U)
    step(1)
    poke(dut.io.rdData, 42.U)
    // - After clock edge and read emulation
    expect(dut.io.ocpSlave.S.Resp, OcpResp.DVA)
    expect(dut.io.ocpSlave.S.Data, 42.U)
  }

  def idleDMAAndSlave: Unit = {
    // - DMA
    poke(dut.io.busAddr, 0.U)
    poke(dut.io.busBurstLen, 0.U)
    poke(dut.io.busDataOut, 0.U)
    poke(dut.io.dmaReady, false.B)
    poke(dut.io.dmaValid, false.B)
    // - OcpBurstMasterPort.S
    poke(dut.io.ocpMaster.S.Resp, OcpResp.NULL)
    poke(dut.io.ocpMaster.S.Data, 0.U)
    poke(dut.io.ocpMaster.S.CmdAccept, false.B)
    poke(dut.io.ocpMaster.S.DataAccept, false.B)

    expect(dut.io.busReady, false.B)
    expect(dut.io.busValid, false.B)
  }

  def expectIdleBus: Unit = {
    expect(dut.io.ocpMaster.M.Cmd, OcpCmd.IDLE)
    expect(dut.io.ocpMaster.M.Addr, 0.U)
    expect(dut.io.ocpMaster.M.Data, 0.U)
    expect(dut.io.ocpMaster.M.DataByteEn, 0.U)
    expect(dut.io.ocpMaster.M.DataValid, false.B)
  }

  def requestShortWrite: (Int, Int, Int) = {
    val shortBurstLen = 1 * burstLen
    poke(dut.io.busAddr, 42.U)
    poke(dut.io.busBurstLen, shortBurstLen.U)
    poke(dut.io.busDataOut, 42.U)
    poke(dut.io.dmaReady, false.B)
    poke(dut.io.dmaValid, true.B)
    return (42, 42, shortBurstLen)
  }

  def expectWriteBurst(expData0: Int, expAddress: Int, len: Int): Unit = {
    var expData = expData0
    var length  = len
    for (cycle <- 0 until burstLen) {

      // OCP bus
      val expCmd = if (cycle == 0) OcpCmd.WR else OcpCmd.IDLE
      expect(dut.io.ocpMaster.M.Cmd, expCmd)
      val expAddr = if (cycle == 0) expAddress else 0
      expect(dut.io.ocpMaster.M.Addr, expAddr)
      expect(dut.io.ocpMaster.M.Data, expData)
      expect(dut.io.ocpMaster.M.DataByteEn, 15.U)
      expect(dut.io.ocpMaster.M.DataValid, true.B)

      // Emulating slave
      poke(dut.io.ocpMaster.S.Resp, OcpResp.NULL)
      poke(dut.io.ocpMaster.S.Data, 0.U)
      poke(dut.io.ocpMaster.S.CmdAccept, (cycle == 0))
      poke(dut.io.ocpMaster.S.DataAccept, true.B)

      // DMA interface
      expect(dut.io.busReady, (cycle != burstLen-1).B)

      // Emulating DMA
      expData += 1
      length = if (length > 0) length-1 else 0
      poke(dut.io.busDataOut, expData.U)
      poke(dut.io.dmaValid, (length != 0).B)
      poke(dut.io.busBurstLen, length.U)

      step(1)
    }

    // OCP bus
    expectIdleBus

    // DMA interface
    expect(dut.io.busReady, false.B)

  }

  def requestLongWrite: (Int, Int, Int) = {
    val longBurstLen = 5 * burstLen
    poke(dut.io.busAddr, 42.U)
    poke(dut.io.busBurstLen, longBurstLen.U)
    poke(dut.io.busDataOut, 42.U)
    poke(dut.io.dmaReady, false.B)
    poke(dut.io.dmaValid, true.B)
    return (42, 42, longBurstLen)
  }

  def expectMultipleWriteBursts(expData0: Int, expAddress: Int, len: Int): Unit = {
    val cycles = if (len%burstLen == 0) len/burstLen else len/burstLen + 1
    var expAddr = expAddress
    var expFirstData = expData0
    var length = len
    for (_ <- 1 to cycles) {
      expectWriteBurst(expFirstData, expAddr, length)
      step(1)
      expAddr += burstLen
      expFirstData += burstLen
      length -= burstLen
    }
  }

  def requestShortRead: (Int, Int) = {
    val shortBurstLen = 1 * burstLen
    poke(dut.io.busAddr, 42.U)
    poke(dut.io.busBurstLen, shortBurstLen.U)
    poke(dut.io.busDataOut, 0.U)
    poke(dut.io.dmaReady, true.B)
    poke(dut.io.dmaValid, false.B)
    return (42, shortBurstLen)
  }

  def expectReadBurst(expAddress: Int, len: Int): Unit = {
    var expData = expAddress
    var length = len
    for (cycle <- 0 to burstLen) {

      // OCP bus
      val expCmd = if (cycle == 0) OcpCmd.RD else OcpCmd.IDLE
      expect(dut.io.ocpMaster.M.Cmd, expCmd)
      val expAddr = if (cycle == 0) expAddress else 0
      expect(dut.io.ocpMaster.M.Addr, expAddr)
      expect(dut.io.ocpMaster.M.Data, 0.U)
      expect(dut.io.ocpMaster.M.DataByteEn, 0.U)
      expect(dut.io.ocpMaster.M.DataValid, false.B)

      // Emulating slave
      if (cycle == 0) {
        poke(dut.io.ocpMaster.S.Resp, OcpResp.NULL)
        poke(dut.io.ocpMaster.S.Data, 0.U)
        poke(dut.io.ocpMaster.S.CmdAccept, true.B)
        poke(dut.io.ocpMaster.S.DataAccept, false.B)
      } else {
        poke(dut.io.ocpMaster.S.Resp, OcpResp.DVA)
        poke(dut.io.ocpMaster.S.Data, expData.U)
        poke(dut.io.ocpMaster.S.CmdAccept, false.B)
        poke(dut.io.ocpMaster.S.DataAccept, false.B)
      }

      // DMA interface
      expect(dut.io.busValid, (cycle > 1).B)
      val dmaData = if (cycle > 1) expData-1 else 0
      expect(dut.io.busDataIn, dmaData)

      // Emulating DMA
      if (cycle != 0) expData += 1
      length = if (length > 0) length-1 else 0
      poke(dut.io.dmaReady, (length != 0).B)
      poke(dut.io.busBurstLen, length.U)

      step(1)
    }

    poke(dut.io.ocpMaster.S.Resp, OcpResp.NULL)
    poke(dut.io.ocpMaster.S.Data, 0.U)

    step(1)

  }

  def requestLongRead: (Int, Int) = {
    val longBurstLen = 5 * burstLen
    poke(dut.io.busAddr, 42.U)
    poke(dut.io.busBurstLen, longBurstLen.U)
    poke(dut.io.busDataOut, 0.U)
    poke(dut.io.dmaReady, true.B)
    poke(dut.io.dmaValid, false.B)
    return (42, longBurstLen)
  }

  def expectMultipleReadBursts(expAddress: Int, len: Int): Unit = {
    val cycles = if (len%burstLen == 0) len/burstLen else len/burstLen + 1
    var expAddr = expAddress
    var length = len
    for (_ <- 1 to cycles) {
      expectReadBurst(expAddr, length)
      step(1)
      expAddr += burstLen
      length -= burstLen
    }
  }
}
