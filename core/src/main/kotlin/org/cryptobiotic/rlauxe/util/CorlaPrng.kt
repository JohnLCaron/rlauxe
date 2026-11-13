package org.cryptobiotic.rlauxe.util

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * A pseudo-random number generator based on Philip Stark's pseudo-random number
 * generator found at
 * [https://www.stat.berkeley.edu/~stark/Java/Html/sha256Rand.htm](https://www.stat.berkeley.edu/~stark/Java/Html/sha256Rand.htm).
 * 
 * @author Joey Dodds <jdodds></jdodds>@freeandfair.us>
 * @author Joseph R. Kiniry <kiniry></kiniry>@freeandfair.us>
 * @version 1.0.0
 * @review kiniry Why is this not just a static class?
 */
/**
 * Create a pseudo-random number generator with functionality identical to
 * Rivest's `sampler.py` example implementation in Python of an RLA sampler.
 *
 * @param the_seed The seed to generate random numbers from
 * @param the_with_replacement True if duplicates can be generated
 * @param the_minimum The minimum value to generate
 * @param the_maximum The maximum value to generate
 */
// class PseudoRandomNumberGenerator(
class CorlaPrng(
    val seed: String, // at least of length MININUM_SEED_LENGTH and whose contents must only be digits.
    // val with_replacement: Boolean, // True if we should "replace" drawn numbers once they are drawn
    val my_minimum: Int,
    val my_maximum: Int
) {
    private val sha256_digest = MessageDigest.getInstance("SHA-256")!!
    private var my_count = 0

    /** The maximum index that can be generated without replacement. */
    private val my_maximum_index: Int
    val prnIds = mutableSetOf<Int>()

    init {
        assert(MINIMUM_SEED_LENGTH <= seed.length)
        my_maximum_index = my_maximum - my_minimum + 1
    }

    fun next(): Int {
        my_count++
        // assert(my_count <= my_maximum_index)

        // CORLA's own SHA-256 PRNG draw sequence from the public random seed and the county ballot manifest(s)
        // (SHA256(seed + "," + i) mod domain_size,
        val hash_input = seed + "," + my_count

        val hash_output = sha256_digest.digest(hash_input.toByteArray(StandardCharsets.UTF_8))

        // TODO get rid of BigInteger
        val int_output = BigInteger(1, hash_output)
        // domain size = my_maximum - my_minimum + 1
        val in_range = int_output.mod(BigInteger.valueOf((my_maximum - my_minimum + 1).toLong()))
        val pick = my_minimum + in_range.intValueExact()

        if (!prnIds.add(pick)) return next()
        return pick
    }

    companion object {
        /**
         * The minimum seed length specified in CRLS, and hence our formal specification,
         * is 20 characters.
         * @trace corla.randomness.seed
         */
        const val MINIMUM_SEED_LENGTH: Int = 20

        /**
         * Checks to see if the passed potential seed only contains digits.
         * @param the_seed is the seed to check.
         */
        fun seedOnlyContainsDigits(the_seed: String): Boolean {
            for (i in 0..<the_seed.length) {
                if (!Character.isDigit(the_seed.get(i))) {
                    return false
                }
            }
            return true
        }
    }
}
