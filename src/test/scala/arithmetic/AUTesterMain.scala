package arithmetic

import chisel3._

object AUTesterMain extends App {
  iotesters.Driver.execute(args, () => new ArithmeticUnit) {
    c => new AUTester(c)
  }
}
