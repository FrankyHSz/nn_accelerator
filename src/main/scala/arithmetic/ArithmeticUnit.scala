package arithmetic

import chisel3._

class ArithmeticUnit extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val en = Input(Bool())
    val clr = Input(Bool())
    val mac = Output(UInt(32.W))
  })

  val aReg = RegInit(0.U(8.W))
  val bReg = RegInit(0.U(8.W))
  val mul  = RegInit(0.U(16.W))
  val accu = RegInit(0.U(32.W))
  val enDelay = Reg(Vec(2, Bool()))
  val clrDelay = Reg(Vec(2, Bool()))

  // First stage
  aReg := io.a
  bReg := io.b
  enDelay(0) := io.en
  clrDelay(0) := io.clr

  // Second stage
  mul  := aReg * bReg
  enDelay(1) := enDelay(0)
  clrDelay(1) := clrDelay(0)

  // Third stage
  when (enDelay(1)) {
    accu := Mux(clrDelay(1), mul, accu + mul)
  }

  io.mac := accu
}
