
import chisel3._
import chisel3.util.{Cat, Enum, log2Up}
import _root_.memory._

class Controller(testInternals: Boolean) extends Module {

  // --------------------
  // Module configuration
  // --------------------

  // Register address map
  val STATUS    = 0 * busDataWidth + 0
  val CMD_RF_B  = 1 * busDataWidth
  val LD_ADDR_B = 2 * busDataWidth
  val LD_SIZE_B = 3 * busDataWidth

  // Control/status register bits (indices) -- RD: readable, WR: writable, CL: cleanable, ST: settable
  val chEn = 0  // Chip enable      : enables operation according to the command queue (RD/WR)
  val busy = 1  // Busy flag        : signals that operations are under execution      (RD)
  val qEmp = 2  // Queue is empty   : signals that no operation is specified           (RD/ST)
  val itEn = 3  // Interrupt enable : enables generation of processor interrupts       (RD/WR)
  val itFl = 4  // Interrupt flag   : signals processor interrupt (if enabled)         (RD/CL)
  val acEn = 5  // Activation enable: if cleared, bypasses the Activation Grid         (RD/WR)
  // ...
  val erFl = busDataWidth-1  // Error flag: signals that an error occurred during the last operation (RD)
  // Related
  val inactiveBitsInStatus = busDataWidth - 7

  // Useful constants for module
  val blockAddrW  = log2Up(busDataWidth)
  val numOfStates = 4
  val stateRegW   = log2Up(numOfStates)


  // ----------------
  // Module interface
  // ----------------

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
    // - Memory-mapped and internal registers
    val statusReg  = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val cmdValid   = if (testInternals) Output(Vec(busDataWidth, Bool())) else Output(Vec(0, Bool()))
    val commandRF  = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
    val ldAValid   = if (testInternals) Output(Vec(busDataWidth, Bool())) else Output(Vec(0, Bool()))
    val ldSValid   = if (testInternals) Output(Vec(busDataWidth, Bool())) else Output(Vec(0, Bool()))
    val loadAddrRF = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
    val loadSizeRF = if (testInternals) Output(Vec(busDataWidth, UInt(busDataWidth.W))) else Output(Vec(0, UInt(0.W)))
    // - Registers of state machine
    val stateReg    = if (testInternals) Output(UInt(stateRegW.W)) else Output(UInt(0.W))
    val cmdPtr      = if (testInternals) Output(UInt(blockAddrW.W)) else Output(UInt(0.W))
    val ldAPtr      = if (testInternals) Output(UInt(blockAddrW.W)) else Output(UInt(0.W))
    val ldSPtr      = if (testInternals) Output(UInt(blockAddrW.W)) else Output(UInt(0.W))
    val currCommand = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
  })


  // ------------------------------------
  // Memory-mapped and internal registers
  // ------------------------------------

  // Control/status register
  val statusReg = RegInit(0.U(busDataWidth.W))

  // Command registers
  val cmdValid  = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
  val commandRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))

  // Load registers
  val ldAValid   = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
  val ldSValid   = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
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
      val queueEmpty = statusReg(qEmp) || io.wrData(qEmp)
      val bits5to0 = Cat(bits5to3, queueEmpty, statusReg(busy), io.wrData(chEn))
      statusReg := Cat(statusReg(erFl), 0.U(inactiveBitsInStatus.W), bits5to0)
    }
  } .otherwise {  // Non-bus operations involving statusReg
    // Refreshing queue-is-empty bit at every clock cycle other than bus reads/writes
    statusReg := Cat(statusReg(busDataWidth-1, 3), !cmdValid.asUInt.orR, statusReg(1, 0))
  }
  when(io.commandSel) {  // Command register file: WR
    when(!io.rdWrN) {
      commandRF(io.addr(blockAddrW - 1, 0)) := io.wrData
    }
  }
  when(io.ldAddrSel) {   // Load Address register file: WR
    when(!io.rdWrN) {
      loadAddrRF(io.addr(blockAddrW - 1, 0)) := io.wrData
    }
  }
  when(io.ldSizeSel) {   // Load Size register file: WR
    when(!io.rdWrN) {
      loadSizeRF(io.addr(blockAddrW - 1, 0)) := io.wrData
    }
  }

  // Read pointers
  val cmdPtr = RegInit(0.U(blockAddrW.W))
  val ldAPtr = RegInit(0.U(blockAddrW.W))
  val ldSPtr = RegInit(0.U(blockAddrW.W))

  // Invalidate-on-read signals
  val cmdRead = WireDefault(false.B)
  val ldARead = WireDefault(false.B)
  val ldSRead = WireDefault(false.B)

  // Validity registers
  // - Writing a word makes that word valid
  // - Setting the queue-empty bit in statusReg makes every word invalid
  // - Reading a word makes that word invalid
  when (io.commandSel && !io.rdWrN) {
    cmdValid(io.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.statusSel && !io.rdWrN && io.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      cmdValid(i) := false.B
  } .elsewhen (cmdRead) {
    cmdValid(cmdPtr) := false.B
  }
  when (io.ldAddrSel && !io.rdWrN) {
    ldAValid(io.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.statusSel && !io.rdWrN && io.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      ldAValid(i) := false.B
  } .elsewhen (ldARead) {
    ldAValid(ldAPtr) := false.B
  }
  when (io.ldSizeSel && !io.rdWrN) {
    ldSValid(io.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.statusSel && !io.rdWrN && io.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      ldSValid(i) := false.B
  } .elsewhen (ldSRead) {
    ldSValid(ldSPtr) := false.B
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


  // ------------------
  // FSM to operate NNA
  // ------------------

  // States
  val idle :: fetch :: decode :: execute :: Nil = Enum(numOfStates)

  // State register
  val stateReg = RegInit(idle)

  // Command ("instruction") and operand registers (to be continued...)
  val currCommand = RegInit(0.U(busDataWidth.W))

  // Next state logic and related operations
  when (stateReg === idle) {
    when(statusReg(chEn) && !statusReg(qEmp)) {
      stateReg := fetch
      cmdPtr := 0.U
    }
  } .elsewhen (stateReg === fetch) {
    when (statusReg(chEn)) {
      when(cmdValid(cmdPtr)) {
        stateReg := decode
        currCommand := commandRF(cmdPtr)
        cmdPtr := cmdPtr + 1.U
      } .otherwise {
        stateReg := idle
        cmdPtr := 0.U
      }
    } .otherwise {
      stateReg := idle
      cmdPtr := 0.U
    }
  } .elsewhen (stateReg === decode) {
    when (statusReg(chEn)) {
      stateReg := execute
    } .otherwise {
      stateReg := idle
    }
  } .elsewhen (stateReg === execute) {
    when (cmdValid.asUInt.orR) {
      stateReg := fetch
    } .otherwise {
      stateReg := idle
    }
  } .otherwise {
    stateReg := idle
  }

  // Signals to invalidate register data on read (to be continued...)
  cmdRead := (stateReg === fetch) && statusReg(chEn) && cmdValid(cmdPtr)

  // Providing information about the internal
  // state of Controller for testing
  if (testInternals) {
    io.stateReg    := stateReg
    io.cmdPtr      := cmdPtr
    io.ldAPtr      := ldAPtr
    io.ldSPtr      := ldSPtr
    io.currCommand := currCommand
  }


  // Helper functions
  def getRegMap = Map(
    "Status Register"                         -> STATUS,
    "Command Register File Base Address"      -> CMD_RF_B,
    "Load Address Register File Base Address" -> LD_ADDR_B,
    "Load Size Register File Base Address"    -> LD_SIZE_B
  )
  def getDataW = busDataWidth
}
