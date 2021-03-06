package memory

import chisel3._
import chisel3.util.{Cat, log2Up}
import _root_.arithmetic.gridSize

class LoadUnit extends Module {
  val io = IO(new Bundle() {

    // Control interface
    val ctrl = new Bundle {
      val computeEnable = Input(Bool())
      val computeDone = Output(Bool())
      val mulConvN = Input(Bool())
      val widthA = Input(UInt(log2Up(gridSize + 1).W))
      val widthB = Input(UInt(log2Up(gridSize + 1).W))
      val heightA = Input(UInt(log2Up(gridSize + 1).W))
      val heightB = Input(UInt(log2Up(gridSize + 1).W))
    }

    // Local memory interfaces
    val addrA = Output(UInt(localAddrWidth.W))
    val addrB = Output(UInt(localAddrWidth.W))
    val saveAddr = Output(UInt(localAddrWidth.W))

    // Arithmetic Grid interface
    val auEn  = Output(Vec(gridSize, Bool()))
    val auClr = Output(Bool())
  })

  val doneReg = RegInit(true.B)

  // Free-running address generators (counters)
  val addrAReg = RegInit(0.U(localAddrWidth.W))
  val addrBReg = RegInit(0.U(localAddrWidth.W))
  val saveAddrReg = RegInit(0.U(localAddrWidth.W))
  when (RegNext(io.ctrl.computeEnable)) {

    // Generating addresses for matrix multiplication
    when (io.ctrl.mulConvN) {
      // Checking that indexing does not go outside of matrix A
      when (addrAReg(localAddrWidth/2-1, 0) === (io.ctrl.widthA-1.U)) {                  // Last column
        when (addrAReg(localAddrWidth-1, localAddrWidth/2) === (io.ctrl.heightA-1.U)) {  // Last row
          addrAReg := 0.U
          doneReg := true.B
        } .otherwise {
          addrAReg := addrAReg + gridSize.U - io.ctrl.widthA + 1.U  // Last column but not last row: next row
        }
      } .otherwise {  // Not the last column: next column
        addrAReg := addrAReg + 1.U
      }
      // Checking that indexing does not go outside of matrix B
      when (addrBReg(localAddrWidth-1, localAddrWidth/2) === (io.ctrl.heightB-1.U)) {  // Last row
        addrBReg := 0.U
      } .otherwise {  // Not the last row: next row
        addrBReg := addrBReg + gridSize.U
      }  // Columns are handled by auEn: if width of matrix B is less than gridSize, some AUs are not enabled

    // Generating addresses for convolution
    } .otherwise {
      // When reaching the end of the kernel, we reset the address
      when (addrAReg === (io.ctrl.widthA*io.ctrl.widthA)-1.U) {
        addrAReg := 0.U
      } .otherwise {
        addrAReg := addrAReg + 1.U
      }
      // When we reach the right edge of image with
      // right edge of kernel then we jump to the next line
      when (addrAReg === (io.ctrl.widthA*io.ctrl.widthA)-1.U) {
        when (addrBReg(localAddrWidth-1, localAddrWidth/2) === (io.ctrl.heightB-1.U)) {
          addrBReg := 0.U
          saveAddrReg := addrBReg - io.ctrl.widthA + 1.U - (io.ctrl.widthA - 2.U) * io.ctrl.widthB
          doneReg := true.B
        } .otherwise {
          addrBReg := addrBReg - io.ctrl.widthA + 1.U - (io.ctrl.widthA - 2.U) * io.ctrl.widthB
        }
      } .elsewhen (addrBReg(localAddrWidth/2-1, 0) === (io.ctrl.widthA - 1.U)) {
        addrBReg := addrBReg + io.ctrl.widthB - io.ctrl.widthA + 1.U
      } .otherwise {
        addrBReg := addrBReg + 1.U
      }
    }
  } .otherwise {
    addrAReg := 0.U
    addrBReg := 0.U
    saveAddrReg := 0.U
  }

  when (doneReg) {
    doneReg := !io.ctrl.computeEnable  // Waiting on io.computeEnable to become high
  }
  io.ctrl.computeDone := doneReg

  io.addrA := addrAReg
  io.addrB := addrBReg
  io.saveAddr := saveAddrReg

  for (i <- 0 until gridSize) {

    // For multiplication, as many AUs are enabled as the width of Matrix B
    when (io.ctrl.mulConvN) {
      io.auEn(i) := io.ctrl.computeEnable && (RegNext(io.ctrl.computeEnable) ^ doneReg) && (i.U < io.ctrl.widthB)

    // For convolution, as many AUs are enabled
    // as the number of kernels can fit into Matrix B
    } .otherwise {
      io.auEn(i) := io.ctrl.computeEnable && (
        RegNext(io.ctrl.computeEnable) ^ doneReg) && (i.U < (io.ctrl.widthB - io.ctrl.widthA + 1.U))
    }
  }

  // Clearing AU accumulators after wrap-around
  // Mul: Address become 0 when a row of the output matrix is finished
  // Conv: Address become 0 when one row of output pixels are finished
  io.auClr := io.ctrl.computeEnable && (addrAReg === 0.U)

  def getAddrW = localAddrWidth
  def getN = gridSize
}
