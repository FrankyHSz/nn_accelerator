package memory

import chisel3._
import chisel3.util.log2Up
import _root_.arithmetic.gridSize

class LoadUnit extends Module {
  val io = IO(new Bundle() {

    // Control interface
    val en       = Input(Bool())
    val mulConvN = Input(Bool())
    val sizeA    = Input(UInt(log2Up(gridSize+1).W))
    val sizeB    = Input(UInt(log2Up(gridSize+1).W))

    // Local memory interfaces
    val addrA = Output(UInt(localAddrWidth.W))
    val addrB = Output(UInt(localAddrWidth.W))

    // Arithmetic Grid interface
    val auEn  = Output(Vec(gridSize, Bool()))
    val auClr = Output(Bool())
  })

  // Free-running address generators (counters)
  val addrAReg = RegInit(0.U(localAddrWidth.W))
  val addrBReg = RegInit(0.U(localAddrWidth.W))
  when (RegNext(io.en)) {

    // Generating addresses for matrix multiplication
    when (io.mulConvN) {
      addrAReg := addrAReg + 1.U
      addrBReg := addrBReg + gridSize.U

    // Generating addresses for convolution
    } .otherwise {
      // When reaching the end of the kernel, we reset the address
      when (addrAReg === (io.sizeA*io.sizeA)-1.U) {
        addrAReg := 0.U
      } .otherwise {
        addrAReg := addrAReg + 1.U
      }
      // When we reach the right edge of image with
      // right edge of kernel then we jump to the next line
      when (addrAReg === (io.sizeA*io.sizeA)-1.U) {
        when (addrBReg(localAddrWidth-1, localAddrWidth/2) === (io.sizeB-1.U)) {
          addrBReg := 0.U
        } .otherwise {
          addrBReg := addrBReg - io.sizeA + 1.U - (io.sizeA - 2.U) * io.sizeB
        }
      } .elsewhen (addrBReg(localAddrWidth/2-1, 0) === (io.sizeA - 1.U)) {
        addrBReg := addrBReg + io.sizeB - io.sizeA + 1.U
      } .otherwise {
        addrBReg := addrBReg + 1.U
      }
    }
  } .otherwise {
    addrAReg := 0.U
    addrBReg := 0.U
  }

  io.addrA := addrAReg
  io.addrB := addrBReg

  for (i <- 0 until gridSize) {

    // For multiplication, as many AUs are enabled as the width of Matrix B
    when (io.mulConvN) {
      io.auEn(i) := RegNext(io.en) && RegNext(i.U < io.sizeB)

    // For convolution, as many AUs are enabled
    // as the number of kernels can fit into Matrix B
    } .otherwise {
      io.auEn(i) := RegNext(io.en) && RegNext(i.U < (io.sizeB - io.sizeA - 1.U))
    }
  }

  // Clearing AU accumulators after wrap-around
  // Mul: Address become 0 when a row of the output matrix is finished
  // Conv: Address become 0 when one row of output pixels are finished
  io.auClr := RegNext(io.en) && (addrAReg === 0.U)

  def getAddrW = localAddrWidth
  def getN = gridSize
}
