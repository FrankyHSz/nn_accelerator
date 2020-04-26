
import chisel3._
import chisel3.iotesters.PeekPokeTester

class CtrlTester(dut: Controller) extends PeekPokeTester(dut) {

  val regMap = dut.getRegMap
  println("Register Address Map:")
  for ((k,v) <- regMap) println(" " + k + ": " + v)

  // Write-and-read-back tests
  // -------------------------
  println("[CtrlTester] Write-and-read-back testing...")
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

  // Load Address register file and Load Address Valid register
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


  // State machine tests
  // -------------------
  println("[CtrlTester] State machine testing...")

  // Clearing all enable signals and flags
  // - resetWord: Everything is 0 except queue-empty bit which invalidates every command
  val resetWord = (1 << dut.qEmp)
  poke(dut.io.statusSel, true.B)
  poke(dut.io.wrData, resetWord.U)
  poke(dut.io.rdWrN, false.B)
  step(1)
  expect(dut.io.statusReg, resetWord)
  poke(dut.io.statusSel, false.B)

  // Writing commands
  val dummyCmd = 42
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
    poke(dut.io.wrData, (dummyCmd + i).U)
    step(1)
  }
  // - Expecting active valid bits for all of them (0 to 10)
  for (i <- 0 until dut.getDataW)
    expect(dut.io.cmdValid(i), (i<=10))

  // Starting FSM and inspecting it as it "processes" the commands
  // - startWord: Sets chip enable to start operations
  val startWord = (1 << dut.chEn)
  poke(dut.io.commandSel, false.B)
  poke(dut.io.statusSel, true.B)
  poke(dut.io.wrData, startWord.U)
  expect(dut.io.stateReg, dut.idle)     // FSM starts from idle state
  step(1)
  poke(dut.io.statusSel, false.B)
  poke(dut.io.rdWrN, true.B)            // FSM is still in idle state because signal propagation
  expect(dut.io.stateReg, dut.idle)     // takes two cycles: io.wrData -> statusReg -> stateReg
  step(1)
  expect(dut.io.stateReg, dut.fetch)    // FSM is in fetch state after one clock edge
  step(1)
  expect(dut.io.stateReg, dut.decode)   // FSM is in decode state after two clock edges
  expect(dut.io.currCommand, dummyCmd)  // 0th command is in current-command register
  expect(dut.io.cmdPtr, 1.U)            // cmdPtr points to the next (1st) command
  step(1)
  expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
  expect(dut.io.currCommand, dummyCmd)  // Current-command register remains the same
  expect(dut.io.cmdPtr, 1.U)            // cmdPtr remains the same
  // - This repeats until no valid command remains
  var currCmd = dummyCmd
  var cmdPtr = 1
  while (toBoolean(peek(dut.io.cmdValid(cmdPtr)), 0)) {
    step(1)
    expect(dut.io.stateReg, dut.fetch)
    currCmd += 1  // Modelling operations
    cmdPtr += 1   // in fetch state
    step(1)
    expect(dut.io.stateReg, dut.decode)
    expect(dut.io.currCommand, currCmd)
    expect(dut.io.cmdPtr, cmdPtr)
    step(1)
    expect(dut.io.stateReg, dut.execute)  // FSM is in execute state after three clock edges
    expect(dut.io.currCommand, currCmd)   // Current-command register remains the same
    expect(dut.io.cmdPtr, cmdPtr)         // cmdPtr remains the same
  }
  step(1)
  expect(dut.io.stateReg, dut.idle)  // First, the FSM goes to idle
  step(10)
  val expStatus = (1 << dut.chEn) + (1 << dut.qEmp)
  expect(dut.io.statusReg, expStatus)  // Then queue-empty bit gets asserted


  // Helper functions
  def toBoolean(in: BigInt, bitPos: Int) = ((in >> bitPos) % 2) == 1
}
