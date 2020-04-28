
import _root_.ocp._

package object memory {

  // Config for modules in memory package
  // ---------------------------------
  val busAddrWidth = addrWidth  // From OCP package object
  val busDataWidth = dataWidth  // From OCP package object

  val localAddrWidth = 16
  val bankAddrWidth  = 8  // Determines the number of banks, not their size
  val localDataWidth = 8

  // Derived from above
  val dmaChannels = busDataWidth / localDataWidth
  // ---------------------------------
}
