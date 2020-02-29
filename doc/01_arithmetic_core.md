
## Arithmetic Units and the Arithmetic Grid
This section describes the architecture of the main part of the neural network accelerator: Arithmetic Units and the Arithmetic Grid built up from them. The considerations and decisions of this section highly influence other parts of the design.

The two most important operations during inference of neural networks are matrix multiplication and convolution. These operations can be very time-consuming to compute sequentially due to the size of recent neural networks, but can be efficiently parallelized. To accelerate inference of neural networks we have to focus on these two operations and build arithmetic blocks specialized for them.

**Note:** Besides these arithmetic operations, non-linear activation functions are also a very important part of neural network inference. Their implementation is discussed in [Nonlinear function blocks](nonlinear_blocks.md).

### Arithmetic Unit
The Arithmetic Unit (AU) is a basic Multiply-Accumulate (MAC) unit inspired by Google's Tensor Processing Unit (TPU) from [1]. Lower precision is sufficient during inference of neural networks compared to their training phase, hence Google's TPU - and our AU - uses 8-bit inputs both for activation and network weights. Result of an 8-bit x 8-bit multiplication is represented in 16 bits, which then passed to a 32-bit accumulator.

This architecture is well suited for matrix multiplication and convolution. Because the accumulator is 32-bit wide it can sum up 65.536 16-bit values, enabling multiplication of matrices with size up to 65.536 x 65.536. This size may seem insanely large but recent neural networks can have several thousands of neurons in each layer, and a fully connected layer with N inputs and M outputs implements signal propagation as multiplication with a matrix that has a size of NxM.

**Decision:** The fundamental unit of arithmetic is a MAC-unit with two 8-bit (registered) inputs, a multiplier with 16-bit output register and a 32-bit accumulator. Further pipeline stages might be introduced later to improve performance.

### Arithmetic Grid
The Arithmetic Grid (AG) is a two-dimensional array of AUs, the "arithmetic core" of the accelerator. Multiple AUs are used to parallelize computation. The exact number and organization of AUs are derived from following considerations/requirements:

- The number of AUs should be large enough to provide high computational capacity.
- The number of AUs should not be too large. Large arrays require high amounts of hardware resources to implement. Also, providing a constant flow of data for the AUs requires large amounts of local memory with high enough bandwidth to feed all processing units at the same time.
- The number and organization of AUs should support the most common kernel-sizes efficiently (square windows from 2x2 to 8x8).

The first two requirements says we should find balance between performance and implementation cost/complexity. The third requirement targets the organization of AUs, the way they can be used, and it says it should operate efficiently in many common use cases.

At first draft, three sizes were considered: 16x16, 32x32 and 64x64, resulting in 256, 1024 and 4096 AUs, respectively. Smaller than 16x16 grids could not provide high enough performance to consider.

**Note:** Because computational units heavily rely on memory bandwidth, AG was designed alongside with the local memory organization and the following paragraph contains some considerations from [Memory organization](memory_organization.md).

N multiplications per clock cycle requires 2N bytes of data. This 2N bytes should be directly and instantly available for the AG, hence local memory should have a bandwidth of 2N bytes per clock cycle. Also, to provide constant data flow, at least 2N bytes should be loaded in every clock cycle. If we assume a 32-bit wide data bus, 4 bytes per clock cycle (4 B/CC) is available. To match this with the 2N B/CC requirement we have to use K*2N bytes of local memory where K is the "buffering factor" that should be determined and considered for all three size candidates.

- In case of a 16x16 AG, the requirement is 2\*256 = 512 B/CC bandwidth, hence K = 512/4 = 128. This means at least 128\*2\*256 B = 64 kB of local memory is required to keep up with the data consumption of AG.

- In case of a 32x32 AG, the requirement is 2\*1024 = 2048 B/CC bandwidth, hence K = 2048/4 = 512. This means at least 512\*2\*1024 B = 1 MB of local memory is required to keep up with the data consumption of AG.

- In case of a 64x64 AG, the requirement is 2\*4096 = 8192 B/CC bandwidth, hence K = 8192/4 = 2048. This means at least 2048\*2\*4096 B = 16 MB of local memory is required to keep up with the data consumption of AG.

The first thing to note that computed K factors are minimum requirements, because we assumed 100% bus utilization, which is unacceptable in a real system. To lower the required bus utilization ratio of our accelerator even higher K factor should be used which would result in multiple MBs of local memory in case of a larger AG. Also, 2 kB/CC and 8 kB/CC memory bandwidths require many parallel memory blocks which could require an unacceptably large amount routing resources if we want to use them flexibly.

**Decision:** The Arithmetic Grid accommodates 256 Arithmetic Units in a default organization of 16x16.

### Other configurations to support more efficient operation
Other configurations beside default 16x16 could also be supported to increase performance. Configurations of 256x1, 128x2, 64x4, 32x8, and complementing ones (e.g. 8x32, 1x256) were considered.

For convolution, the default, square shaped configuration was found to be most efficient because convolutional layers usually have square shaped inputs (images) with width and height less than 1000 pixels. Also, convolution is carried out in a way that every AU computes one output pixel, hence any configuration could be fully utilized independent of kernel size.

For matrix multiplication, assuming way larger matrices than AG, the "vector configuration" of 1x256 was found to be most efficient due to the nature of DRAM memory access. (For further elaboration, read [Matrix multiplication](matrix_multiplication.md).)

**Decision:** 1x256 configuration of AG will also be supported.

#### References
[1] Eric B. Olsen: "Proposal for a High Precision Tensor Processing Unit", Whitepaper, Digital System Research, Inc., v1.01, June 9th, 2017


