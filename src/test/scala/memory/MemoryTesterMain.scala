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

    println("--------------------------------------------------------------------------")
    println("Testing banked LocalMemory with non-flipped interface (DMA write, AG read)")
    println("--------------------------------------------------------------------------")
    iotesters.Driver.execute(args, () => new LocalMemory(banked = true, flippedInterface = false)) {
      c => new LocalMemoryTester(c)
    }

    println("----------------------------------------------------------------------")
    println("Testing banked LocalMemory with flipped interface (DMA read, AG write)")
    println("----------------------------------------------------------------------")
    iotesters.Driver.execute(args, () => new LocalMemory(banked = true, flippedInterface = true)) {
      c => new LocalMemoryTester(c)
    }

    println("------------------------------------------------------------------------------")
    println("Testing non-banked LocalMemory with non-flipped interface (DMA write, AG read)")
    println("------------------------------------------------------------------------------")
    iotesters.Driver.execute(args, () => new LocalMemory(banked = false, flippedInterface = false)) {
      c => new LocalMemoryTester(c)
    }

    println("--------------------------------------------------------------------------")
    println("Testing non-banked LocalMemory with flipped interface (DMA read, AG write)")
    println("--------------------------------------------------------------------------")
    iotesters.Driver.execute(args, () => new LocalMemory(banked = false, flippedInterface = true)) {
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
