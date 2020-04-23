
import chisel3._
import chisel3.util.{Cat, log2Up}
import _root_.memory._

class Controller(testInternals: Boolean) extends Module {

  // Register address map
  val STATUS    = 0 * busDataWidth + 0
  val CMD_RF_B  = 1 * busDataWidth
  val LD_ADDR_B = 2 * busDataWidth
  val LD_SIZE_B = 3 * busDataWidth

  // Control/status register bits (indices) -- RD: readable, WR: writable, CL: cleanable
  val chEn = 0  // Chip enable      : enables operation according to the command queue (RD/WR)
  val busy = 1  // Busy flag        : signals that operations are under execution      (RD)
  val qEmp = 2  // Queue is empty   : signals that no operation is specified           (RD)
  val itEn = 3  // Interrupt enable : enables generation of processor interrupts       (RD/WR)
  val itFl = 4  // Interrupt flag   : signals processor interrupt (if enabled)         (RD/CL)
  val acEn = 5  // Activation enable: if cleared, bypasses the Activation Grid         (RD/WR)
  // ...
  val erFl = busDataWidth-1  // Error flag: signals that an error occurred during the last operation (RD)
  // Related
  val inactiveBitsInStatus = busDataWidth - 7

  // Useful constants for module
  val blockAddrW = log2Up(busDataWidth)

  val io = IO(new Bundle {

    // RD/WR interface for register files
    val addr   = Input(UInt(busAddrWidth.W))   // Decoded address (offset inside IP address space)
    val wrData = Input(UInt(busDataWidth.W))
    val rdData = Output(UInt(busDataWidth.W))
    val rdWrN  = Input(Bool())

    // Decoded block select signals from Bus Interface
    val statusSel  = Input(Bool())
    val commandSel = Input(Bool())
    val ldAddrSel  = Input(Bool())
    val ldSizeSel  = Input(Bool())

    // Test port
    val statusReg  = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val cmdValid   = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val commandRF  = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
    val ldAValid   = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val ldSValid   = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val loadAddrRF = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
    val loadSizeRF = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
  })

  // Control/status register
  val statusReg = RegInit(0.U(busDataWidth.W))

  // Command registers
  val cmdValid  = RegInit(0.U(busDataWidth.W))  // Internal, not memory-mapped
  val commandRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))

  // Load registers
  val ldAValid   = RegInit(0.U(busDataWidth.W))  // Internal, not memory-mapped
  val ldSValid   = RegInit(0.U(busDataWidth.W))  // Internal, not memory-mapped
  val loadAddrRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))
  val loadSizeRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))


  // Register RD/WR logic
  io.rdData := 0.U
  when (io.statusSel) {  // Control/status register: RD/WR
    when (io.rdWrN) {
      io.rdData := statusReg
    } .otherwise {
      val itFlag   = statusReg(itFl) && io.wrData(itFl)
      val bits5to3 = Cat(io.wrData(acEn), itFlag, io.wrData(itEn))
      val bits5to0 = Cat(bits5to3, statusReg(2, 1), io.wrData(chEn))
      statusReg := Cat(statusReg(erFl), 0.U(inactiveBitsInStatus.W), bits5to0)
    }
  }
  // Other registers are available only if the NNA is enabled
  when (statusReg(chEn)) {
    when(io.commandSel) {  // Command register file: WR
      when(!io.rdWrN) {
        commandRF(io.addr(blockAddrW - 1, 0)) := io.wrData
        cmdValid := Cat(cmdValid(busDataWidth - 2, 0), 1.U(1.W))
      }
    }
    when(io.ldAddrSel) {   // Load Address register file: WR
      when(!io.rdWrN) {
        loadAddrRF(io.addr(blockAddrW - 1, 0)) := io.wrData
        ldAValid := Cat(ldAValid(busDataWidth - 2, 0), 1.U(1.W))
      }
    }
    when(io.ldSizeSel) {   // Load Size register file: WR
      when(!io.rdWrN) {
        loadSizeRF(io.addr(blockAddrW - 1, 0)) := io.wrData
        ldSValid := Cat(ldSValid(busDataWidth - 2, 0), 1.U(1.W))
      }
    }
  }

  // Providing information about the internal
  // state of Controller for testing
  if (testInternals) {
    io.statusReg  := statusReg
    io.cmdValid   := cmdValid
    io.commandRF  := commandRF
    io.ldAValid   := ldAValid
    io.ldSValid   := ldSValid
    io.loadAddrRF := loadAddrRF
    io.loadSizeRF := loadSizeRF
  }


  // Helper functions
  def getRegMap = Map(
    "Status Register"                         -> STATUS,
    "Command Register File Base Address"      -> CMD_RF_B,
    "Load Address Register File Base Address" -> LD_ADDR_B,
    "Load Size Register File Base Address"    -> LD_SIZE_B
  )
}
