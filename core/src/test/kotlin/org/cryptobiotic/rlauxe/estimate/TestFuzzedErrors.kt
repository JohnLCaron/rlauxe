package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ClcaErrorRatesCumul
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.util.dfn
import org.junit.jupiter.api.Test

class TestFuzzedErrors {

    @Test
    fun testFuzzedCardsErrors() {
        val show = false
        val ncontests = 11
        val phantomPct = 0.02
        val test = MultiContestTestData(ncontests, 1, 50000, phantomPctRange=phantomPct..phantomPct)
        val contestsUA = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val cards = test.makeCardsFromContests()
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        println(" testFuzzedCardsErrors phantomPct = $phantomPct")
        println("              ${ClcaErrorRatesCumul.header()}")

        fuzzPcts.forEach { fuzzPct ->
            val fcards = makeFuzzedCardsFrom(contestsUA.map { it.contest} , cards, fuzzPct)
            val testPairs = fcards.zip(cards)

            val avgErrorRates = ClcaErrorRatesCumul()
            contestsUA.forEach { contestUA ->
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val samples = PrevSamplesWithRates(cassorter.noerror())
                    if (show) println("  contest = ${contestUA.id} assertion = ${cassorter.shortName()}")

                    testPairs.forEach { (fcard, card) ->
                        if (card.hasContest(contestUA.id)) {
                            samples.addSample(cassorter.bassort(fcard.cvr(), card.cvr()))
                        }
                    }
                    if (show) println("    errorCounts = ${samples.errorCounts()}")
                    if (show) println("    errorRates =  ${samples.errorRates()}")

                    avgErrorRates.add(samples.errorRates())
                }
            }
            println("fuzzPct ${dfn(fuzzPct,3)}: ${avgErrorRates}")
        }
        println()
    }
    /*
     testFuzzedCardsErrors phantomPct = 0.0
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0000, 0.0000, 0.0000, 0.00000
fuzzPct 0.001: 0.0001, 0.0003, 0.0003, 0.0001, 0.00080
fuzzPct 0.005: 0.0007, 0.0019, 0.0012, 0.0005, 0.00434
fuzzPct 0.010: 0.0013, 0.0038, 0.0024, 0.0008, 0.00842
fuzzPct 0.020: 0.0025, 0.0070, 0.0046, 0.0015, 0.01562
fuzzPct 0.050: 0.0067, 0.0188, 0.0121, 0.0042, 0.04171

 testFuzzedCardsErrors phantomPct = 0.02
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0196, 0.0000, 0.0000, 0.01961
fuzzPct 0.001: 0.0001, 0.0198, 0.0002, 0.0001, 0.02028
fuzzPct 0.005: 0.0008, 0.0212, 0.0013, 0.0007, 0.02392
fuzzPct 0.010: 0.0016, 0.0229, 0.0028, 0.0014, 0.02868
fuzzPct 0.020: 0.0031, 0.0257, 0.0052, 0.0028, 0.03677
fuzzPct 0.050: 0.0077, 0.0350, 0.0125, 0.0064, 0.06162
     */

    @Test
    fun testFuzzedCvrsErrors() {
        val show = false
        val ncontests = 11
        val phantomPct = .02
        val test = MultiContestTestData(ncontests, 1, 50000, phantomPctRange=phantomPct..phantomPct)
        val contestsUA = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val cvrs = test.makeCvrsFromContests()
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        println("phantomPct = $phantomPct")
        println("               ${ClcaErrorRatesCumul.header()}")

        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(test.contests, cvrs, fuzzPct)
            val testPairs = cvrs.zip(fcvrs)

            val cumulErrorRates = ClcaErrorRatesCumul()
            contestsUA.forEach { contestUA ->
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val samples = PrevSamplesWithRates(cassorter.noerror())
                    if (show) println("  contest = ${contestUA.id} assertion = ${cassorter.shortName()}")

                    testPairs.forEach { (mvr, cvr) ->
                        if (cvr.hasContest(contestUA.id)) {
                            samples.addSample(cassorter.bassort(mvr, cvr))
                        }
                    }
                    if (show) println("    errorCounts = ${samples.errorCounts()}")
                    if (show) println("    errorRates =  ${samples.errorRates()}")

                    cumulErrorRates.add(samples.errorRates())
                }
            }
            println("fuzzPct ${dfn(fuzzPct,3)}: ${cumulErrorRates}")
        }
        println()
    }
}
/*
phantomPct = 0.0
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0000, 0.0000, 0.0000, 0.00000
fuzzPct 0.001: 0.0001, 0.0003, 0.0003, 0.0001, 0.00082
fuzzPct 0.005: 0.0006, 0.0012, 0.0016, 0.0007, 0.00412
fuzzPct 0.010: 0.0011, 0.0024, 0.0033, 0.0015, 0.00823
fuzzPct 0.020: 0.0022, 0.0049, 0.0067, 0.0029, 0.01669
fuzzPct 0.050: 0.0056, 0.0121, 0.0166, 0.0071, 0.04133

phantomPct = 0.01
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0099, 0.0000, 0.0000, 0.00990
fuzzPct 0.001: 0.0001, 0.0102, 0.0004, 0.0001, 0.01081
fuzzPct 0.005: 0.0003, 0.0112, 0.0019, 0.0006, 0.01395
fuzzPct 0.010: 0.0007, 0.0124, 0.0037, 0.0011, 0.01786
fuzzPct 0.020: 0.0014, 0.0149, 0.0075, 0.0023, 0.02596
fuzzPct 0.050: 0.0035, 0.0223, 0.0187, 0.0057, 0.05019

phantomPct = 0.02
                 p2o,    p1o,    p1u,    p2u,    sum,
fuzzPct 0.000: 0.0000, 0.0196, 0.0000, 0.0000, 0.01961
fuzzPct 0.001: 0.0001, 0.0199, 0.0004, 0.0001, 0.02044
fuzzPct 0.005: 0.0005, 0.0209, 0.0017, 0.0007, 0.02383
fuzzPct 0.010: 0.0010, 0.0221, 0.0034, 0.0014, 0.02786
fuzzPct 0.020: 0.0020, 0.0246, 0.0069, 0.0027, 0.03629
fuzzPct 0.050: 0.0050, 0.0315, 0.0167, 0.0067, 0.05992
 */
