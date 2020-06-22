package memory

import chisel3.iotesters

object MemoryTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testMemBank") {
    iotesters.Driver.execute(args, () => new MemoryBank) {
      c => new MemoryBankTester(c)
    }
  }

  if (testAll || args(0) == "testLocalMem") {
    iotesters.Driver.execute(args, () => new LocalMemory) {
      c => new LocalMemoryTester(c)
    }
  }

  if (testAll || args(0) == "testLoadUnit") {
    iotesters.Driver.execute(args, () => new LoadUnit) {
      c => new LoadUnitTester(c)
    }
  }

  if (testAll || args(0) == "testDMA") {
    iotesters.Driver.execute(args, () => new DMA) {
      c => new DMATester(c)
    }
  }
}
