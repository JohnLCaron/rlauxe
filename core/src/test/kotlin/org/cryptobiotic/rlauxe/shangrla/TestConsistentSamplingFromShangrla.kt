package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConsistentSamplingFromShangrla {

    // SHANGRLA test_CVR.py
    @Test
    fun test_consistent_sampling() {
        val contestInfos: List<ContestInfo> = listOf(
            ContestInfo("city_council", 0, candidateNames= listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
                choiceFunction = SocialChoiceFunction.PLURALITY),
            ContestInfo("measure_1", 1, candidateNames= listToMap("yes", "no"),
                SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666),
            ContestInfo("dont_care", 2, candidateNames= listToMap("yes", "no"),
                SocialChoiceFunction.PLURALITY),
        )

        val cvrs = CvrBuilders()
            .addCvr().addContest("city_council", "Alice").done()
            .addContest("measure_1", "yes").done().done()
            .addCvr().addContest("city_council", "Bob").done()
            .addContest("measure_1", "yes").done().done()
            .addCvr().addContest("city_council", "Bob").done()
            .addContest("measure_1", "no").done().done()
            .addCvr().addContest("city_council", "Charlie").done().done()
            .addCvr().addContest("city_council", "Doug").done().done()
            .addCvr().addContest("measure_1", "no").done().done()
            .build()

        val prng = Prng(12345678901L)
        val cvrsUA = cvrs.mapIndexed { idx, it ->
            CvrUnderAudit( it, prng.next()) // here we assign sample number deterministically
        }
        val contestsUA = contestInfos.mapIndexed { idx, it ->
            makeContestUAfromCvrs( it, cvrs)
        }
        contestsUA[0].estMvrs = 3
        contestsUA[1].estMvrs = 4

        val sample_cvr_indices = consistentSampling(contestsUA, cvrsUA)
        assertEquals(5, sample_cvr_indices.size)

        assertEquals(listOf(3, 2, 1, 5, 0), sample_cvr_indices)
    }

    @Test
    fun testSamplingWithPhantoms() {
        val contestInfos: List<ContestInfo> = listOf(
            ContestInfo("city_council", 0, candidateNames= listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
                choiceFunction = SocialChoiceFunction.PLURALITY),
            ContestInfo("measure_1", 1, candidateNames= listToMap("yes", "no"),
                SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666),
            ContestInfo("measure_2", 2, candidateNames= listToMap("yes", "no"),
                SocialChoiceFunction.PLURALITY),
        )

        val cvrs = CvrBuilders()
            .addCvr().addContest("city_council", "Alice").done()
                    .addContest("measure_1", "yes").done().done()
            .addCvr().addContest("city_council", "Bob").done()
                    .addContest("measure_1", "yes").done().done()
            .addCvr().addContest("city_council", "Bob").done()
                    .addContest("measure_1", "no").done().done()
            .addCvr().addContest("city_council", "Charlie").done().done()
            .addCvr().addContest("city_council", "Doug").done().done()
            .addCvr().addContest("measure_1", "no").done().done()
            .addCvr().addContest("measure_2", "no").done().done()
            .addCvr().addContest("measure_2", "no").done().done()
            .addCvr().addContest("measure_2", "yes").done().done()
            .build()

        val contests = makeContestsFromCvrs(cvrs)
        val contestsUA = contests.mapIndexed { idx, it -> ContestUnderAudit( it) }
        contestsUA[0].estMvrs = 3
        contestsUA[1].estMvrs = 3
        contestsUA[2].estMvrs = 2

        val ncvrs = makeNcvrsPerContest(contests, cvrs)
        val phantomCVRs = makePhantomCvrs(contests, ncvrs)

        val prng = Prng(123456789012L)
        val cvrsUAP = (cvrs + phantomCVRs).map { CvrUnderAudit( it, prng.next()) }
        assertEquals(9, cvrsUAP.size)

        val sample_cvr_indices = consistentSampling(contestsUA, cvrsUAP)
        assertEquals(6, sample_cvr_indices.size)
        assertEquals(listOf(7, 2, 8, 3, 5, 1), sample_cvr_indices)
    }
}