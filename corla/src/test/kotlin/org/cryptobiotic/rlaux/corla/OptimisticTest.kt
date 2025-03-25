package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

class OptimisticTest {
    val riskLimit = .03
    val gamma = 1.03905

    @Test
    fun testOptimistic() {
        // round1/contest.csv
        // ballot card count = 396121
        // contest_ballot_card_count = 44675
        // min_margin = 27538
        // all errors are 0
        // gamma = 1.03905
        // optimistic_samples_to_audit = 105
        // estimated_samples_to_audit = 105

        var dilutedMargin = 27538.0 / 44675.0
        println("dilutedMargin = $dilutedMargin estSamples = ${optimistic(riskLimit, dilutedMargin, gamma)}")

        dilutedMargin = 27538.0 / 396121.0
        println("dilutedMargin = $dilutedMargin estSamples = ${optimistic(riskLimit, dilutedMargin, gamma)}")

        // targetedContests.xlsx
        // Boulder	    State Representative 10	1	23,460	    3,720	    19,740	6.99%	104	    29,261	    2 ballot cards per ballot
        dilutedMargin = .0699
        println("dilutedMargin = $dilutedMargin estSamples = ${optimistic(riskLimit, dilutedMargin, gamma)}")

        // dilutedMargin = 0.6164073866815892 estSamples = 12.0
        // dilutedMargin = 0.0695191620742147 estSamples = 105.0
        // dilutedMargin = 0.0699 estSamples = 105.0
    }

}