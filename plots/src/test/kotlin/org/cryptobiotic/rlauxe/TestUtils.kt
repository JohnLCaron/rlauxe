package org.cryptobiotic.rlauxe

import org.cryptobiotic.rlauxe.core.*
import kotlin.math.abs

val doublePrecision = 1.0e-8

// def isclose(a, b, rtol=1.e-5, atol=1.e-8, equal_nan=False):
fun doubleIsClose(a: Double, b: Double, rtol: Double=1.0e-5, atol:Double=1.0e-8): Boolean {
    //    For finite values, isclose uses the following equation to test whether
    //    two floating point values are equivalent.
    //
    //     absolute(`a` - `b`) <= (`atol` + `rtol` * absolute(`b`))
    return abs(a - b) <= atol + rtol * abs(b)
}


fun makeStandardContest() =
    Contest(
        ContestInfo("standard", 0, mapOf("A" to 0,"B" to 1), choiceFunction = SocialChoiceFunction.PLURALITY),
        mapOf(0 to 3, 1 to 33),
        Nc = 0,
    )
fun makeStandardPluralityAssorter(): PluralityAssorter {
    val contest = makeStandardContest()
    return PluralityAssorter.makeWithVotes(contest, 0, 1, contest.votes)
}
fun makeStandardComparisonAssorter(avgCvrAssortValue: Double) =
    ComparisonAssorter(makeStandardContest(), makeStandardPluralityAssorter(), avgCvrAssortValue)