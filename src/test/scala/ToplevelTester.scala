
import chisel3._
import chisel3.iotesters.PeekPokeTester
import ocp._
import Controller._

class ToplevelTester(dut: NNAccelerator) extends PeekPokeTester(dut) {

  // DUT parameters
  val memoryWidth = dut.getAddrW
  val dataWidth   = dut.getDataW

  // Useful constants
  val memoryDepth = 1 << memoryWidth
  val maxData     = 1 << dataWidth

  // Operands and expected output
  // ----------------------------

  val imageBaseAddress = 42  // Dummy address
  val image = Array(
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    +1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
  )

  val kernelBaseAddress = 13  // Dummy address
  val kernel = Array(
    -1, -1, -1,
    -1, +8, -1,
    -1, -1, -1,
    0, 0, 0, 0, 0, 0, 0
  )

  val outputBaseAddress = 99 // Dummy address
  val expectedOutput = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    2, +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    +6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
  )


  // Commands and operands for controller
  // ------------------------------------

  val commands = Array(LOAD_K, LOAD_B, SET_RELU, CONV_S, STORE)
  val addresses = Array(kernelBaseAddress, imageBaseAddress, outputBaseAddress)
  val sizes = Array(3, 3, 16, 16)


  // Test steps
  // ----------

  idleBus
  writeRegister(CMD_BLOCK, 0, commands(0))  // LOAD_K
  idleBus
  writeRegister(CMD_BLOCK, 1, commands(1))  // LOAD_B
  idleBus
  writeRegister(CMD_BLOCK, 2, commands(2))  // SET_RELU
  idleBus
  writeRegister(CMD_BLOCK, 3, commands(3))  // CONV_S
  idleBus
  writeRegister(CMD_BLOCK, 4, commands(4))  // STORE
  idleBus

  idleBus
  writeRegister(LD_ADDR_BLOCK, 0, addresses(0))  // kernelBaseAddress
  idleBus
  writeRegister(LD_ADDR_BLOCK, 1, addresses(1))  // imageBaseAddress
  idleBus
  writeRegister(LD_ADDR_BLOCK, 2, addresses(2))  // outputBaseAddress
  idleBus

  idleBus
  writeRegister(LD_SIZE_BLOCK, 0, sizes(0))  // 3
  idleBus
  writeRegister(LD_SIZE_BLOCK, 1, sizes(1))  // 3
  idleBus
  writeRegister(LD_SIZE_BLOCK, 2, sizes(2))  // 16
  idleBus
  writeRegister(LD_SIZE_BLOCK, 3, sizes(3))  // 16
  idleBus

  idleBus
  val startWord = (1 << chEn)
  writeRegister(MAIN_BLOCK, STATUS, startWord)  // Enable accelerator to start it
  idleBus

  waitOnOcpBurstCommand(cmd = OcpCmd.RD.toInt)  // Waiting for kernel read request from accelerator

  checkAndEmulateKernelRead                     // Checking and emulating OCP Burst read

  waitOnOcpBurstCommand(cmd = OcpCmd.RD.toInt)  // Waiting for image read request from accelerator

  checkAndEmulateImageRead                      // Checking and emulating OCP Burst read

  waitOnOcpBurstCommand(cmd = OcpCmd.WR.toInt)  // Waiting for output write request from accelerator

  checkAndEmulateOutputWrite


  // Test steps in detail
  // --------------------

  def idleBus: Unit = {
    poke(dut.io.ocpSlavePort.M.Cmd, OcpCmd.IDLE)
    poke(dut.io.ocpSlavePort.M.Addr, 0.U)
    poke(dut.io.ocpSlavePort.M.Data, 0.U)
    step(1)
    expect(dut.io.ocpSlavePort.S.Resp, OcpResp.NULL)
    expect(dut.io.ocpSlavePort.S.Data, 0.U)
  }

  def writeRegister(block: Int, offset: Int, data: Int): Unit = {
    poke(dut.io.ocpSlavePort.M.Cmd, OcpCmd.WR)
    val regAddress = IP_BASE_ADDRESS + block * blockSize + offset
    poke(dut.io.ocpSlavePort.M.Addr, regAddress.U)
    poke(dut.io.ocpSlavePort.M.Data, data.U)
    step(1)
    expect(dut.io.ocpSlavePort.S.Resp, OcpResp.DVA)
    expect(dut.io.ocpSlavePort.S.Data, 0.U)
  }

  def waitOnOcpBurstCommand(cmd: Int): Unit = {
    var timeOut = 1000
    while(peek(dut.io.ocpMasterPort.M.Cmd).toInt != cmd && timeOut != 0) {
      step(1)
      timeOut -= 1
      if (timeOut % 200 == 0) println("TimeOut is " + timeOut)
    }
    if (timeOut == 0) println("ERROR: Time-out while waiting for " + cmd + " (read is " + OcpCmd.RD + ")")
  }

  def checkAndEmulateKernelRead: Unit = {
    expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.RD)
    expect(dut.io.ocpMasterPort.M.Addr, kernelBaseAddress)
    poke(dut.io.ocpMasterPort.S.CmdAccept, true.B)
    poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
    for (cycle <- 0 until 4) {
      step(1)
      expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.IDLE)
      expect(dut.io.ocpMasterPort.M.Addr, 0)
      poke(dut.io.ocpMasterPort.S.CmdAccept, false.B)
      val dataWord = byteMerge(kernel(4*cycle+3), kernel(4*cycle+2), kernel(4*cycle+1), kernel(4*cycle))
      poke(dut.io.ocpMasterPort.S.Data, dataWord.U)
      poke(dut.io.ocpMasterPort.S.Resp, OcpResp.DVA)
    }
    poke(dut.io.ocpMasterPort.S.Data, 0.U)
    poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
  }

  def checkAndEmulateImageRead: Unit = {
    for (burstIdx <- 0 until 16) {
      if (burstIdx != 0)
        waitOnOcpBurstCommand(OcpCmd.RD.toInt)
      expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.RD)
      expect(dut.io.ocpMasterPort.M.Addr, imageBaseAddress+burstIdx*4)
      poke(dut.io.ocpMasterPort.S.CmdAccept, true.B)
      poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
      for (cycle <- 0 until 4) {
        step(1)
        expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.IDLE)
        expect(dut.io.ocpMasterPort.M.Addr, 0)
        poke(dut.io.ocpMasterPort.S.CmdAccept, false.B)
        val dataWord = byteMerge(
          image(16 * burstIdx + 4 * cycle + 3),
          image(16 * burstIdx + 4 * cycle + 2),
          image(16 * burstIdx + 4 * cycle + 1),
          image(16 * burstIdx + 4 * cycle)
        )
        poke(dut.io.ocpMasterPort.S.Data, dataWord.U)
        poke(dut.io.ocpMasterPort.S.Resp, OcpResp.DVA)
      }
      step(1)
      poke(dut.io.ocpMasterPort.S.Data, 0.U)
      poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
    }
  }

  def checkAndEmulateOutputWrite: Unit = {
    for (burstIdx <- 0 until 14) {
      if (burstIdx != 0)
        waitOnOcpBurstCommand(OcpCmd.WR.toInt)
      expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.WR)
      expect(dut.io.ocpMasterPort.M.Addr, outputBaseAddress+burstIdx*4)
      expect(dut.io.ocpMasterPort.M.DataByteEn, 15)
      expect(dut.io.ocpMasterPort.M.DataValid, true)
      val dataIdx = 4*burstIdx
      val byteMsb = Array(7, 15, 23, 31)
      val byteLsb = Array(0, 8, 16, 24)
      for (port <- 0 until 4)
        print(" " + (peek(dut.io.ocpMasterPort.M.Data) >> byteLsb(port)) % 256)
      val expData = byteMerge(
        expectedOutput(burstIdx*16 + 3),
        expectedOutput(burstIdx*16 + 2),
        expectedOutput(burstIdx*16 + 1),
        expectedOutput(burstIdx*16 + 0)
      )
      expect(dut.io.ocpMasterPort.M.Data, expData)
      poke(dut.io.ocpMasterPort.S.CmdAccept, true.B)
      poke(dut.io.ocpMasterPort.S.DataAccept, true.B)
      poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
      for (cycle <- 1 until 4) {
        step(1)
        expect(dut.io.ocpMasterPort.M.Cmd, OcpCmd.IDLE)
        expect(dut.io.ocpMasterPort.M.Addr, 0)
        val byteMsb = Array(7, 15, 23, 31)
        val byteLsb = Array(0, 8, 16, 24)
        for (port <- 0 until 4)
          print(" " + (peek(dut.io.ocpMasterPort.M.Data) >> byteLsb(port)) % 256)
        val expData = byteMerge(
          expectedOutput(burstIdx*16 + cycle*4 + 3),
          expectedOutput(burstIdx*16 + cycle*4 + 2),
          expectedOutput(burstIdx*16 + cycle*4 + 1),
          expectedOutput(burstIdx*16 + cycle*4 + 0)
        )
        expect(dut.io.ocpMasterPort.M.Data, expData)
        poke(dut.io.ocpMasterPort.S.CmdAccept, false.B)
        poke(dut.io.ocpMasterPort.S.DataAccept, true.B)
        poke(dut.io.ocpMasterPort.S.Resp, OcpResp.NULL)
      }
      print("\n")
      step(1)
      poke(dut.io.ocpMasterPort.S.Data, 0.U)
      poke(dut.io.ocpMasterPort.S.Resp, OcpResp.DVA)
    }
  }


  // Helper functions
  def toBoolean(in: BigInt) = (in % 2) == 1
  def toByte(in: Int) = in.toChar.toInt % 256
  def byteMerge(b3: Int, b2: Int, b1: Int, b0: Int): BigInt = {
    var msByte: BigInt = toByte(b3)
    (msByte << 24) + (toByte(b2) << 16) + (toByte(b1) << 8) + toByte(b0)
  }
}