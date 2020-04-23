
import chisel3._

object ToplevelTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testNNA") {
    iotesters.Driver.execute(args, () => new NNAccelerator()) {
      c => new ToplevelTester(c)
    }
  }

  if (testAll || args(0) == "testController") {
    iotesters.Driver.execute(args, () => new Controller(testInternals = true)) {
      c => new CtrlTester(c)
    }
  }
}
