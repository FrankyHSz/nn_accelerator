
import chisel3._

package object arithmetic {

  // NumberRepresentation
  // ---------------------------------
  type dataType_t = SInt  // Type alias for SInt
  val dataType = SInt     // "Alias" to create SInts with different bit width
  val bitWidth = 8
  val fractionalBits = 7

  // Derived from above
  val baseType = dataType(bitWidth.W)
  val scale = 1 << fractionalBits
  // ---------------------------------


  // Config for modules in arithmetic package
  // ---------------------------------
  val accuWidth = 32
  val gridSize = 16

  // Derived from above
  type accuType_t = dataType_t
  val accuType = dataType(accuWidth.W)
  val accuExt = accuWidth - 2 * bitWidth
  // ---------------------------------
}
