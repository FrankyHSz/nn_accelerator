
import chisel3._
import chisel3.iotesters.PeekPokeTester

class CtrlTester(dut: Controller) extends PeekPokeTester(dut) {

  val regMap = dut.getRegMap
  println("[CtrlTester] Register Address Map:")
  for ((k,v) <- regMap) println("[CtrlTester]  " + k + ": " + v)

  // Emulating inactive LoadUnit and DMA
  poke(dut.io.computeDone, true.B)
  poke(dut.io.dma.done, true.B)

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
  val loadKCmd = dut.LOAD_K
  val busBaseAddrA = Array(0, 42, 1024)
  val busBaseAddrB = Array(0, 13, 2048)
  val busBaseAddrK = Array(99, 74, 575)
  // localBaseAddrK is always 0
  val heightA = Array(10, 123, 212)
  val widthA  = Array(5, 1, 240)
  val heightB = Array(9, 131, 256)
  val widthB  = Array(3, 1, 8)
  val sizeK   = Array(3, 6, 8)
  val loadConfigs = 3  // Should match with the size of arrays above

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

  // Testing activation selector commands
  // ------------------------------------
  println("[CtrlTester] Testing activation selector commands...")

  resetFSM
  writeActivationSelectCommands
  startFSM
  checkFSM_activationToggling

  // Testing LoadUnit interface with realistic MMUL and CONV command sequences
  // -------------------------------------------------------------------------
  println("[CtrlTester] Testing LoadUnit interface with realistic MMUL and CONV command sequences...")

  resetFSM
  writeMMULSequence
  startFSM
  checkFSM_mmul

  resetFSM
  writeCONVSequence
  startFSM
  checkFSM_conv

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
    val recvWord = (1 << dut.qEmp) + (1 << dut.itEn)
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
    cmdPtr = 1
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
    for (i <- 0 until loadConfigs) {
      poke(dut.io.addr, (2*loadConfigs+i).U)
      poke(dut.io.wrData, loadKCmd.U)
      step(1)
    }
    // - Expecting active valid bits for all of them
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i < 3*loadConfigs))
    poke(dut.io.commandSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def writeBaseAddresses: Unit = {
    // Writing first address
    poke(dut.io.ldAddrSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, busBaseAddrA(0).U)
    // - Before clock edge, everything remains in reset (invalid addresses)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), false)
    step(1)
    // - After clock edge, ldAValid(0) changes to true
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), (i==0))
    step(1)
    // - Writing the other addresses
    for (i <- 1 until 2*loadConfigs) {
      poke(dut.io.addr, i.U)
      val busBaseAddress = if (i%2 == 0) busBaseAddrA(i/2) else busBaseAddrB(i/2)
      poke(dut.io.wrData, busBaseAddress.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (bit < (i+1)))
    }
    for (i <- 0 until loadConfigs) {
      poke(dut.io.addr, (2*loadConfigs+i).U)
      poke(dut.io.wrData, busBaseAddrK(i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldAValid(bit), (bit < 2*loadConfigs+i+1))
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
      poke(dut.io.addr, (2*i).U)
      val expHeight = if (i%2 == 0) heightA(i/2) else heightB(i/2)
      poke(dut.io.wrData, expHeight.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*i+1))
      poke(dut.io.addr, (2*i+1).U)
      val expWidth = if (i%2 == 0) widthA(i/2) else widthB(i/2)
      poke(dut.io.wrData, expWidth.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*i+2))
    }
    for (i <- 0 until loadConfigs) {
      poke(dut.io.addr, (2*2*loadConfigs+2*i).U)
      poke(dut.io.wrData, sizeK(i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*2*loadConfigs+2*i+1))
      poke(dut.io.addr, (2*2*loadConfigs+2*i+1).U)
      poke(dut.io.wrData, sizeK(i).U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*2*loadConfigs+2*i+2))
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
    expect(dut.io.dma.busBaseAddr, busBaseAddrA(0))
    expect(dut.io.dma.ldHeight, heightA(0))
    expect(dut.io.dma.ldWidth, widthA(0))
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
    expect(dut.io.ldAValid(0), false.B)   // Expecting the first bit of ldAValid to be 0
    expect(dut.io.ldAValid(1), true.B)    // while ldAValid(1) is still 1
    expect(dut.io.ldSValid(0), false.B)   // Expecting the first two bits of ldSValid to be 0
    expect(dut.io.ldSValid(1), false.B)   // while ldSValid(2) is still 1 (only first two bits
    expect(dut.io.ldSValid(2), true.B)    // got to be invalidated
    // - This repeats until no valid commands remain
    cmdPtr = 1
    while (toBoolean(peek(dut.io.cmdValid(cmdPtr)), 0)) {
      expect(dut.io.stateReg, dut.fetch)
      cmdPtr += 1
      step(1)
      expect(dut.io.stateReg, dut.decode)
      var expCmd = 0
      if (cmdPtr-1 < 2*loadConfigs) {
        if ((cmdPtr - 1) % 2 == 0)
          expCmd = loadACmd
        else
          expCmd = loadBCmd
      } else {
        expCmd = loadKCmd
      }
      expect(dut.io.currCommand, expCmd)
      expect(dut.io.cmdPtr, cmdPtr.U)
      step(1)
      expect(dut.io.stateReg, dut.execute)
      expect(dut.io.currCommand, expCmd)
      expect(dut.io.cmdPtr, cmdPtr.U)
      // - DMA interface is expected to be active
      var expBusAddress = 0
      if (cmdPtr-1 < 2*loadConfigs) {
        if ((cmdPtr - 1) % 2 == 0)
          expBusAddress = busBaseAddrA((cmdPtr - 1) / 2)
        else
          expBusAddress = busBaseAddrB((cmdPtr - 1) / 2)
      } else {
        expBusAddress = busBaseAddrK((cmdPtr-1) - 2*loadConfigs)
      }
      expect(dut.io.dma.busBaseAddr, expBusAddress)
      var expHeight = 0
      if (cmdPtr-1 < 2*loadConfigs) {
        if ((cmdPtr - 1) % 2 == 0)
          expHeight = heightA((cmdPtr - 1) / 2)
        else
          expHeight = heightB((cmdPtr - 1) / 2)
      } else {
        expHeight = sizeK((cmdPtr-1) - 2*loadConfigs)
      }
      expect(dut.io.dma.ldHeight, expHeight)
      var expWidth = 0
      if (cmdPtr-1 < 2*loadConfigs) {
        if ((cmdPtr - 1) % 2 == 0)
          expWidth = widthA((cmdPtr - 1) / 2)
        else
          expWidth = widthB((cmdPtr - 1) / 2)
      } else {
        expWidth = sizeK((cmdPtr-1) - 2*loadConfigs)
      }
      expect(dut.io.dma.ldWidth, expWidth)
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
    expStatus = (1 << dut.chEn) + (1 << dut.busy)
    expect(dut.io.statusReg, expStatus)     // Before clock edge, status is normal
    step(1)
    expect(dut.io.stateReg, dut.idle)       // FSM is back to idle state because of error
    if (expCommand == loadACmd || expCommand == loadBCmd)
      expect(dut.io.dma.start, false.B)     // If some information is missing, no DMA cycle starts
    // expStatus: only chip enable is active (queue not empty, see writeLoadCommands function)
    // Error can already be seen in errorCause register
    expStatus = (1 << dut.chEn) + (if (expectQueueEmpty) (1 << dut.qEmp) else 0) + (1 << dut.busy)
    val expError = (1 << expErrorCode)
    expect(dut.io.statusReg, expStatus)
    expect(dut.io.errorCause, expError)
    step(1)
    // Error flag is raised now
    val one: BigInt = 1
    val expStatusU = (one << dut.erFl) + (1 << dut.chEn) + (if (expectQueueEmpty) (1 << dut.qEmp) else 0)
    expect(dut.io.statusReg, expStatusU)
  }

  // Tests for activation selection
  // ------------------------------

  def writeActivationSelectCommands: Unit = {
    // Writing commands
    poke(dut.io.commandSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, dut.SET_RELU.U)
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
    // - Writing additional commands
    for (i <- 1 until 4) {
      poke(dut.io.addr, i.U)
      val loadCmd = if (i%2 == 0) dut.SET_RELU else dut.SET_SIGM
      poke(dut.io.wrData, loadCmd.U)
      step(1)
    }
    // - Expecting active valid bits for all of them
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i < 4))
    poke(dut.io.commandSel, false.B)
    poke(dut.io.rdWrN, true.B)
  }

  def checkFSM_activationToggling: Unit = {
    expect(dut.io.stateReg, dut.idle)     // FSM is still in idle state because signal propagation
    step(1)                               // takes two cycles: io.wrData -> statusReg -> stateReg
    expect(dut.io.stateReg, dut.fetch)    // FSM is in fetch state after one clock edge
    step(1)
    expect(dut.io.stateReg, dut.decode)       // FSM is in decode state after two clock edges
    expect(dut.io.currCommand, dut.SET_RELU)  // 0th command is in current-command register
    expect(dut.io.cmdPtr, 1.U)                // cmdPtr points to the next (1st) command
    step(1)
    expect(dut.io.stateReg, dut.execute)      // FSM is in execute state after three clock edges
    expect(dut.io.currCommand, dut.SET_RELU)  // Current-command register remains the same
    expect(dut.io.cmdPtr, 1.U)                // cmdPtr remains the same
    // - This repeats until no valid command remain
    cmdPtr = 1
    while (toBoolean(peek(dut.io.cmdValid(cmdPtr)), 0)) {
      step(1)
      expect(dut.io.stateReg, dut.fetch)
      cmdPtr += 1
      step(1)
      expect(dut.io.stateReg, dut.decode)
      val expCmd = if ((cmdPtr-1)%2 == 0) dut.SET_RELU else dut.SET_SIGM
      expect(dut.io.currCommand, expCmd)
      expect(dut.io.cmdPtr, cmdPtr)
      step(1)
      expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
      expect(dut.io.currCommand, expCmd)    // Current-command register remains the same
      expect(dut.io.cmdPtr, cmdPtr)         // cmdPtr remains the same
    }
    step(1)
    expect(dut.io.stateReg, dut.idle)  // First, the FSM goes to idle
    step(1)
    expStatus = (1 << dut.chEn) + (1 << dut.qEmp)
    expect(dut.io.statusReg, expStatus)  // Then queue-empty bit gets asserted
  }

  // Tests of LoadUnit interface
  // ---------------------------

  def writeRealSequence(cmd: Int): Unit = {

    // Writing first load command
    poke(dut.io.commandSel, true.B)
    poke(dut.io.rdWrN, false.B)
    poke(dut.io.addr, 0.U)
    val firstCmd = if (cmd == dut.MMUL_S) dut.LOAD_A else dut.LOAD_K
    poke(dut.io.wrData, firstCmd.U)
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

    // Writing second load command
    poke(dut.io.addr, 1.U)
    poke(dut.io.wrData, dut.LOAD_B.U)
    step(1)
    expect(dut.io.statusReg, 0.U)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i < 2))

    // Writing matrix multiplication command
    poke(dut.io.addr, 2.U)
    poke(dut.io.wrData, cmd.U)
    step(1)
    expect(dut.io.statusReg, 0.U)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.cmdValid(i), (i < 3))

    // Switching from command RF to address RF
    poke(dut.io.commandSel, false.B)
    poke(dut.io.ldAddrSel, true.B)

    // Writing first address
    poke(dut.io.addr, 0.U)
    poke(dut.io.wrData, 42.U)
    // - Before clock edge, everything remains in reset (invalid addresses)
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), false)
    step(1)
    // - After clock edge, ldAValid(0) changes to true
    for (i <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(i), (i==0))
    step(1)
    // Writing second address
    poke(dut.io.addr, 1.U)
    poke(dut.io.wrData, 13.U)
    step(1)
    for (bit <- 0 until dut.getDataW)
      expect(dut.io.ldAValid(bit), (bit < 2))

    // Switching from address RF to size RF
    poke(dut.io.ldAddrSel, false.B)
    poke(dut.io.ldSizeSel, true.B)

    // Writing matrix sizes (test uses arrays defined for previous tests)
    for (i <- 0 until 2) {
      poke(dut.io.addr, (2*i).U)
      val expHeight = if (i == 0) (if (cmd == dut.MMUL_S) heightA(0) else sizeK(0)) else heightB(0)
      poke(dut.io.wrData, expHeight.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*i+1))
      poke(dut.io.addr, (2*i+1).U)
      val expWidth = if (i == 0) (if (cmd == dut.MMUL_S) widthA(0) else sizeK(0)) else widthB(0)
      poke(dut.io.wrData, expWidth.U)
      step(1)
      for (bit <- 0 until dut.getDataW)
        expect(dut.io.ldSValid(bit), (bit < 2*i+2))
    }

    // Turning off select and write signals
    poke(dut.io.ldSizeSel, false.B)
    poke(dut.io.rdWrN, false.B)
  }

  def checkFSMAndLoadInterface(cmd: Int): Unit = {
    val firstCmd = if (cmd == dut.MMUL_S) dut.LOAD_A else dut.LOAD_K
    expect(dut.io.stateReg, dut.idle)     // FSM is still in idle state because signal propagation
    step(1)                               // takes two cycles: io.wrData -> statusReg -> stateReg
    expect(dut.io.stateReg, dut.fetch)    // FSM is in fetch state after one clock edge
    step(1)
    expect(dut.io.stateReg, dut.decode)       // FSM is in decode state after two clock edges
    expect(dut.io.currCommand, firstCmd)      // 0th command is in current-command register
    expect(dut.io.cmdPtr, 1.U)                // cmdPtr points to the next (1st) command
    step(1)
    expect(dut.io.stateReg, dut.execute)      // FSM is in execute state after three clock edges
    expect(dut.io.currCommand, firstCmd)      // Current-command register remains the same
    expect(dut.io.cmdPtr, 1.U)                // cmdPtr remains the same
    // - DMA interface is expected to be active
    expect(dut.io.dma.busBaseAddr, 42)
    val expHeight = if (cmd == dut.MMUL_S) heightA(0) else sizeK(0)
    expect(dut.io.dma.ldHeight, expHeight)
    val expWidth = if (cmd == dut.MMUL_S) widthA(0) else sizeK(0)
    expect(dut.io.dma.ldWidth, expWidth)
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
    expect(dut.io.ldAValid(0), false.B)   // Expecting the first bit of ldAValid to be 0
    expect(dut.io.ldAValid(1), true.B)    // while ldAValid(1) is still 1
    expect(dut.io.ldSValid(0), false.B)   // Expecting the first two bits of ldSValid to be 0
    expect(dut.io.ldSValid(1), false.B)   // while ldSValid(2) is still 1 (only first two bits
    expect(dut.io.ldSValid(2), true.B)    // got to be invalidated

    step(1)
    expect(dut.io.stateReg, dut.decode)
    expect(dut.io.currCommand, dut.LOAD_B.U)
    step(1)
    expect(dut.io.stateReg, dut.execute)
    expect(dut.io.currCommand, dut.LOAD_B.U)
    // - DMA interface is expected to be active
    expect(dut.io.dma.busBaseAddr, 13)
    expect(dut.io.dma.ldHeight, heightB(0))
    expect(dut.io.dma.ldWidth, widthB(0))
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
    expect(dut.io.stateReg, dut.fetch)

    step(1)
    expect(dut.io.stateReg, dut.decode)
    expect(dut.io.currCommand, cmd.U)
    step(1)
    expect(dut.io.stateReg, dut.execute)
    expect(dut.io.currCommand, cmd.U)
    // - LoadUnit interface is expected to be active
    expect(dut.io.computeEnable, true.B)
    expect(dut.io.mulConvN, (cmd == dut.MMUL_S))
    expect(dut.io.heightA, expHeight)
    expect(dut.io.widthA, expWidth)
    expect(dut.io.heightB, heightB(0))
    expect(dut.io.widthB, widthB(0))
    poke(dut.io.computeDone, false.B)
    // - While computation is not done, FSM should wait in execute state
    for (_ <- 0 until 10) {
      step(1)
      expect(dut.io.stateReg, dut.execute)
      expect(dut.io.computeEnable, true.B)
    }
    poke(dut.io.computeDone, true.B)         // Emulating LoadUnit
    step(1)
    expect(dut.io.stateReg, dut.idle)
    expect(dut.io.computeEnable, false.B)
    expStatus = (1 << dut.chEn) + (1 << dut.busy) + (1 << dut.qEmp)
    expect(dut.io.statusReg, expStatus)
    step(1)
    expStatus = (1 << dut.chEn) + (1 << dut.qEmp)
    expect(dut.io.statusReg, expStatus)
  }

  def writeMMULSequence: Unit = writeRealSequence(cmd = dut.MMUL_S)

  def checkFSM_mmul: Unit = checkFSMAndLoadInterface(cmd = dut.MMUL_S)

  def writeCONVSequence: Unit = writeRealSequence(cmd = dut.CONV_S)

  def checkFSM_conv: Unit = checkFSMAndLoadInterface(cmd = dut.CONV_S)


  // Helper functions
  def toBoolean(in: BigInt, bitPos: Int) = ((in >> bitPos) % 2) == 1
}
