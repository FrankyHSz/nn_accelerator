
import chisel3._
import chisel3.util.{Cat, Enum, log2Up}
import _root_.arithmetic.gridSize
import _root_.memory._
import scala.collection.immutable.ListMap

class Controller(testInternals: Boolean) extends Module {

  // --------------------
  // Module configuration
  // --------------------

  // Register address map -- RD: readable, WR: writable
  // (To modify it, go to the companion object)
  val STATUS    = ocp.STATUS     // (see below)
  val ERR_CAUSE = ocp.ERR_CAUSE  // (RD)
  val CMD_RF_B  = ocp.CMD_RF_B   // (WR)
  val LD_ADDR_B = ocp.LD_ADDR_B  // (WR)
  val LD_SIZE_B = ocp.LD_SIZE_B  // (WR)

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
  val unusedBitsInStatus = busDataWidth - 7

  // Error Cause register bits (indices) -- The whole register is read-only
  val unknownCommand = 0  // If a command (which is claimed to be valid in cmdValid reg.) is unrecognized.
  val noBaseAddress  = 1  // If there was no corresponding base address found for a load command.
  val noSize         = 2  // If there was no data size found for a load command.
  // Related
  val unusedBitsInErrorCause = busDataWidth - 3

  // Supported commands
  val DUMMY    = 42 // For testing only
  val LOAD_K   = 1  // [000...001] Loads kernel into mem. A bank0. Needs base address and size of data to be specified.
  val LOAD_A   = 3  // [000...011] Loads data into memory A. Needs base address and size of data to be specified.
  val LOAD_B   = 2  // [000...010] Loads data into memory B. Needs base address and size of data to be specified.
  val SET_SIGM = 4  // [00...0100] Sets activation to sigmoid. No further data required (N.f.d.r.).
  val SET_RELU = 5  // [00...0101] Sets activation to ReLU. N.f.d.r.

  // Useful constants for module
  val blockAddrW  = log2Up(busDataWidth)
  val numOfStates = 4
  val stateRegW   = log2Up(numOfStates)


  // ----------------
  // Module interface
  // ----------------

  val io = IO(new Bundle {

    // RD/WR interface for register files
    val addr   = Input(UInt(blockAddrW.W))   // Decoded address (offset inside IP address space)
    val wrData = Input(UInt(busDataWidth.W))
    val rdData = Output(UInt(busDataWidth.W))
    val rdWrN  = Input(Bool())

    // Decoded block select signals from Bus Interface
    val statusSel  = Input(Bool())
    val errCauseSel = Input(Bool())
    val commandSel = Input(Bool())
    val ldAddrSel  = Input(Bool())
    val ldSizeSel  = Input(Bool())

    // DMA interface
    val dma = new Bundle() {
      val busBaseAddr   = Output(UInt(busAddrWidth.W))
      val burstLen      = Output(UInt(localAddrWidth.W))
      val rowLen        = Output(UInt(log2Up(gridSize+1).W))
      val sel   = Output(Bool())
      val start = Output(Bool())
      val done  = Input(Bool())
    }

    // Activation select for the Activation Grid
    val actSel = Output(Bool())

    // Test port
    // - Memory-mapped and internal registers
    val statusReg  = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
    val errorCause = if (testInternals) Output(UInt(busDataWidth.W)) else Output(UInt(0.W))
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

  // Error Cause register (see possible error causes under Module Configuration)
  val errorCause = RegInit(0.U(busDataWidth.W))

  // Error Cause wires
  val setUnknownCommand = WireDefault(false.B)
  val setNoBaseAddress  = WireDefault(false.B)
  val setNoSize         = WireDefault(false.B)

  // Command registers
  val cmdValid  = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
  val commandRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))

  // Load registers
  val ldAValid   = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
  val ldSValid   = RegInit(VecInit(Seq.fill(busDataWidth)(false.B)))  // Internal, not memory-mapped
  val loadAddrRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))
  val loadSizeRF = Reg(Vec(busDataWidth, UInt(busDataWidth.W)))

  // Activation Select register that drives io.actSel (set/reset logic is in FSM)
  val actSelReg = RegInit(false.B)  // Sigmoid is default

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
      statusReg := Cat(statusReg(erFl), 0.U(unusedBitsInStatus.W), bits5to0)

      // When the programmer invalidates all registers by setting the queue-empty bit
      // then all error flags should be cleared
      when (io.wrData(qEmp)) {
        errorCause := 0.U
      }
    }
  } .otherwise {  // Register update outside bus operations
    // Refreshing queue-is-empty bit at every clock cycle other than bus reads/writes
    statusReg := Cat(errorCause.orR, statusReg(busDataWidth-2, 3), !cmdValid.asUInt.orR, statusReg(1, 0))
  }
  when (io.errCauseSel) {  // Error Cause register: RD (resettable by setting queue-empty bit of status reg.)
    when (io.rdWrN) {
      io.rdData := errorCause
    }
  } .otherwise {  // Register update outside bus operations
    errorCause := Cat(0.U(unusedBitsInErrorCause.W), setNoSize, setNoBaseAddress, setUnknownCommand)
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
  val loadRead = WireDefault(false.B)

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
  } .elsewhen (loadRead) {
    ldAValid(ldAPtr) := false.B
  }
  when (io.ldSizeSel && !io.rdWrN) {
    ldSValid(io.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.statusSel && !io.rdWrN && io.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      ldSValid(i) := false.B
  } .elsewhen (loadRead) {
    ldSValid(ldSPtr) := false.B
    ldSValid(ldSPtr+1.U) := false.B
  }

  // Providing information about the internal
  // state of Controller for testing
  if (testInternals) {
    io.statusReg  := statusReg
    io.errorCause := errorCause
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
  val dmaBusBaseAddr   = RegInit(0.U(busAddrWidth.W))
  val dmaBurstLen      = RegInit(0.U(localAddrWidth.W))
  val dmaMemSel = RegInit(true.B)
  val dmaStart  = RegInit(false.B)
  val widthA  = RegInit(0.U(log2Up(gridSize+1).W))
  val widthB  = RegInit(0.U(log2Up(gridSize+1).W))
  val heightA = RegInit(0.U(log2Up(gridSize+1).W))
  val heightB = RegInit(0.U(log2Up(gridSize+1).W))

  // Next state logic and related operations
  when (stateReg === idle) {
    when(statusReg(chEn) && !statusReg(qEmp) && !statusReg(erFl)) {
      stateReg := fetch
      cmdPtr := 0.U
      ldAPtr := 0.U
      ldSPtr := 0.U
    }
  } .elsewhen (stateReg === fetch) {
    when (statusReg(chEn)) {
      when(cmdValid(cmdPtr)) {
        stateReg := decode
        currCommand := commandRF(cmdPtr)
        cmdPtr := cmdPtr + 1.U
      } .otherwise {
        stateReg := idle
      }
    } .otherwise {
      stateReg := idle
    }
  } .elsewhen (stateReg === decode) {
    when (statusReg(chEn)) {
      when (currCommand === DUMMY.U) {
        stateReg := execute
      } .elsewhen ((currCommand === LOAD_A.U) || (currCommand === LOAD_B.U) || (currCommand === LOAD_K.U)) {
        stateReg := execute
        // Providing information for DMA
        when (ldAValid(ldAPtr)) {
          dmaBusBaseAddr := loadAddrRF(ldAPtr)
        } .otherwise {
          setNoBaseAddress := true.B
          stateReg := idle
        }
        when (ldSValid(ldSPtr) && ldSValid(ldSPtr+1.U)) {
          dmaBurstLen := loadSizeRF(ldSPtr)
          when (currCommand === LOAD_B.U) {
            widthB := loadSizeRF(ldSPtr+1.U)
          } .otherwise {  // LOAD_A or LOAD_K
            widthA := loadSizeRF(ldSPtr+1.U)
          }
        } .otherwise {
          setNoSize := true.B
          stateReg := idle
        }
        // Providing control signals for DMA
        dmaMemSel := (currCommand === LOAD_A.U || currCommand === LOAD_K.U)
        dmaStart  := ldAValid(ldAPtr) && ldSValid(ldSPtr) && ldSValid(ldSPtr+1.U)
        // Updating read pointers
        ldAPtr := ldAPtr + 1.U
        ldSPtr := ldSPtr + 2.U
      } .elsewhen ((currCommand === SET_SIGM.U) || (currCommand === SET_RELU.U)) {
        stateReg := execute
        actSelReg := (currCommand === SET_RELU.U)
      } .otherwise {
        setUnknownCommand := true.B
        stateReg := idle
      }
    } .otherwise {
      stateReg := idle
    }
  } .elsewhen (stateReg === execute) {
    when (dmaStart) {                    // If a load operation has just started
      stateReg := execute                // deactivate start and keep executing
      dmaStart := false.B
    } .elsewhen (!io.dma.done) {         // If a load operation is in progress
      stateReg := execute                // wait for it to end ("keep executing")
    } .elsewhen (cmdValid.asUInt.orR) {  // If no outside operation is in progress
      stateReg := fetch                  // then fetch a new command
    } .otherwise {                       // If no valid command remained
      stateReg := idle                   // then go idle
    }
  } .otherwise {
    stateReg := idle
  }

  // Signals to invalidate register data on read (to be continued...)
  cmdRead := (stateReg === fetch) && statusReg(chEn) && cmdValid(cmdPtr)
  loadRead := (stateReg === decode) && statusReg(chEn) && (
    (currCommand === LOAD_A.U) || (currCommand === LOAD_B.U) || (currCommand === LOAD_K.U))

  // Output driving from registers
  // - DMA
  io.dma.busBaseAddr := dmaBusBaseAddr
  io.dma.burstLen    := dmaBurstLen
  io.dma.rowLen      := Mux(currCommand === LOAD_B.U, widthB, widthA)
  io.dma.sel   := dmaMemSel
  io.dma.start := dmaStart
  // - Activation Grid
  io.actSel := actSelReg

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
  def getRegMap = ListMap(
    "Status Register"                         -> STATUS,
    "Error Cause Register"                    -> ERR_CAUSE,
    "Command Register File Base Address"      -> CMD_RF_B,
    "Load Address Register File Base Address" -> LD_ADDR_B,
    "Load Size Register File Base Address"    -> LD_SIZE_B
  )
  def getDataW = busDataWidth
}
