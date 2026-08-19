# Compare Rlauxe and Corla
last updated 8/18/2026

## PRNG

* Corla uses MessageDigest.getInstance("SHA-256")
* Rlauxe uses HmacSha256

````
For pseudo-random number generation or key stretching, HMAC-SHA256 is cryptographically superior to plain MessageDigest.getInstance("SHA-256"). 
HMAC uses a secret key and a nested structure that prevents length-extension attacks and structural weaknesses inherent in 
raw Merkle–Damgård hash functions like plain SHA-256.
````

## Random Sampling

**Corla** uses the same seed for all counties. For each card index in (1 .. ncards) in the county's manifest, it assigns a PRN (psuedo random number)
in (1 .. ncards), creating a random sort of the manifest, and samples those cards in order. These PRNS are not unique across counties.

````
    fun digest(cardIndex: Int): Triple<ByteArray, BigInteger, Int> {
        val hash_input = seed + "," + cardIndex

        val digest = sha256_digest.digest(hash_input.toByteArray(StandardCharsets.UTF_8))
        val bigint = BigInteger(1, digest)

        val in_range = bigint.mod(BigInteger.valueOf((my_maximum - my_minimum + 1).toLong()))
        val pick = my_minimum + in_range.intValueExact()
        return Triple(digest, bigint, pick)
    }
````
This creates a uniform sample over each county manifest. Corla uses "sample-with-replacement".

Because they use BigInteger, this algorithm supports full 20 decimal digits (10^20).

TODO: what about "statewide contests" ?

**Rlauxe** assigns each card an unsigned long PRN (Maximum Value: 18,446,744,073,709,551,615 (2⁶⁴ - 1) = 1.844674407×10¹⁹.
These PRNS are unique across counties.

Rlauxe creates and stores the manifest sorted by the PRN, for speed of sampling. This creates a uniform or card-style sample over the entire election.
Rlauxe uses "sample-without-replacement".

