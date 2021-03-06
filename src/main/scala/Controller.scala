
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
  // ...
  val erFl = busDataWidth-1  // Error flag: signals that an error occurred during the last operation (RD)
  // Related
  val unusedBitsInStatus = busDataWidth - 6

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
  val CONV_S   = 8  // [0...01000] Performs (single) convolution on previously loaded data. N.f.d.r.
  val MMUL_S   = 9  // [0...01001] Performs (single) matrix multiplication on previously loaded data. N.f.d.r.
  val STORE    = 16 // [0..010000] Stores the result of last operation from output memory to system memory. Needs a
                    //             base address in system memory to know where to save the data.

  // Useful constants for module
  val blockAddrW  = log2Up(busDataWidth)
  val numOfStates = 4
  val stateRegW   = log2Up(numOfStates)


  // ----------------
  // Module interface
  // ----------------

  val io = IO(new Bundle {

    val bus = new Bundle {
      // RD/WR interface for register files
      val addr = Input(UInt(blockAddrW.W)) // Decoded address (offset inside IP address space)
      val wrData = Input(UInt(busDataWidth.W))
      val rdData = Output(UInt(busDataWidth.W))
      val rdWrN = Input(Bool())

      // Decoded block select signals from Bus Interface
      val statusSel = Input(Bool())
      val errCauseSel = Input(Bool())
      val commandSel = Input(Bool())
      val ldAddrSel = Input(Bool())
      val ldSizeSel = Input(Bool())
    }

    // DMA interface
    val dma = new Bundle() {
      val busBaseAddr   = Output(UInt(busAddrWidth.W))
      val ldHeight = Output(UInt(log2Up(gridSize+1).W))
      val ldWidth  = Output(UInt(log2Up(gridSize+1).W))
      val rdWrN = Output(Bool())
      val sel   = Output(Bool())
      val kernel = Output(Bool())
      val start = Output(Bool())
      val done  = Input(Bool())
    }

    // Activation select for the Activation Grid
    val actSel = Output(Bool())

    // Control signals for LoadUnit
    val ldunit = new Bundle {
      val computeEnable = Output(Bool())
      val computeDone = Input(Bool())
      val mulConvN = Output(Bool())
      val widthA = Output(UInt(log2Up(gridSize + 1).W))
      val widthB = Output(UInt(log2Up(gridSize + 1).W))
      val heightA = Output(UInt(log2Up(gridSize + 1).W))
      val heightB = Output(UInt(log2Up(gridSize + 1).W))
    }

    // Test port
    // - Memory-mapped and internal registers
    val statusReg  = Output(UInt(busDataWidth.W))
    val errorCause = Output(UInt(busDataWidth.W))
    val cmdValid   = Output(Vec(busDataWidth, Bool()))
    val commandRF  = Output(Vec(busDataWidth, UInt(busDataWidth.W)))
    val ldAValid   = Output(Vec(busDataWidth, Bool()))
    val ldSValid   = Output(Vec(busDataWidth, Bool()))
    val loadAddrRF = Output(Vec(busDataWidth, UInt(busDataWidth.W)))
    val loadSizeRF = Output(Vec(busDataWidth, UInt(busDataWidth.W)))
    // - Registers of state machine
    val stateReg    = Output(UInt(stateRegW.W))
    val cmdPtr      = Output(UInt(blockAddrW.W))
    val ldAPtr      = Output(UInt(blockAddrW.W))
    val ldSPtr      = Output(UInt(blockAddrW.W))
    val currCommand = Output(UInt(busDataWidth.W))
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

  // States
  val idle :: fetch :: decode :: execute :: Nil = Enum(numOfStates)
  // State register
  val stateReg = RegInit(idle)

  // Register RD/WR logic
  io.bus.rdData := 0.U
  when (io.bus.statusSel) {  // Control/status register: RD/WR
    when (io.bus.rdWrN) {
      io.bus.rdData := statusReg
    } .otherwise {
      val itFlag   = statusReg(itFl) && io.bus.wrData(itFl)
      val bits4to3 = Cat(itFlag, io.bus.wrData(itEn))
      val queueEmpty = statusReg(qEmp) || io.bus.wrData(qEmp)
      val bits4to0 = Cat(bits4to3, queueEmpty, statusReg(busy), io.bus.wrData(chEn))
      statusReg := Cat(statusReg(erFl), 0.U(unusedBitsInStatus.W), bits4to0)

      // When the programmer invalidates all registers by setting the queue-empty bit
      // then all error flags should be cleared
      when (io.bus.wrData(qEmp)) {
        errorCause := 0.U
      }
    }
  } .otherwise {  // Register update outside bus operations
    // Refreshing queue-is-empty  and busy bits at every clock cycle other than bus reads/writes
    statusReg := Cat(errorCause.orR, statusReg(busDataWidth-2, 3),
      !cmdValid.asUInt.orR, (stateReg =/= idle), statusReg(0))
  }
  when (io.bus.errCauseSel) {  // Error Cause register: RD (resettable by setting queue-empty bit of status reg.)
    when (io.bus.rdWrN) {
      io.bus.rdData := errorCause
    }
  } .otherwise {  // Register update outside bus operations
    errorCause := Cat(0.U(unusedBitsInErrorCause.W), setNoSize, setNoBaseAddress, setUnknownCommand)
  }
  when(io.bus.commandSel) {  // Command register file: WR
    when(!io.bus.rdWrN) {
      commandRF(io.bus.addr(blockAddrW - 1, 0)) := io.bus.wrData
    }
  }
  when(io.bus.ldAddrSel) {   // Load Address register file: WR
    when(!io.bus.rdWrN) {
      loadAddrRF(io.bus.addr(blockAddrW - 1, 0)) := io.bus.wrData
    }
  }
  when(io.bus.ldSizeSel) {   // Load Size register file: WR
    when(!io.bus.rdWrN) {
      loadSizeRF(io.bus.addr(blockAddrW - 1, 0)) := io.bus.wrData
    }
  }

  // Read pointers
  val cmdPtr = RegInit(0.U(blockAddrW.W))
  val ldAPtr = RegInit(0.U(blockAddrW.W))
  val ldSPtr = RegInit(0.U(blockAddrW.W))

  // Invalidate-on-read signals
  val cmdRead = WireDefault(false.B)
  val loadRead = WireDefault(false.B)
  val storeRead = WireDefault(false.B)

  // Validity registers
  // - Writing a word makes that word valid
  // - Setting the queue-empty bit in statusReg makes every word invalid
  // - Reading a word makes that word invalid
  when (io.bus.commandSel && !io.bus.rdWrN) {
    cmdValid(io.bus.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.bus.statusSel && !io.bus.rdWrN && io.bus.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      cmdValid(i) := false.B
  } .elsewhen (cmdRead) {
    cmdValid(cmdPtr) := false.B
  }
  when (io.bus.ldAddrSel && !io.bus.rdWrN) {
    ldAValid(io.bus.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.bus.statusSel && !io.bus.rdWrN && io.bus.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      ldAValid(i) := false.B
  } .elsewhen (loadRead) {
    ldAValid(ldAPtr) := false.B
  }
  when (io.bus.ldSizeSel && !io.bus.rdWrN) {
    ldSValid(io.bus.addr(blockAddrW - 1, 0)) := true.B
  } .elsewhen (io.bus.statusSel && !io.bus.rdWrN && io.bus.wrData(qEmp)) {
    for (i <- 0 until busDataWidth)
      ldSValid(i) := false.B
  } .elsewhen (loadRead) {
    ldSValid(ldSPtr) := false.B
    ldSValid(ldSPtr+1.U) := false.B
  }

  // Providing information about the internal
  // state of Controller for testing
  io.statusReg  := statusReg
  io.errorCause := errorCause
  io.cmdValid   := cmdValid
  io.commandRF  := commandRF
  io.ldAValid   := ldAValid
  io.ldSValid   := ldSValid
  io.loadAddrRF := loadAddrRF
  io.loadSizeRF := loadSizeRF



  // ------------------
  // FSM to operate NNA
  // ------------------

  // Command ("instruction") and operand registers (to be continued...)
  val currCommand = RegInit(0.U(busDataWidth.W))
  val dmaBusBaseAddr   = RegInit(0.U(busAddrWidth.W))
  val dmaBurstLen      = RegInit(0.U(localAddrWidth.W))
  val dmaMemSel = RegInit(true.B)
  val kernelFlag = RegInit(false.B)
  val dmaRdWrN  = RegInit(true.B)
  val dmaStart  = RegInit(false.B)
  val widthAReg  = RegInit(0.U(log2Up(gridSize+1).W))
  val widthBReg  = RegInit(0.U(log2Up(gridSize+1).W))
  val heightAReg = RegInit(0.U(log2Up(gridSize+1).W))
  val heightBReg = RegInit(0.U(log2Up(gridSize+1).W))
  val computeEnableReg = RegInit(false.B)
  val computeStart     = RegInit(false.B)
  val mulConvNReg      = RegInit(false.B)
  val loadedOpOne = RegInit(false.B)
  val loadedOpTwo = RegInit(false.B)
  val nothing :: mmul :: conv :: Nil = Enum(3)
  val lastComputation = RegInit(nothing)

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
          when (currCommand === LOAD_B.U) {
            heightBReg := loadSizeRF(ldSPtr)
            widthBReg  := loadSizeRF(ldSPtr+1.U)
          } .otherwise {  // LOAD_A or LOAD_K
            heightAReg := loadSizeRF(ldSPtr)
            widthAReg  := loadSizeRF(ldSPtr+1.U)
          }
        } .otherwise {
          setNoSize := true.B
          stateReg := idle
        }
        // Providing control signals for DMA
        dmaMemSel := (currCommand === LOAD_A.U || currCommand === LOAD_K.U)
        kernelFlag := (currCommand === LOAD_K.U)
        dmaStart  := ldAValid(ldAPtr) && ldSValid(ldSPtr) && ldSValid(ldSPtr+1.U)
        dmaRdWrN  := ldAValid(ldAPtr) && ldSValid(ldSPtr) && ldSValid(ldSPtr+1.U)
        // Updating read pointers
        ldAPtr := ldAPtr + 1.U
        ldSPtr := ldSPtr + 2.U
      } .elsewhen ((currCommand === SET_SIGM.U) || (currCommand === SET_RELU.U)) {
        stateReg := execute
        actSelReg := (currCommand === SET_RELU.U)
      } .elsewhen ((currCommand === CONV_S.U) || (currCommand === MMUL_S.U)) {
        when (loadedOpOne && loadedOpTwo) {
          stateReg := execute
          loadedOpOne := false.B
          loadedOpTwo := false.B
          computeEnableReg := true.B
          computeStart := true.B
          mulConvNReg      := (currCommand === MMUL_S.U)
        }
      } .elsewhen (currCommand === STORE.U) {
        when (ldAValid(ldAPtr)) {
          stateReg := execute
          dmaBusBaseAddr := loadAddrRF(ldAPtr)
          when (lastComputation === mmul) {
            // heightAReg := heightAReg (does not change)
            widthAReg  := widthBReg
          } .elsewhen (lastComputation === conv) {
            heightAReg := heightBReg - widthAReg + 1.U
            widthAReg  := widthBReg - widthAReg + 1.U
          }
          dmaStart := true.B
          dmaRdWrN := false.B
        }
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
    } .elsewhen (computeStart) {
      stateReg := execute
      computeStart := false.B
    } .elsewhen (!io.ldunit.computeDone) {      // If a computation is in progress
      statusReg := execute               // wait for it to end ("keep executing")
    } .elsewhen (cmdValid.asUInt.orR) {  // If no outside operation is in progress
      stateReg := fetch                  // then fetch a new command
      when (currCommand === LOAD_A.U || currCommand === LOAD_K.U) {
        loadedOpOne := true.B
      }
      when (currCommand === LOAD_B.U) {
        loadedOpTwo := true.B
      }
      when (currCommand === MMUL_S.U || currCommand === CONV_S.U) {
        computeEnableReg := false.B
        when (currCommand === MMUL_S.U) {
          lastComputation := mmul
        } .otherwise {
          lastComputation := conv
        }
      }
    } .otherwise {                       // If no valid command remained
      stateReg := idle                   // then go idle
      when (currCommand === LOAD_A.U || currCommand === LOAD_K.U) {
        loadedOpOne := true.B
      }
      when (currCommand === LOAD_B.U) {
        loadedOpTwo := true.B
      }
      when (currCommand === MMUL_S.U || currCommand === CONV_S.U) {
        computeEnableReg := false.B
      }
    }
  } .otherwise {
    stateReg := idle
  }

  // Signals to invalidate register data on read (to be continued...)
  cmdRead := (stateReg === fetch) && statusReg(chEn) && cmdValid(cmdPtr)
  loadRead := (stateReg === decode) && statusReg(chEn) && (
    (currCommand === LOAD_A.U) || (currCommand === LOAD_B.U) || (currCommand === LOAD_K.U))
  storeRead := (stateReg === decode) && statusReg(chEn) && (currCommand === STORE.U)

  // Output driving from registers
  // - DMA
  io.dma.busBaseAddr := dmaBusBaseAddr
  io.dma.ldHeight := Mux(currCommand === LOAD_B.U, heightBReg, heightAReg)
  io.dma.ldWidth  := Mux(currCommand === LOAD_B.U, widthBReg, widthAReg)
  io.dma.rdWrN := dmaRdWrN
  io.dma.sel   := dmaMemSel
  io.dma.kernel := kernelFlag
  io.dma.start := dmaStart
  // - Activation Grid
  io.actSel := actSelReg
  // - Load Unit
  io.ldunit.computeEnable := computeEnableReg
  io.ldunit.mulConvN      := mulConvNReg
  io.ldunit.widthA  := widthAReg
  io.ldunit.widthB  := widthBReg
  io.ldunit.heightA := heightAReg
  io.ldunit.heightB := heightBReg

  // Providing information about the internal
  // state of Controller for testing
  io.stateReg    := stateReg
  io.cmdPtr      := cmdPtr
  io.ldAPtr      := ldAPtr
  io.ldSPtr      := ldSPtr
  io.currCommand := currCommand


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

object Controller {
  // Control/status register bits (indices) -- RD: readable, WR: writable, CL: cleanable, ST: settable
  val chEn = 0  // Chip enable      : enables operation according to the command queue (RD/WR)
  val busy = 1  // Busy flag        : signals that operations are under execution      (RD)
  val qEmp = 2  // Queue is empty   : signals that no operation is specified           (RD/ST)
  val itEn = 3  // Interrupt enable : enables generation of processor interrupts       (RD/WR)
  val itFl = 4  // Interrupt flag   : signals processor interrupt (if enabled)         (RD/CL)
  // ...
  val erFl = busDataWidth-1  // Error flag: signals that an error occurred during the last operation (RD)
  // Related
  val unusedBitsInStatus = busDataWidth - 6

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
  val CONV_S   = 8  // [0...01000] Performs (single) convolution on previously loaded data. N.f.d.r.
  val MMUL_S   = 9  // [0...01001] Performs (single) matrix multiplication on previously loaded data. N.f.d.r.
  val STORE    = 16 // [0..010000] Stores the result of last operation from output memory to system memory. Needs a
  //             base address in system memory to know where to save the data.
}