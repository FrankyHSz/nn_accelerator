package ocp

import chisel3.iotesters

object OCPTesterMain extends App {

  var testAll = false
  if (args.length == 0 || args(0) == "testAll") testAll = true

  if (testAll || args(0) == "testBusInterface") {
    iotesters.Driver.execute(args, () => new BusInterface()) {
      c => new BusInterfaceTester(c)
    }
  }

}
