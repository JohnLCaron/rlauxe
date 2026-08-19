package org.cryptobiotic.rlauxe.corla

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
// class PseudoRandomNumberGenerator(
class CorlaPRNG(
    val seed: String, // at least of length MININUM_SEED_LENGTH and whose contents must only be digits.
    val with_replacement: Boolean, // True if we should "replace" drawn numbers once they are drawn
    val my_minimum: Int,
    val my_maximum: Int
) {
    private val sha256_digest = MessageDigest.getInstance("SHA-256")!!

    /**
     * The random numbers generated so far.
     */
    private val my_random_numbers = mutableListOf<Int>()

    /**
     * The current number to use for generation.
     */
    private var my_count = 0

    /**
     * The maximum index that can be generated without replacement.
     */
    private val my_maximum_index: Int

    /**
     * Create a pseudo-random number generator with functionality identical to
     * Rivest's `sampler.py` example implementation in Python of an RLA sampler.
     * 
     * @param the_seed The seed to generate random numbers from
     * @param the_with_replacement True if duplicates can be generated
     * @param the_minimum The minimum value to generate
     * @param the_maximum The maximum value to generate
     */
    //@ requires 20 <= the_seed.length();
    //@ requires seedOnlyContainsDigits(the_seed);
    //@ requires the_minimum <= the_maximum;
    init {
        // @trace randomness.seed side condition
        assert(MINIMUM_SEED_LENGTH <= seed.length)
        my_maximum_index = my_maximum - my_minimum + 1
    }

    /**
     * Generate the specified list of random numbers.
     * 
     * @param the_from the "index" of the first random number to give
     * @param the_to the "index" of the final random number to give
     * 
     * @return A list containing the_to - the_from + 1 random numbers
     */
    //@ requires the_from <= the_to;
    // @todo kiniry Refine this specification to include public model fields.
    // requires my_with_replacement || the_to <= my_maximum_index;
    fun getRandomNumbers(the_from: Int, the_to: Int): List<Int> {
        assert(the_from <= the_to)
        assert(with_replacement || the_to <= my_maximum_index)
        if (the_to + 1 > my_random_numbers.size) {
            extendList(the_to + 1)
        }
        // subList has an exclusive upper bound, but we have an inclusive one
        return my_random_numbers.subList(the_from, the_to + 1)
    }

    /**
     * A helper function to extend the list of generated random numbers.
     * @param the_length the number of random numbers to generate.
     */
    //@ private behavior
    //@   requires 0 <= the_length;
    //@   ensures my_random_numbers.size() == the_length;
    private fun extendList(the_length: Int) {
        while (my_random_numbers.size < the_length) {
            generateNext()
        }
    }

    /**
     * Attempt to generate the next random number. This will either extend the
     * list of random numbers in length or leave it the same. It will always
     * advance the count.
     */
    fun generateNext() {
        my_count++
        assert(with_replacement || my_count <= my_maximum_index)

        val hash_input = seed + "," + my_count

        val hash_output = sha256_digest.digest(hash_input.toByteArray(StandardCharsets.UTF_8))
        val int_output = BigInteger(1, hash_output)

        val in_range = int_output.mod(BigInteger.valueOf((my_maximum - my_minimum + 1).toLong()))
        val pick = my_minimum + in_range.intValueExact()

        if (with_replacement || !my_random_numbers.contains(pick)) {
            my_random_numbers.add(pick)
        }
    }

    fun digest(cardIndex: Int): Triple<ByteArray, BigInteger, Int> {
        val hash_input = seed + "," + cardIndex

        val digest = sha256_digest.digest(hash_input.toByteArray(StandardCharsets.UTF_8))
        val bigint = BigInteger(1, digest)

        val in_range = bigint.mod(BigInteger.valueOf((my_maximum - my_minimum + 1).toLong()))
        val pick = my_minimum + in_range.intValueExact()
        return Triple(digest, bigint, pick)
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
        /*@ behavior
    @   ensures (\forall int i; 0 <= i && i < the_seed.length(); 
    @            Character.isDigit(the_seed.charAt(i)));
    @*/
        /*@ pure @*/ fun seedOnlyContainsDigits(the_seed: String): Boolean {
            for (i in 0..<the_seed.length) {
                if (!Character.isDigit(the_seed.get(i))) {
                    return false
                }
            }
            return true
        }
    }
}
