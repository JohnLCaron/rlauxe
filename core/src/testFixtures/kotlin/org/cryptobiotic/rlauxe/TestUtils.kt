package org.cryptobiotic.rlauxe

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val doublePrecision = 1.0e-8

fun doublesAreClose(a: List<Double>, b: List<Double>, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))

    assertEquals(a.size, b.size, "size differs")
    repeat(a.size) { assertTrue(doubleIsClose(a[it], b[it], rtol, atol), "$it: ${a[it]} !~ ${b[it]}") }
    return true
}

class SampleFromArray(val array: DoubleArray) {
    var index = 0
    fun sample() = array[index++]
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
    return PluralityAssorter.makeWithVotes(contest, 0, 1, contest.votes)
}



