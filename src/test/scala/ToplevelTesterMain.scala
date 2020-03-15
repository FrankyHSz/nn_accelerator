
import chisel3._

object ToplevelTesterMain extends App {
  iotesters.Driver.execute(args, () => new NNAccelerator()) {
    c => new ToplevelTester(c)
  }
}
