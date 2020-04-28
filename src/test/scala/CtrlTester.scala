
import chisel3._
import chisel3.iotesters.PeekPokeTester

class CtrlTester(dut: Controller) extends PeekPokeTester(dut) {

  val regMap = dut.getRegMap
  println("[CtrlTester] Register Address Map:")
  for ((k,v) <- regMap) println("[CtrlTester]  " + k + ": " + v)

  // Write-and-read-back tests
  // -------------------------
  println("[CtrlTester] Write-and-read-back testing...")
  writeAndReadBack_Status
  writeAndReadBack_CmdRegFile
  writeAndReadBack_LoadAddrRegFile
  writeAndReadBack_LoadSizeRegFile

  // State machine tests with dummy commands
  // ---------------------------------------
  println("[CtrlTester] State machine testing with dummy commands...")

  // Helper values and variables
  val resetWord = (1 << dut.qEmp)
  val startWord = (1 << dut.chEn)
  val dummyCmd = dut.DUMMY
  var cmdPtr = 1
  var expStatus = (1 << dut.chEn) + (1 << dut.qEmp)

  // Emulating DMA (no interaction in case of dummy commands)
  poke(dut.io.dma.done, true.B)

  resetFSM
  writeDummyCommands
  startFSM
  checkFSM_Dummy

  // State machine and DMA interface tests with load commands
  // --------------------------------------------------------
  println("[CtrlTester] State machine and DMA interface testing with load commands...")

  // Load parameters
  val loadACmd = dut.LOAD_A
  val loadBCmd = dut.LOAD_B
  val busBaseAddrA = Array(0, 42, 1024)
  val busBaseAddrB = Array(0, 13, 2048)
  val localBaseAddrA = Array(20, 0, 99)
  val localBaseAddrB = Array(0, 512, 42)
  val burstSizeA = Array(10, 123, 512)
  val burstSizeB = Array(9, 131, 256)
  val loadConfigs = 3  // Should match with the size of arrays above

  // Emulating DMA
  poke(dut.io.dma.done, true.B)

  resetFSM
  writeLoadCommands
  writeBaseAddresses
  writeBurstSizes
  startFSM
  checkFSM_Load

  // Testing error handling capabilities of the state machine
  // --------------------------------------------------------
  println("[CtrlTester] Testing error handling capabilities of the state machine...")

  val invalidCmd = 99

  resetFSM
  writeInvalidCommand
  startFSM
  checkFSM_invalidCmd

  resetFSM
  writeLoadCommands
  // Don't write base addresses
  writeBurstSizes
  startFSM
  checkFSM_noLoadAddress

  resetFSM
  writeLoadCommands
  writeBaseAddresses
  // Don't write burst sizes
  startFSM
  checkFSM_noBurstLen

  // --------------------
  // Test steps in detail
  // --------------------

  // Write-and-read-back tests
  // -------------------------

  def writeAndReadBack_Status: Unit = {
    // Status register
    poke(dut.io.statusSel, true.B)   // Important
    poke(dut.io.addr, dut.STATUS.U)  // Optional
    // - Write
    // Sent     Word: the 2 least significant bytes are set to 1 (except chip enable)
    // Received Word: only writable bits will change
    val sentWord = (1 << 16) - 2
    val recvWord = (1 << dut.qEmp) + (1 << dut.itEn) + (1 << dut.acEn)
    poke(dut.io.wrData, sentWord.U)
    poke(dut.io.rdWrN, false.B)
    expect(dut.io.statusReg, 0.U)
    step(1)
    expect(dut.io.statusReg, recvWord.U)
    // - Read back: expect success
    poke(dut.io.rdWrN, true.B)
    step(1)
    expect(dut.io.rdData, recvWord.U)
    // Don't forget to deactivate register select after test
    poke(dut.io.statusSel, false.B)
  }

  def writeAndReadBack_CmdRegFile: Unit = {
    // Command register file and Command Valid register
    poke(dut.io.commandSel, true.B) // Important
    // - Write
    poke(dut.io.rdWrN, false.B)
    var cmdValidRef = 0
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.CMD_RF_B + i).U) // Important
      poke(dut.io.wrData, (42 + i).U)
      expect(dut.io.commandRF(i), 0.U)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.cmdValid(bit), (cmdValidRef.U)(bit))
      step(1)
      cmdValidRef = (cmdValidRef << 1) + 1
      expect(dut.io.commandRF(i), (42 + i).U)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.cmdValid(bit), (cmdValidRef.U)(bit))
    }
    // - Read back: expecting 0s because these registers are write-only
    poke(dut.io.rdWrN, true.B)
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.CMD_RF_B + i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.cmdValid(bit), (cmdValidRef.U)(bit))  // Registers are still
      expect(dut.io.commandRF(i), (42 + i).U)               // holding their values
      expect(dut.io.rdData, 0.U)                            // but rdData is 0
    }
    // Don't forget to deactivate register select after test
    poke(dut.io.commandSel, false.B)
  }

  def writeAndReadBack_LoadAddrRegFile: Unit = {
    // Load Address register file and Load Address Valid register
    poke(dut.io.ldAddrSel, true.B) // Important
    // - Write
    poke(dut.io.rdWrN, false.B)
    var ldAddrValidRef = 0
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.LD_ADDR_B + i).U) // Important
      poke(dut.io.wrData, (42 + i).U)
      expect(dut.io.loadAddrRF(i), 0.U)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (ldAddrValidRef.U)(bit))
      step(1)
      ldAddrValidRef = (ldAddrValidRef << 1) + 1
      expect(dut.io.loadAddrRF(i), (42 + i).U)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (ldAddrValidRef.U)(bit))
    }
    // - Read back: expecting 0s because these registers are write-only
    poke(dut.io.rdWrN, true.B)
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.LD_ADDR_B + i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (ldAddrValidRef.U)(bit))  // Registers are still
      expect(dut.io.loadAddrRF(i), (42 + i).U)                 // holding their values
      expect(dut.io.rdData, 0.U)                               // but rdData is 0
    }
    // Don't forget to deactivate register select after test
    poke(dut.io.ldAddrSel, false.B)
  }

  def writeAndReadBack_LoadSizeRegFile: Unit = {
    // Load Size (burst length) register file and Load Size Valid register
    poke(dut.io.ldSizeSel, true.B) // Important
    // - Write
    poke(dut.io.rdWrN, false.B)
    var ldSizeValidRef = 0
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.LD_SIZE_B + i).U) // Important
      poke(dut.io.wrData, (42 + i).U)
      expect(dut.io.loadSizeRF(i), 0.U)
      for (j <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(i), (ldSizeValidRef.U)(i))
      step(1)
      ldSizeValidRef = (ldSizeValidRef << 1) + 1
      expect(dut.io.loadSizeRF(i), (42 + i).U)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (ldSizeValidRef.U)(bit))
    }
    // - Read back: expecting 0s because these registers are write-only
    poke(dut.io.rdWrN, true.B)
    for (i <- 0 until 5) {
      poke(dut.io.addr, (dut.LD_SIZE_B + i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (ldSizeValidRef.U)(bit))  // Registers are still
      expect(dut.io.loadSizeRF(i), (42 + i).U)                 // holding their values
      expect(dut.io.rdData, 0.U)                               // but rdData is 0
    }
    // Don't forget to deactivate register select after test
    poke(dut.io.ldSizeSel, false.B)
  }

  // Common functions for FSM tests
  // ------------------------------

  def resetFSM: Unit = {
    // Clearing all enable signals and flags
    // - resetWord: Everything is 0 except queue-empty bit which invalidates every command
    // val resetWord = (1 << dut.qEmp)
    poke(dut.io.statusSel, true.B)
    poke(dut.io.wrData, resetWord.U)
    poke(dut.io.rdWrN, false.B)
    step(1)
    poke(dut.io.statusSel, false.B)
    poke(dut.io.rdWrN, true.B)
    step(1)                              // Reset needs two cycles to take full effect
    expect(dut.io.statusReg, resetWord)  // because of signal propagation (error flag in status)
  }

  def startFSM: Unit = {
    // Starting FSM and inspecting it as it "processes" the commands
    // - startWord: Sets chip enable to start operations
    // val startWord = (1 << dut.chEn)
    poke(dut.io.statusSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.wrData, startWord.U)
    expect(dut.io.stateReg, dut.idle)     // FSM starts from idle state
    step(1)
    poke(dut.io.statusSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  // FSM test with dummy commands
  // ----------------------------

  def writeDummyCommands: Unit = {
    // Writing commands
    // val dummyCmd = dut.DUMMY
    poke(dut.io.commandSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, dummyCmd.U)
    // - Before clock edge, everything remains in reset
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), false)
    step(1)
    // - After first clock edge, only cmdValid(0) changes to true
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    step(1)
    // - After second clock edge, statusReg's queue-empty bit changes to 0
    expect(dut.io.statusReg, 0.U)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    // - Writing 10 additional commands
    for (i <- 1 to 10) {
      poke(dut.io.addr, i.U)
      poke(dut.io.wrData, dummyCmd.U)
      step(1)
    }
    // - Expecting active valid bits for all of them (0 to 10)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i<=10))
    poke(dut.io.commandSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def checkFSM_Dummy: Unit = {
    expect(dut.io.stateReg, dut.idle)     // FSM is still in idle state because signal propagation
    step(1)                               // takes two cycles: io.wrData -> statusReg -> stateReg
    expect(dut.io.stateReg, dut.fetch)    // FSM is in fetch state after one clock edge
    step(1)
    expect(dut.io.stateReg, dut.decode)   // FSM is in decode state after two clock edges
    expect(dut.io.currCommand, dummyCmd)  // 0th command is in current-command register
    expect(dut.io.cmdPtr, 1.U)            // cmdPtr points to the next (1st) command
    step(1)
    expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
    expect(dut.io.currCommand, dummyCmd)  // Current-command register remains the same
    expect(dut.io.cmdPtr, 1.U)            // cmdPtr remains the same
    // - This repeats until no valid command rem
    // var cmdPtr = 1
    while (toBoolean(peek(dut.io.cmdValid(cmdPtr)), 0)) {
      step(1)
      expect(dut.io.stateReg, dut.fetch)
      cmdPtr += 1
      step(1)
      expect(dut.io.stateReg, dut.decode)
      expect(dut.io.currCommand, dummyCmd)
      expect(dut.io.cmdPtr, cmdPtr)
      step(1)
      expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
      expect(dut.io.currCommand, dummyCmd)  // Current-command register remains the same
      expect(dut.io.cmdPtr, cmdPtr)         // cmdPtr remains the same
    }
    step(1)
    expect(dut.io.stateReg, dut.idle)  // First, the FSM goes to idle
    step(1)
    // var expStatus = (1 << dut.chEn) + (1 << dut.qEmp)
    expect(dut.io.statusReg, expStatus)  // Then queue-empty bit gets asserted
  }

  // FSM and DMA interface test with load commands
  // ---------------------------------------------

  def writeLoadCommands: Unit = {
    // Writing commands
    poke(dut.io.commandSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, loadACmd.U)
    // - Before clock edge, everything remains in reset
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), false)
    step(1)
    // - After first clock edge, only cmdValid(0) changes to true
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    step(1)
    // - After second clock edge, statusReg's queue-empty bit changes to 0
    expect(dut.io.statusReg, 0.U)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    // - Writing additional load commands
    for (i <- 1 until 2*loadConfigs) {
      poke(dut.io.addr, i.U)
      val loadCmd = if (i%2 == 0) loadACmd else loadBCmd
      poke(dut.io.wrData, loadCmd.U)
      step(1)
    }
    // - Expecting active valid bits for all of them
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i < 2*loadConfigs))
    poke(dut.io.commandSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def writeBaseAddresses: Unit = {
    // Writing base addresses: local address first, bus address second
    // - Writing first addresses
    poke(dut.io.ldAddrSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, localBaseAddrA(0).U)
    // - Before clock edge, everything remains in reset (invalid addresses)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), false)
    step(1)
    // - After first clock edge, only ldAValid(0) changes to true
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), (i==0))
    poke(dut.io.addr, 1.U)
    poke(dut.io.wrData, busBaseAddrA(0).U)
    step(1)
    // - After second clock edge, both ldAValid(0) and ldAValid(1) is true but not others
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), (i<2))
    // - Writing the other addresses
    for (i <- 1 until 2*loadConfigs) {
      poke(dut.io.addr, (2*i).U)
      val localBaseAddress = if (i%2 == 0) localBaseAddrA(i/2) else localBaseAddrB(i/2)
      poke(dut.io.wrData, localBaseAddress.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (bit < (2*i+1)))
      poke(dut.io.addr, (2*i+1).U)
      val busBaseAddress = if (i%2 == 0) busBaseAddrA(i/2) else busBaseAddrB(i/2)
      poke(dut.io.wrData, busBaseAddress.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (bit < (2*i+2)))
    }
    poke(dut.io.ldAddrSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def writeBurstSizes: Unit = {
    // Writing burst lengths
    poke(dut.io.ldSizeSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    for (i <- 0 until 2*loadConfigs) {
      poke(dut.io.addr, i.U)
      val burstLen = if (i%2 == 0) burstSizeA(i/2) else burstSizeB(i/2)
      poke(dut.io.wrData, burstLen.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < i+1))
    }
    poke(dut.io.ldSizeSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def checkFSM_Load: Unit = {
    expect(dut.io.stateReg, dut.idle)     // FSM is still in idle state because signal propagation
    step(1)                               // takes two cycles: io.wrData -> statusReg -> stateReg
    expect(dut.io.stateReg, dut.fetch)    // FSM is in fetch state after one clock edge
    step(1)
    expect(dut.io.stateReg, dut.decode)   // FSM is in decode state after two clock edges
    expect(dut.io.currCommand, loadACmd)  // 0th command is in current-command register
    expect(dut.io.cmdPtr, 1.U)            // cmdPtr points to the next (1st) command
    step(1)
    expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
    expect(dut.io.currCommand, loadACmd)  // Current-command register remains the same
    expect(dut.io.cmdPtr, 1.U)            // cmdPtr remains the same
    // - DMA interface is expected to be active
    expect(dut.io.dma.localBaseAddr, localBaseAddrA(0))
    expect(dut.io.dma.busBaseAddr, busBaseAddrA(0))
    expect(dut.io.dma.burstLen, burstSizeA(0))
    expect(dut.io.dma.start, true.B)
    poke(dut.io.dma.done, false.B)        // Emulating DMA
    step(1)
    expect(dut.io.stateReg, dut.execute)  // FSM expected to remain in execute state during DMA operation
    expect(dut.io.dma.start, false.B)     // Start is expected to go low after one clock cycle
    // - While DMA is not done, FSM should wait in execute state
    for (_ <- 0 until 10) {
      step(1)
      expect(dut.io.stateReg, dut.execute)
      expect(dut.io.dma.start, false.B)
    }
    poke(dut.io.dma.done, true.B)         // Emulating DMA
    step(1)
    expect(dut.io.stateReg, dut.fetch)    // FSM moved to fetch state after receiving done signal
    expect(dut.io.cmdValid(0), false.B)   // Expecting the first bit of cmdValid to be 0
    expect(dut.io.cmdValid(1), true.B)    // while the following command remains valid
    expect(dut.io.ldAValid(0), false.B)   // Expecting the first two bits of ldAValid to be 0
    expect(dut.io.ldAValid(1), false.B)   // while ldAValid(2) is still 1 (only first two bits
    expect(dut.io.ldAValid(2), true.B)    // got to be invalidated
    expect(dut.io.ldSValid(0), false.B)   // Expecting the first bit of ldSValid to be 0
    expect(dut.io.ldSValid(1), true.B)    // while ldSValid(1) is still 1
    // - This repeats until no valid commands remain
    cmdPtr = 1
    while (toBoolean(peek(dut.io.cmdValid(cmdPtr)), 0)) {
      expect(dut.io.stateReg, dut.fetch)
      cmdPtr += 1
      step(1)
      expect(dut.io.stateReg, dut.decode)
      val expCmd = if ((cmdPtr-1)%2 == 0) loadACmd else loadBCmd
      expect(dut.io.currCommand, expCmd)
      expect(dut.io.cmdPtr, cmdPtr.U)
      step(1)
      expect(dut.io.stateReg, dut.execute)
      expect(dut.io.currCommand, expCmd)
      expect(dut.io.cmdPtr, cmdPtr.U)
      // - DMA interface is expected to be active
      val expLocalAddress = if ((cmdPtr-1)%2 == 0) localBaseAddrA((cmdPtr-1)/2) else localBaseAddrB((cmdPtr-1)/2)
      expect(dut.io.dma.localBaseAddr, expLocalAddress)
      val expBusAddress = if ((cmdPtr-1)%2 == 0) busBaseAddrA((cmdPtr-1)/2) else busBaseAddrB((cmdPtr-1)/2)
      expect(dut.io.dma.busBaseAddr, expBusAddress)
      val expBurstLen = if ((cmdPtr-1)%2 == 0) burstSizeA((cmdPtr-1)/2) else burstSizeB((cmdPtr-1)/2)
      expect(dut.io.dma.burstLen, expBurstLen)
      expect(dut.io.dma.start, true.B)
      poke(dut.io.dma.done, false.B)        // Emulating DMA
      step(1)
      expect(dut.io.stateReg, dut.execute)  // FSM expected to remain in execute state during DMA operation
      expect(dut.io.dma.start, false.B)     // Start is expected to go low after one clock cycle
      // - While DMA is not done, FSM should wait in execute state
      for (_ <- 0 until 10) {
        step(1)
        expect(dut.io.stateReg, dut.execute)
        expect(dut.io.dma.start, false.B)
      }
      poke(dut.io.dma.done, true.B)         // Emulating DMA
      step(1)
    }
    expect(dut.io.stateReg, dut.idle)       // Expecting FSM to go idle after all commands were executed
    for (bit <- 0 until dut.getDataW) {     // Expecting that all validity registers are "empty" now
      expect(dut.io.cmdValid(bit), false.B)
      expect(dut.io.ldAValid(bit), false.B)
      expect(dut.io.ldSValid(bit), false.B)
    }
    step(1)
    expStatus = (1 << dut.chEn) + (1 << dut.qEmp)
    expect(dut.io.statusReg, expStatus)     // Expecting queue-empty signal to be active after one clock cycle
  }

  // Tests of error handling
  // -----------------------

  def writeInvalidCommand: Unit = {
    poke(dut.io.commandSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, invalidCmd.U)
    // - Before clock edge, everything remains in reset
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), false)
    step(1)
    // - After first clock edge, only cmdValid(0) changes to true
    expect(dut.io.statusReg, resetWord)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    step(1)
    // - After second clock edge, statusReg's queue-empty bit changes to 0
    expect(dut.io.statusReg, 0.U)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i==0))
    poke(dut.io.commandSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def checkFSM_invalidCmd: Unit = checkError(invalidCmd, dut.unknownCommand, expectQueueEmpty = true)

  def checkFSM_noLoadAddress: Unit = checkError(loadACmd, dut.noBaseAddress, expectQueueEmpty = false)

  def checkFSM_noBurstLen: Unit = checkError(loadACmd, dut.noSize, expectQueueEmpty = false)

  def checkError(expCommand: Int, expErrorCode: Int, expectQueueEmpty: Boolean): Unit = {
    expect(dut.io.stateReg, dut.idle)
    step(1)
    expect(dut.io.stateReg, dut.fetch)
    step(1)
    expect(dut.io.stateReg, dut.decode)
    expect(dut.io.currCommand, expCommand)  // First load command is under decoding
    expStatus = (1 << dut.chEn)
    expect(dut.io.statusReg, expStatus)     // Before clock edge, status is normal
    step(1)
    expect(dut.io.stateReg, dut.idle)       // FSM is back to idle state because of error
    // expStatus: only chip enable is active (queue not empty, see writeLoadCommands function)
    // Error can already be seen in errorCause register
    expStatus = (1 << dut.chEn) + (if (expectQueueEmpty) (1 << dut.qEmp) else 0)
    val expError = (1 << expErrorCode)
    expect(dut.io.statusReg, expStatus)
    expect(dut.io.errorCause, expError)
    step(1)
    // Error flag is raised now
    val one: BigInt = 1
    val expStatusU = (one << dut.erFl) + (1 << dut.chEn) + (if (expectQueueEmpty) (1 << dut.qEmp) else 0)
    expect(dut.io.statusReg, expStatusU)
  }


  // Helper functions
  def toBoolean(in: BigInt, bitPos: Int) = ((in >> bitPos) % 2) == 1
}
