package org.cryptobiotic.rlauxe.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestPrng {

    @Test
    fun testPrng() {
        val prng1 = Prng(123456787901237890L)
        val prng2 = Prng(123456787901237890L)
        repeat (1000) {
            assertEquals(prng1.next(), prng2.next())
        }
        val prng3 = Prng(1234567879012378901L)
        repeat (1000) {
            assertNotEquals(prng1.next(), prng3.next())
        }
    }

    /*
    @Test
    fun testPrng2() {
        val prng0 = Prng2("123456787901237890")
        repeat (10) {
            println(prng0.next())
        }

        val prng1 = Prng2("123456787901237890")
        val prng2 = Prng2("123456787901237890")
        repeat (1000) {
            assertEquals(prng1.next(), prng2.next())
        }
        val prng3 = Prng2("23456787901237890")
        repeat (1000) {
            assertNotEquals(prng1.next(), prng3.next())
        }
    }

     */

    //     def test_sha256(self):
    //        auditSeed = 12345678901234567890
    //        prng = SHA256(auditSeed)
    //        print(f'{prng=}\n')
    //
    //        hashinput = (str(auditSeed) + ',').encode()
    //        print(f'hashinput={hashinput}\n')
    //        print(f'basehash={prng.basehash}\n')
    //
    //        random = prng.nextRandom()
    //        print(f'{random=} {len(random)}\n')
    //        sample_num = int_from_hash(random)
    //        print(f'{sample_num=} \n')


}

// prng = SHA256(audit.seed)
// CVR.assign_sample_nums(cvr_list, prng)
//
//     @classmethod
//    def assign_sample_nums(cls, cvr_list: list["CVR"], prng: "np.RandomState") -> bool:
//        """
//        Assigns a pseudo-random sample number to each cvr in cvr_list
//
//        Parameters
//        ----------
//        cvr_list: list of CVR objects
//        prng: instance of cryptorandom SHA256 generator
//
//        Returns
//        -------
//        True
//
//        Side effects
//        ------------
//        assigns (or overwrites) sample numbers in each CVR in cvr_list
//        """
//        for cvr in cvr_list:
//            cvr.sample_num = int_from_hash(prng.nextRandom())
//        return True

// cryptorandom.py
// SHA-256 PRNG prototype in Python
// https://statlab.github.io/cryptorandom/
//
//     def __init__(self, seed=None):
//        """
//        Initialize an instance of the SHA-256 PRNG.
//
//        Parameters
//        ----------
//        seed : {None, int, string} (optional)
//            Random seed used to initialize the PRNG. It can be an integer of arbitrary length,
//             a string of arbitrary length, or `None`. Default is `None`.
//        """
//        self.seed(seed)
//        self.hashfun = "SHA-256"
//        self._basehash()

//     def seed(self, baseseed=None):
//        """
//        Initialize internal seed and hashable object with counter 0.
//
//        Parameters
//        ----------
//        baseseed : {None, int, string} (optional)
//            Random seed used to initialize the PRNG. It can be an integer of arbitrary length,
//            a string of arbitrary length, or `None`. Default is `None`.
//        counter : int (optional)
//            Integer that counts how many times the PRNG has been called. The counter
//             is used to update the internal state after each step. Default is 0.
//        """
//        if not hasattr(self, 'baseseed') or baseseed != self.baseseed:
//            self.baseseed = baseseed
//            self._basehash()
//        self.counter = 0
//        self.randbits = None
//        self.randbits_remaining = 0

//     def _basehash(self):
//        """
//        Initialize the SHA256 hash function with given seed
//        """
//        if self.baseseed is not None:
//            hashinput = (str(self.baseseed) + ',').encode()
//            self.basehash = hashlib.sha256(hashinput)
//        else:
//            self.basehash = None

//     def nextRandom(self):
//        """
//        Generate the next hash value
//
//        >>> r = SHA256(12345678901234567890)
//        >>> r.next()
//        >>> r.nextRandom()
//        4da594a8ab6064d666eab2bdf20cb4480e819e0c3102ca353de57caae1d11fd1
//        """
//        # Apply SHA-256, interpreting digest output as integer
//        # to yield 256-bit integer (a python "long integer")
//        hash_output = self.basehash.digest()
//        self.next()
//        return hash_output

// def int_from_hash_py3(hash):
//    '''
//    Convert byte(s) to ints, specific for Python 3.
//
//    Parameters
//    ----------
//    hash : bytes
//        Hash or list of hashes to convert to integers
//
//    Returns
//    -------
//    int or list ndarray of ints
//    '''
//    if isinstance(hash, list):
//        hash_int = np.array([int.from_bytes(h, 'big') for h in hash])
//    else:
//        hash_int = int.from_bytes(hash, 'big')
//    return hash_int
// TODO looks like convert bytes -> hex string, then call int.from_bytes() whatever that is

// # Import library of cryptographic hash functions
//import hashlib

