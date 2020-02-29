
## Memory organization
This section describes the memory organization of the neural network accelerator, continuing the discussion on required parameters of local memory.

### High bandwidth memory access
The first and most important parameter of local memory is its bandwidth. The 256 AUs of the Arithmetic Grid requires 2*256 B = 512 B per clock cycle (B/CC) to operate continuously. This high bandwidth can be provided by banked memory organization: the whole memory is decomposed into smaller blocks in a way that neighboring data bytes belong to neighboring memory blocks. This way, a larger portion of continuous address space can be accessed simultaneously. Banked memory access can provide high bandwidth but needs attention from the programmer to avoid bank collision, the situation where multiple access requests arrive to the same bank at the same time.

To enable collision-free memory access at least one memory bank should be available for all 256 AUs. Because every AU operates on two 8-bit values at the same time, two memory blocks per AU or two-byte-wide memory banking should be used. To keep the design simple and flexible we decide on two separate banks per AU, resulting in 512 memory banks total on the input side. This memory organization is formally separated into A and B sets of banks to hold parts of the two input matrices separately during matrix multiplication.

**Decision:** Local memory is partitioned into two larger blocks named A and B to hold the two matrices (or parts of them) during matrix multiplication. These memories are further decomposed into memory banks to provide high enough bandwidth to feed the computation core continuously.

### Size of local memory
The second most important parameter of local memory is its size. As it was discussed in [Arithmetic Units and the Arithmetic Grid](01_arithmetic_core.md), section of *Arithmetic Grid*, the "buffering factor" needed for a 16x16 sized AG is at least K = 128. This means at least 128*2*256 B = 64 kB of local memory is needed in the input side to match the available 4 B/CC bandwidth of the data bus with the 512 B/CC data consumption rate of AG. As it was noted, the minimum value of K assumes 100% bus utilization, which is unacceptable and not realistic.

To lover this ratio and to loosen up the addressing requirements created by the simultaneous loading and consuming of data, we can double the buffering factor and use ping-pong buffering. This results in 128 kB of local memory, a theoretical value of 50% bus utilization and easy separation of loading and consuming of data in the address space.

**Decision:** Local memory will have a total size of 128 kB, A and B memories 64 kB each. Both A and B will be composed from 256 banks, 256 memory locations each. A and B will have separate address inputs and each address space will be divided into a lower and an upper half to support ping-pong buffering of data.

### Broadcasting
One other issue related to memory organization is support for broadcasting. During matrix multiplication, each row and column is used to compute multiple outputs. To avoid collision or multiple read request for the same data, the hardware should support broadcasting.

The most universal way to enable broadcasting is to route every AU to every memory bank. This solution is very expensive: to connect 256 AUs with 2*256 memory banks we would need 256 multiplexers, each having 256 8-bit inputs and an 8-bit select signal. This solution is also very unnecessary: not all memory banks should be routed to all AUs because AG only supports certain configurations.

The two configurations supported by AG are 16x16 and 1x256. These require different sets of multiplexers:
- 16 multiplexers with 16 inputs,
- 256 direct connections ("multiplexers with 1 input"),
- 1 large multiplexer with 256 inputs.

Although implementing these three sets of connections for both memories already eliminates 2/3 of cost (if we measure cost in hardware inputs), the required amount of hardware could be further reduced if we implement only one of two complementing multiplexer sets for each memory. This would result in a slightly less flexibility but it should not be painful at all, while reducing cost with 77.8% relative to the fully connected implementation.

**Decision:** Broadcasting will be implemented with a reduced set of routing resources: memory A will have 16 multiplexers with 16 inputs and one large multiplexer with 256 input, while memory B will have 16 multiplexers with 16 inputs and 256 direct connections between memory banks and AUs in a one-to-one way.

### Kernel memory
In case of convolution, one input matrix is a small (max. 8x8) kernel which slides through the other input matrix. Because the way convolution is carried out in the accelerator (see [Convolution](convolution.md)) one small memory block with broadcast routes to every AU should be implemented. In order not to introduce additional hardware and routing cost we could use the first bank of memory A for this purpose. Throughout the whole convolution, the multiplexer with 256 inputs would be set to Bank 0 broadcasting its output to all AUs and address generation should only sweep through the first 64 bytes of Bank 0.

**Decision:** Kernel memory is introduced as a virtual memory block. In implementation, it is only the first bank of memory A with a special access pattern, which should be supported by the address generation hardware.

