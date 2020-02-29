## Matrix multiplication
This section describes how matrix multiplication is carried out in the computational core of the neural network accelerator, also containing advises for programmers how to use it efficiently. Throughout this section we assume matrices way larger than 256x256.

### Setting up the environment
Assuming a valid matrix multiplication in form of P*Q, the programmer should select a suitable grid configuration for the dimensions of P and Q. Here we will assume a configuration of 1x256 as an example but the other configuration could also be used in case of smaller matrices and the operation would be similar.

At first, the dimensions of the input matrices should be specified. After the accelerator and its DMA unit were programmed with this information, part of P and Q should be loaded. Because both memories A and B have a size of 64 kB, memory A can surely accommodate the first row of P as a whole, while from Q, the left-most 256x256 block should be loaded into memory B (i.e. the first 256 bytes of every rows from the first 256 rows).

### Multiplication
Every Arithmetic Unit will compute one output value. This way, after AG sweeps through Q vertically, 256 values from one row will be computed.

Multiplication starts with data fetch both from memory A and B. The first byte of memory A is broadcasted to every AUs, while the first 256 bytes of memory B travels to their corresponding AUs. Then the MAC operation takes place. The second byte of A and the second row of B is fetched for the second MAC operation, the third byte of A and third row of B fetched for the third MAC operation, and so on.

The next interesting step is when we reach step number 127. After the corresponding MAC operation, half of memory B was already used, so the DMA unit can take it to load the next 128 rows (ping-pong buffering). The time we reach the end of memory B, the next 128 rows will be loaded into the first half, so address generation can simply wrap around and continue the multiplication while DMA takes over the second half of memory B.

### Computation cycles
Data fetching and computing goes on until we reach the end of the first row of P and simultaneously the last row of Q. The first 256 values are ready to be saved into system memory and computation of the next 256 can start. The whole computation process for 256 output values is called a computation cycle.

To maximize data reuse between computation cycles, the first row of P stays in memory A while the second 256 bytes from the first 256 rows of Q are loaded into memory B. The whole computation starts over and repeats until the whole Q is covered i.e. the whole first row of the output matrix is computed. After that, the second row of P is loaded to memory A and sweeping through B restarts from the beginning.

### Notes on performance
With reusing rows from P as many times as we can and using 256 long rows of data the operation is already accommodates some optimizations regarding system memory access.

But one more optimization can still be added: if the first row of P is smaller than 65.536, e.g. 4096, we could load more than one row into memory A, merging multiple shorter load operations into one larger. If we do that, we can also introduce higher data reuse because we could compute more than one output with one AU. The downside of this operation is we have to save and reload the values of accumulators when we switch between rows of P.

**Note:** Investigation should be carried out on the potential performance benefits of computing more than one output per AU.

