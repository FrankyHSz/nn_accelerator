
import chisel3._

package object memory {

  // Config for modules in memory package
  // ---------------------------------
  val busAddrWidth = 32
  val busDataWidth = 32

  val localAddrWidth = 16
  val bankAddrWidth  = 8  // Determines the number of banks, not their size
  val localDataWidth = 8

  // Derived from above
  val dmaChannels = busDataWidth / localDataWidth
  // ---------------------------------
}
