## Matrix multiplication
This section defines matrix multiplication, describes two different ways to parallelize it and examines memory usage of this operation.

### Definition of matrix multiplication

If $A$ is an $n\times k$ matrix ($A\in M_{n\times k}$) with elements $(A)_{ij} = a_{ij}$ and $B$ is a $k\times m$ matrix ($B\in M_{k\times m}$) with elements $(B)_{ij} = b_{ij}$, then the *matrix product* $C = AB$ is defined to be an $n\times m$ matrix with elements $(C)_{ij} = c_{ij} = \sum_{p=0}^k a_{ip}b_{pj}$ [1].

In other words, every element of matrix $C$ is the result of the dot-product between the $i$-th row of $A$ and the $j$-th column of $B$.

This definition gives us a straightforward way to compute the output matrix: for every element of $C$ we should compute the corresponding dot-product, each of which is a $k$-step multiply-accumulate process.

### Accelerating computation

The computational complexity of matrix multiplication is $\mathcal{O}(N^3)$ because if $A,B\in M_N$, the output $C=AB\in M_N$ which means one shall compute $N^2$ output values, each of which takes $N$ steps to compute. This complexity can result in very long computation times for large matrices: if $N = 10^3$ then $N^3 = 10^9$ which takes 2.5 seconds on a high-performance AD Blackfin DSP running on 400 MHz [2].

Computation can be accelerated in at least two ways: by parallelizing the computation of one output element, or by computing multiple output elements concurrently.

**Parallelizing the computation of one output elements** means we split the $k$-step process of computing $c_{ij} = \sum_{p=0}^k a_{ip}b_{pj}$ into $K$ parts and use $K$ processing units to compute them concurrently. Using this method, the $K$ results should be summed together as a final step to determine $c_{ij}$. If $K = k$, all processing units would compute only one multiplication, then a sequential or tree-structured summation should take place.

**Computing multiple outputs concurrently** means we use our $K$ processing units to compute $K$ outputs concurrently. In this way, each processing element sweeps through its own two input vectors and compute its output sequentially.

If we assume that every input data required by computations are provided, the latter method is more efficient because there is no dependency between results of processing units.

### Memory usage

#### Local memory

The discussion of parallelization assumed that every input value needed is available for the processing units all the time. In real hardware this means that to enable efficient parallelization the memory organization should be able to "feed" the processing units continuously.

By default, on-chip memories have a single port which means a single-port memory block can only support one read or one write operation in a given clock cycle. If we have $K$ processing units and each requires two input values of size $W_{in}$ bytes, then the local memory should provide $BW = K\cdot 2\cdot W_{in}$ bytes/clock-cycle (B/CC) bandwidth.

...

#### System memory

Assume we have the two input matrices stored in their own continuous fields of system memory. Further assume that it is physically a DRAM which have relatively high latency, hence memory access is usually carried out in bursts.

If we aim to compute $K$ output values in an $n\times m$ configuration (neighboring output values from $n$ rows an $m$ columns) then we need $n$ rows from $A$ and $m$ columns from $B$. Loading $n$ rows from $A$ would be efficient if we assume both matrices are large enough, but loading $m$ values from different rows of $B$ could be time consuming if $m$ is too small. To ...

### References
[1] Lipschutz, S.; Lipson, M. (2009). _Linear Algebra_. Schaum's Outlines (4th ed.). McGraw Hill (USA). pp. 30–31. ISBN 978-0-07-154352-1

[2] Analog Devices, Blackfin+ Core, datasheet, https://www.analog.com/media/en/technical-documentation/data-sheets/adsp-bf700_bf701_bf702_bf703_bf704_bf705_bf706_bf707.pdf


