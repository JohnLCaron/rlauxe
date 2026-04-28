package org.cryptobiotic.rlauxe.port

import java.security.SecureRandom
import kotlin.random.Random

//     def choice(self, a, size=None, replace=True, p=None): # real signature unknown; restored from __doc__
//        """
//        choice(a, size=None, replace=True, p=None)
//
//                Generates a random sample from a given 1-D array
//
//                .. versionadded:: 1.7.0
//
//                .. note::
//                    New code should use the `~numpy.random.Generator.choice`
//                    method of a `~numpy.random.Generator` instance instead;
//                    please see the :ref:`random-quick-start`.
//
//                Parameters
//                ----------
//                a : 1-D array-like or int
//                    If an ndarray, a random sample is generated from its elements.
//                    If an int, the random sample is generated as if it were ``np.arange(a)``
//                size : int or tuple of ints, optional
//                    Output shape.  If the given shape is, e.g., ``(m, n, k)``, then
//                    ``m * n * k`` samples are drawn.  Default is None, in which case a
//                    single value is returned.
fun python_choice(from: DoubleArray, size: Int): DoubleArray {
    val n = from.size
    if (n <= 0)
        println("HEY")
    return DoubleArray(size) { from[Random.nextInt(n)] }
}

// python bool(int), i think
fun python_bool(v: Int?): Boolean {
    return if (v == null || v == 0) false else true
}

interface SampleFn {
    fun sample(): Double
    fun reset()
    fun sampleMean(): Double
    fun N(): Int
}

class SampleFromArrayWithoutReplacement(val samples : DoubleArray): SampleFn {
    val selectedIndices = mutableSetOf<Int>()
    val N = samples.size

    override fun sample(): Double {
        while (true) {
            val idx = Random.nextInt(N) // withoutReplacement
            if (!selectedIndices.contains(idx)) {
                selectedIndices.add(idx)
                return samples[idx]
            }
            require(selectedIndices.size < samples.size)
        }
    }
    override fun reset() {
        selectedIndices.clear()
    }

    override fun sampleMean() = samples.average()
    override fun N() = N
}

fun randomShuffle(samples : DoubleArray): DoubleArray {
    val n = samples.size
    val permutedIndex = MutableList(n) { it }
    permutedIndex.shuffle(Random)
    return DoubleArray(n) { samples[permutedIndex[it]] }
}