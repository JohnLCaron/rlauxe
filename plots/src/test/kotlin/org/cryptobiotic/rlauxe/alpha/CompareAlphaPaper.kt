package org.cryptobiotic.rlauxe.alpha


import org.cryptobiotic.rlauxe.comparison.runAlphaMartRepeated
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.sampling.ComparisonNoErrors
import org.cryptobiotic.rlauxe.sampling.PollWithoutReplacement
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.workflow.RunTestRepeatedResult
import kotlin.test.Test

// compare ballot polling to card comparison
class CompareAlphaPaper {

    // using values from alpha.ipynb, compare polling and comparison audit
    @Test
    fun plotCmpareAlphaPaper() {
        val d = 100
        val N = 10000
        val reps = 100

        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val etas = listOf(0.9, 1.0, 1.5, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0) // should be .9, 1, 1.009, 2, 2.018

        val info = ContestInfo("contest0", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)

        val pollingSrs = mutableListOf<SRT>()
        val compareSrs = mutableListOf<SRT>()
        for (theta in thetas) {
            val cvrs = makeCvrsByExactMean(N, theta)
            val contest = makeContestFromCvrs(info, cvrs)
            val contestUA = ContestUnderAudit(contest, cvrs.size, isComparison = false)

            contestUA.makePollingAssertions()
            val pollingAssertion = contestUA.pollingAssertions.first()

            contestUA.makeComparisonAssertions(cvrs)
            val compareAssertion = contestUA.comparisonAssertions.first()

            for (eta in etas) {
                val compareResult: RunTestRepeatedResult = runAlphaMartRepeated(
                    drawSample = ComparisonNoErrors(cvrs, compareAssertion.cassorter),
                    maxSamples = N,
                    eta0 = eta,
                    d = d,
                    ntrials = reps,
                    upperBound = compareAssertion.cassorter.upperBound,
                )
                compareSrs.add(compareResult.makeSRT(theta, 0.0))

                val pollingResult = runAlphaMartRepeated(
                    drawSample = PollWithoutReplacement(contestUA, cvrs, pollingAssertion.assorter),
                    maxSamples = N,
                    eta0 = eta, // use the reportedMean for the initial guess
                    d = d,
                    ntrials = reps,
                    withoutReplacement = true,
                    upperBound = pollingAssertion.assorter.upperBound()
                )
                pollingSrs.add(pollingResult.makeSRT(theta, 0.0))
            }
        }

        val ctitle = " nsamples, ballot comparison, N=$N, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(
            compareSrs, ctitle, true, colf = "%6.3f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples }
        )

        val ptitle = " nsamples, ballot polling, N=$N, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(
            pollingSrs, ptitle, true, colf = "%6.3f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples }
        )
    }
    //  nsamples, ballot comparison, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9955,   9766,   9336,   8571,   7461,   2257,    464,    221,    140,    101,     59,     41,     25,     18,
    // 1.000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
    // 1.500,   9916,   9014,   5954,   3189,   1827,    418,    153,     98,     74,     59,     39,     29,     19,     14,
    // 2.000,   9825,   6722,   2923,   1498,    937,    309,    148,     98,     74,     59,     39,     29,     19,     14,
    // 5.000,   5173,   1620,    962,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 7.500,   3310,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //10.000,   2765,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //15.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //20.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //
    // nsamples, ballot polling, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9683,   9762,   9641,   9615,   9476,   9043,   7995,   6446,   4840,   3759,   1764,    852,    242,     84,
    // 1.000,   9683,   9562,   9843,   9723,   9408,   9585,   8691,   7847,   7313,   6191,   3988,   2783,   1293,    589,
    // 1.500,   9483,   9562,   9246,   9426,   9307,   9414,   9234,   8778,   8521,   9001,   8350,   7672,   6309,   6069,
    // 2.000,   9782,   9664,   9644,   9525,   9903,   9510,   9040,   8873,   8893,   8725,   8175,   7586,   6545,   6217,
    // 5.000,   9682,   9663,   9247,   9524,   9607,   9803,   9137,   9343,   8890,   8548,   8092,   7752,   6695,   5934,
    // 7.500,   9782,   9763,   9544,   9426,   9803,   9609,   9427,   9150,   8701,   8547,   8438,   7417,   6537,   5717,
    //10.000,   9683,   9564,   9742,   9624,   9706,   9512,   9039,   8962,   8890,   8908,   8352,   7589,   7080,   5717,
    //15.000,   9284,   9863,   9941,   9526,   9604,   9512,   9234,   9060,   8704,   8547,   7997,   7748,   6617,   6146,
    //20.000,   9582,   9862,   9445,   9922,   9408,   9513,   9329,   8965,   8799,   9002,   8437,   7421,   6927,   6360,
}