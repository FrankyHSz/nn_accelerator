package memory

import chisel3.iotesters

object MemoryTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testMemBank") {
    iotesters.Driver.execute(args, () => new MemoryBank(addrW = 8, dataW = 8)) {
      c => new MemoryBankTester(c)
    }
  }
}
