package org.cryptobiotic.rlauxe.util

import java.security.SecureRandom
import kotlin.enums.EnumEntries
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
val version = "0.10.2.0"

val secureRandom = SecureRandom.getInstanceStrong()!!
const val doublePrecision = 1.0e-8

fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    val result =  abs(a - b) <= atol + rtol * abs(b)
    if (!result) {
        val t1 = abs(a - b)
        val t2 = abs(b)
        val t3 = atol + rtol * abs(b)
        print("")
    }
    return result
}

fun roundToClosest(x: Double) = round(x).toInt()
fun roundUp(x: Double) = ceil(x).toInt()
fun roundDown(x: Double) = floor(x).toInt()

fun listToMap(vararg names: String): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun listToMap(names: List<String>): Map<String, Int> {
    return names.mapIndexed { idx, value -> value to idx }.toMap()
}

fun margin2mean(margin: Double) = (margin + 1.0) / 2.0

fun mean2margin(mean: Double) = 2.0 * mean - 1.0
fun noerror(margin: Double, upper: Double) = 1.0 / (2.0 - margin / upper)

// this is duplicated in PluralityAssorter.calcMarginFromRegVotes
fun calcReportedMargin(useVotes: Map<Int, Int>, Nc: Int, winner: Int, loser: Int): Double {
    val winnerVotes = useVotes[winner] ?: 0
    val loserVotes = useVotes[loser] ?: 0
    return if (Nc == 0) 0.0 else (winnerVotes - loserVotes) / Nc.toDouble()
}

fun df(d: Double?) = if (d==null) "N/A" else "%6.4f".format(d)
fun dfn(d: Double?, n: Int) = if (d==null) "N/A" else "%${n+2}.${n}f".format(d)
fun pfn(d: Double?, n: Int=4) = if (d==null) "N/A" else "%${n+2}.${n}f%%".format(100*d)
fun pfz(d: Double?, n: Int=1) = if (d==null) "N/A" else {
    val s = "%${n+2}.${n}f%%".format(100*d)
    val toks = s.split(".")
    var tok0: String = toks[0]
    if (tok0.length == 1) tok0 = "0${tok0}"
    "$tok0.${toks[1]}"
}

fun nfn(i: Int, n: Int) = "%${n}d".format(i)
fun nfz(i: Int, n: Int) = i.toString().padStart(n, '0')
fun sfn(s: String, n: Int) = "%${n}s".format(s)  // +right/-left justify in width n
fun trunc(s: String, n:Int) : String {
    if (s.length > abs(n)) return s.substring(0,abs(n))
    if (s.length < abs(n)) return sfn(s, n)
    return s
}

fun Double.sigfig(minSigfigs: Int = 4): String {
    val df = "%.${minSigfigs}G".format(this)
    return if (df.startsWith("0.")) df.substring(1) else df
}

fun MutableMap<Int, Int>.mergeReduce(others: List<Map<Int, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

fun MutableMap<String, Int>.mergeReduceS(others: List<Map<String, Int>>) =
    others.forEach { other -> other.forEach { merge(it.key, it.value) { a, b -> a + b } } }

fun <T : Enum<T>> enumValueOf(name: String, entries: EnumEntries<T>): T? {
    for (status in entries) {
        if (status.name.lowercase() == name.lowercase()) return status
    }
    return null
}





