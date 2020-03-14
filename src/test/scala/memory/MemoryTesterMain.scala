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

  if (testAll || args(0) == "testLocalMem") {
    iotesters.Driver.execute(args, () => new LocalMemory(addrW = 10, bankAddrW = 6, dataW = 8)) {
      c => new LocalMemoryTester(c)
    }
  }

  if (testAll || args(0) == "testLoadUnit") {
    iotesters.Driver.execute(args, () => new LoadUnit(addrW = 8)) {
      c => new LoadUnitTester(c)
    }
  }
}
