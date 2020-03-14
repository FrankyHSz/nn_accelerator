package arithmetic

import chisel3.iotesters

object ArithmeticTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testAU") {
    iotesters.Driver.execute(args, () => new ArithmeticUnit(inputW = 8, accuW = 32)) {
      c => new AUTester(c)
    }
  }
}
