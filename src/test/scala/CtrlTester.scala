
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
  // Sent     Word: the 2 least significant bytes are set to 1
  // Received Word: only writable bits will change
  val sentWord = (1 << 16) - 1
  val recvWord = (1 << dut.chEn) + (1 << dut.itEn) + (1 << dut.acEn)
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
    expect(dut.io.cmdValid, cmdValidRef.U)
    step(1)
    cmdValidRef = (cmdValidRef << 1) + 1
    expect(dut.io.commandRF(i), (42 + i).U)
    expect(dut.io.cmdValid, cmdValidRef.U)
  }
  // - Read back: expecting 0s because these registers are write-only
  poke(dut.io.rdWrN, true.B)
  for (i <- 0 until 5) {
    poke(dut.io.addr, (dut.CMD_RF_B + i).U)
    step(1)
    expect(dut.io.cmdValid, cmdValidRef)   // Registers are still
    expect(dut.io.commandRF(i), (42 + i).U)  // holding their values
    expect(dut.io.rdData, 0.U)             // but rdData is 0
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
    expect(dut.io.ldAValid, ldAddrValidRef.U)
    step(1)
    ldAddrValidRef = (ldAddrValidRef << 1) + 1
    expect(dut.io.loadAddrRF(i), (42 + i).U)
    expect(dut.io.ldAValid, ldAddrValidRef.U)
  }
  // - Read back: expecting 0s because these registers are write-only
  poke(dut.io.rdWrN, true.B)
  for (i <- 0 until 5) {
    poke(dut.io.addr, (dut.LD_ADDR_B + i).U)
    step(1)
    expect(dut.io.ldAValid, ldAddrValidRef)   // Registers are still
    expect(dut.io.loadAddrRF(i), (42 + i).U)    // holding their values
    expect(dut.io.rdData, 0.U)                // but rdData is 0
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
    expect(dut.io.ldSValid, ldSizeValidRef.U)
    step(1)
    ldSizeValidRef = (ldSizeValidRef << 1) + 1
    expect(dut.io.loadSizeRF(i), (42 + i).U)
    expect(dut.io.ldSValid, ldSizeValidRef.U)
  }
  // - Read back: expecting 0s because these registers are write-only
  poke(dut.io.rdWrN, true.B)
  for (i <- 0 until 5) {
    poke(dut.io.addr, (dut.LD_SIZE_B + i).U)
    step(1)
    expect(dut.io.ldSValid, ldSizeValidRef)  // Registers are still
    expect(dut.io.loadSizeRF(i), (42 + i).U)   // holding their values
    expect(dut.io.rdData, 0.U)               // but rdData is 0
  }
  // Don't forget to deactivate register select after test
  poke(dut.io.ldSizeSel, false.B)
}
