package arithmetic

import chisel3._

class ActivationUnit extends Module {
  val io = IO(new Bundle() {
    val in  = Input(accuType)
    val sel = Input(Bool())
    val out = Output(baseType)
  })

  // Propagating the selected activation
  io.out := Mux(
    io.sel,
    ReLU(io.in),
    Sigmoid(io.in)
  )

  def getInW = accuType.getWidth
  def getOutW = baseType.getWidth
}
