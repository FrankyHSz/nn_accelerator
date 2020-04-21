package arithmetic

import chisel3.iotesters

object ArithmeticTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testAU") {
    iotesters.Driver.execute(args, () => new ArithmeticUnit) {
      c => new AUTester(c)
    }
  }

  if (testAll || args(0) == "testAG") {
    iotesters.Driver.execute(args, () => new ArithmeticGrid) {
      c => new AGTester(c)
    }
  }

  if (testAll || args(0) == "testSigmoid") {
    iotesters.Driver.execute(args, () => new Sigmoid) {
      c => new SigmoidTester(c)
    }
  }

  if (testAll || args(0) == "testActivationUnit") {
    iotesters.Driver.execute(args, () => new ActivationUnit) {
      c => new ActivationUnitTester(c)
    }
  }

  if (testAll || args(0) == "testActivationGrid") {
    iotesters.Driver.execute(args, () => new ActivationGrid) {
      c => new ActivationGridTester(c)
    }
  }
}
