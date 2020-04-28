package ocp

import Chisel._
import chisel3.iotesters.PeekPokeTester

class BusInterfaceTester(dut: BusInterface) extends PeekPokeTester(dut) {

  // Slave port tests
  // ----------------

  // Write tests
  println("[BusInterfaceTester] Write tests...")
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
  println("[BusInterfaceTester] Read tests...")
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

  // ...


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
}
