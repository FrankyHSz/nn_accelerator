## Convolution
This section describes how convolution is carried out with the neural network accelerator. Throughout this section we assume an input matrix larger than the Arithmetic Grid.

### Setting up the environment
At first, the programmer should command the DMA unit to load part of the input matrix into memory A. Then, the size of the kernel should be specified and the kernel itself loaded into kernel memory (see [Memory organization](memory_organization.md)). Here we will assume an AG configuration of 16x16. We do not make any assumptions about kernel size but it will be considered in section *Loading the correct amount of data*.

**Note:** In case of convolutional neural networks, one parameter of a convolutional layer is padding. The accelerator hardware does not provide automatic padding, it remains the task of the programmer. Also, this first version of the accelerator, it only supports one channel. If multiple channels are needed (e.g. in case of an RGB image), the programmer should assemble every channel as separate matrices in memory and do the convolution for all of them one after another.

### Convolution
One Arithmetic Unit computes one output value (pixel) like in case of matrix multiplication.

At first, data fetching takes place: the first value of the kernel is broadcasted to all AUs while every AU receives its own data byte from the upper-left-most 16x16 block of memory A. Then, a MAC operation takes place. At the second step, the second value of the kernel is broadcasted and the 16x16 block shifts one step to right in memory A to feed the second MAC operation.

Right shifts take place until we reach the upper-right-most value of the kernel, when we step down one row and continue computation from left again. Computation continues until we we sweep through the whole kernel.

Then, we finished computation for 256 output values (pixels) for a 16x16 grid which can be saved into system memory and computation can start over from the 17th memory location of A and the first value of the kernel.

### Loading the correct amount of data
Convolution is a very different from matrix multiplication regarding memory access patterns, hence it is more complicated to load the correct amount of data into local memory.

If we assume a kernel size of NxN, an AU with two dimensional index [i, j] uses memory locations from [i, j] to [i+N-1, j+N-1]. To provide enough data to compute 256 output in a 16x16 grid we should load at least 16+N-1 rows that are 16+N-1 wide.

Because the accelerator supports kernel sizes up to 8x8 the programmer should load at least 16+7 = 23 rows with the same width. Because memory A has a size of 64 kB this means the maximum width of these rows could be 64k/23 = 2489. To keep address generation simple, the accelerator onyl supports rows with width of power-of-two. Because of this, the above derived maximum row length is reduced to 2048 but memory occupancy can be reserved by loading 32 rows instead of 23.

Having said the above considerations, the recommended way to load data into local memory is loading 32 rows of 2048 bytes. Assuming the maximum supported kernel size, this amount of data can be used to compute (32-7)x(2048-7) = 51.025 output values (pixels) on a 25x2041 grid.


