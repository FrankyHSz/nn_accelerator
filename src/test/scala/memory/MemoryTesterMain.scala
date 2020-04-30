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

    println("Testing LocalMemory with non-flipped interface (DMA write, AG read)")
    iotesters.Driver.execute(args, () => new LocalMemory(flippedInterface = false)) {
      c => new LocalMemoryTester(c)
    }

    println("Testing LocalMemory with flipped interface (DMA read, AG write)")
    iotesters.Driver.execute(args, () => new LocalMemory(flippedInterface = true)) {
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
