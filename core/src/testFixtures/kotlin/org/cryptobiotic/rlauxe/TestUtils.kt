package org.cryptobiotic.rlauxe

import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val testdataDir = "/home/stormy/rla"

fun doublesAreClose(a: List<Double>, b: List<Double>, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    assertEquals(a.size, b.size, "size differs")
    repeat(a.size) { assertTrue(doubleIsClose(a[it], b[it], rtol, atol), "$it: ${a[it]} !~ ${b[it]}") }
    return true
}

class SampleFromArray(val array: DoubleArray): SamplerTracker {
    val welford = Welford()

    var index = 0
    override fun sample(): Double {
        val nextValue = array[index++]
        welford.update(nextValue)
        return nextValue
    }

    override fun maxSamples() = array.size

    override fun maxSampleIndexUsed() = index

    override fun nmvrs() = array.size

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun numberOfSamples() = index

    override fun welford() = welford

    override fun done() {
    }

    override fun last() = array[index]

    override fun sum() = welford.sum()

    override fun mean() = welford.mean

    override fun variance() = welford.variance()

    override fun addSample(sample: Double) {
        TODO("Not yet implemented")
    }

    override fun next() = sample()

    override fun hasNext() = index < array.size - 1
}

class SampleFromList(val list: List<Double>) {
    var index = 0
    fun sample() = list[index++]
}

// deprecate
fun makeStandardContest(Nc: Int) =
    Contest(
        ContestInfo("standard", 0, mapOf("A" to 0,"B" to 1), choiceFunction = SocialChoiceFunction.PLURALITY),
            mapOf(0 to 3, 1 to 33),  // TODO BOGUS
            Nc = Nc,
            Ncast=0, // TODO
        )

fun makeStandardPluralityAssorter(Nc: Int): PluralityAssorter {
    val contest = makeStandardContest(Nc)
    return PluralityAssorter.makeWithVotes(contest, 0, 1, Nc)
}



