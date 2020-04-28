
package object ocp {

  // Configuration of OCP bus
  // ------------------------
  val addrWidth = 32
  val dataWidth = 32

  // Configuration of the module's address space
  // -------------------------------------------

  val IP_BASE_ADDRESS  = 1 << 16  // Just an example
  val IP_ADDRESS_WIDTH = 5+2      // Currently, Controller has 4 blocks of 32-words

  // Block addresses
  val MAIN_BLOCK    = 0
  val CMD_BLOCK     = 1
  val LD_ADDR_BLOCK = 2
  val LD_SIZE_BLOCK = 3

  // Register address map -- RD: readable, WR: writable
  val STATUS    = MAIN_BLOCK    * dataWidth + 0  // (see below)
  val ERR_CAUSE = MAIN_BLOCK    * dataWidth + 1  // (RD)
  val CMD_RF_B  = CMD_BLOCK     * dataWidth      // (WR)
  val LD_ADDR_B = LD_ADDR_BLOCK * dataWidth      // (WR)
  val LD_SIZE_B = LD_SIZE_BLOCK * dataWidth      // (WR)

  // Useful constants
  val blockSize = dataWidth
}
