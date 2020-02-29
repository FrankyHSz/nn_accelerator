package arithmetic

import chisel3._

class ArithmeticUnit extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val clear = Input(Bool())
    val mac = Output(UInt(32.W))
  })

  val aReg = RegInit(0.U(8.W))
  val bReg = RegInit(0.U(8.W))
  val mul  = RegInit(0.U(16.W))
  val accu = RegInit(0.U(32.W))
  val clearDelay = Reg(Vec(2, Bool()))

  // First stage
  aReg := io.a
  bReg := io.b
  clearDelay(0) := io.clear

  // Second stage
  mul  := aReg * bReg
  clearDelay(1) := clearDelay(0)

  // Third stage
  accu := Mux(clearDelay(1), mul, accu + mul)

  io.mac := accu
}
