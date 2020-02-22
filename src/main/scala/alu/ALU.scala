package alu

import chisel3._

class ALU extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val c = Output(UInt(32.W))
  })

  val result = io.a + io.b
  io.c := result
}
