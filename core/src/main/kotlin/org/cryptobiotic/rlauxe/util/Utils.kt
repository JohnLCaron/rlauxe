package org.cryptobiotic.rlauxe.util

import java.security.SecureRandom
import kotlin.enums.EnumEntries
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

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

// these are the number of ballots needed THAT CONTAIN THE CONTEST
fun estSamplesFromNomargin(bet:Double, nomargin:Double, alpha: Double) =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
fun estSamplesFromNoerror(bet:Double, noerror:Double, alpha: Double): Double {
    val nomargin = 2.0 * noerror - 1.0
    return -ln(alpha) / ln(1.0 + bet * nomargin / 2)
}
// marginUpper = margin/upper
fun estSamplesFromMarginUpper(bet:Double, marginUpper:Double, alpha: Double): Double {
    val noerror = 1.0 / (2.0 - marginUpper)
    val nomargin = 2.0 * noerror - 1.0
    return -ln(alpha) / ln(1.0 + bet * nomargin / 2)
}
fun estMarginUpperFromSamples(bet:Double, samples:Int, alpha: Double): Double {
    // payoff^n = 1/alpha
    // ln(payoff) * n = -ln(alpha)
    // ln(1.0 + bet * nomargin / 2) = -ln(alpha) / n
    // ln(1.0 + bet * nomargin / 2) = -ln(alpha) / n
    // 1.0 + bet * nomargin / 2 = e^(-ln(alpha) / n)
    // 1.0 + bet * (noerror - 1/2) = e^(-ln(alpha) / n)

    // let term = e^(-ln(alpha) / n)
    // 1.0 + bet * (noerror - 1/2) = term
    // (noerror - 1/2) = (term - 1)/bet
    // noerror = (term - 1)/bet + 1/2
    // substitute noerror = 1/(2 - marginUpper)

    // 1/(2 - marginUpper) = (term - 1)/bet + 1/2
    // 1/(2 - marginUpper) = 2(term - 1)/2bet + bet/2bet
    // 1/(2 - marginUpper) = (2term - 2 + bet)/2bet
    // (2 - marginUpper) = 2bet/(2term - 2 + bet)
    // marginUpper = 2 - 2bet/(2term - 2 + bet)

    val term = exp(-ln(alpha) / samples)
    val den = (2.0*term - 2.0 + bet)
    return 2.0 - 2.0 * bet / den
}

// this is the estimate risk when nsamles CONTAIN THE CONTEST
fun estRisk(nomargin:Double, nsamples: Int): Double {
    return estRisk(2.0 / 1.03905, nomargin, nsamples)
}
// payoff^n = 1/risk; risk = 1/(payoff^n)
fun estRisk(bet:Double, nomargin:Double, nsamples: Int): Double {
    val payoff = 1.0 + bet * nomargin/2
    val payoffn = payoff.pow(nsamples.toDouble())
    val result =  1.0 / payoffn
    return result
}
// marginUpper = margin/upper
fun estRiskFromMargin(bet:Double, marginUpper:Double, nsamples: Int): Double {
    val noerror = 1.0 / (2.0 - marginUpper)
    val payoff = 1.0 + bet * (noerror - 0.5)
    val payoffn = payoff.pow(nsamples.toDouble())
    val result =  1.0 / payoffn
    return result
}

// this is duplicated in PluralityAssorter.calcMarginFromRegVotes
fun calcReportedMargin(useVotes: Map<Int, Int>, Nc: Int, winner: Int, loser: Int): Double {
    val winnerVotes = useVotes[winner] ?: 0
    val loserVotes = useVotes[loser] ?: 0
    return if (Nc == 0) 0.0 else (winnerVotes - loserVotes) / Nc.toDouble()
}

fun df(d: Double?) = if (d==null) "N/A" else "%6.4f".format(d)
fun dfn(d: Double?, n: Int) = if (d==null) "N/A" else "%${n+2}.${n}f".format(d)
fun pfn(d: Double?, n: Int=4) = if (d==null) "N/A" else "%${n+2}.${n}f%%".format(100*d)
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




