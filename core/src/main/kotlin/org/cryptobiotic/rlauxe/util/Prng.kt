package org.cryptobiotic.rlauxe.util

import java.math.BigInteger
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Prng(seed: Long) {
    val internalSeed = longToByteArray(seed)
    val index = AtomicInteger(0)

    fun next(): Long {
        val hmac = HmacSha256(internalSeed)
        hmac.update(intToByteArray(index.incrementAndGet()))
        return hmac.finish()
    }
}

class HmacSha256(key : ByteArray) {
    val md : Mac = Mac.getInstance("HmacSHA256")
    init {
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        md.init(secretKey)
    }

    fun update(ba : ByteArray) {
        md.update(ba)
    }

    fun finish() : Long {
        return md.doFinal().toLong()
    }
}

fun intToByteArray (data: Int) : ByteArray {
    require (data >= 0)
    val dataLong : Long = data.toLong()
    return if (isBigEndian()) {
        ByteArray(4) { i -> (dataLong shr (i * 8)).toByte() }
    } else {
        ByteArray(4) { i -> (dataLong shr ((3-i) * 8)).toByte() }
    }
}

fun longToByteArray (data: Long) : ByteArray {
    require (data >= 0)
    return if (isBigEndian()) {
        ByteArray(8) { i -> (data shr (i * 8)).toByte() }
    } else {
        ByteArray(8) { i -> (data shr ((3-i) * 8)).toByte() }
    }
}

fun ByteArray.toLong(): Long {
    val result = this.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
    return if (result > 0) result else -result
}

fun isBigEndian(): Boolean = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN

////////////////////////////////////////////////////////////////////
class Prng2(seed: BigInteger) {
    val index = AtomicInteger(0)
    val md: Mac = Mac.getInstance("HmacSHA256")

    init {
        val secretKey = SecretKeySpec((seed.toString(10) + ",").toByteArray(), "HmacSHA256")
        md.init(secretKey)
    }

    fun next(): BigInteger {
        val n = index.getAndIncrement() // appears that the first one has 0 bytes
        val addto = ByteArray(n)
        md.update(addto)
        val result: ByteArray =  md.doFinal()
        // maybe should be a BigInteger, then a String ??
        return BigInteger(1, result) // positive
    }
}
