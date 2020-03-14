package util

import chisel3._
import chisel3.util.log2Up

class BarrelShifter[T <: Data](n: Int, data: T, pipelined: Boolean) extends Module {
  val io = IO(new Bundle() {
    val in  = Input(Vec(n, data))
    val sh  = Input(UInt(log2Up(n).W))
    val out = Output(Vec(n, data))
  })

  val wires = if (pipelined) Reg(Vec(log2Up(n), Vec(n, data)))
              else Wire(Vec(log2Up(n), Vec(n, data)))

  for (layer <- 0 until log2Up(n)) {

    // Shifted indices
    val shifted1 = Array.range(1 << layer, n)
    val shifted2 = Array.range(0, 1 << layer)
    val shiftedIdx = shifted1 ++ shifted2

    // Linear indices
    val linearIdx = Array.range(0, n)

    // Creating shifted layer
    val shifted = Wire(Vec(n, data))
    for(i <- linearIdx)
      if (layer == 0)
        shifted(i) := io.in(shiftedIdx(i))
      else
        shifted(i) := wires(layer-1)(shiftedIdx(i))

    // Connecting Multiplexers
    for (item <- 0 until n)
      if (layer == 0)
        wires(0) := Mux(io.sh(0), shifted, io.in)
      else
        wires(layer) := Mux(io.sh(layer), shifted, wires(layer-1))
  }

  io.out := wires(log2Up(n)-1)

  def getN = n
  def getType = data
  def isPipelined = pipelined
}