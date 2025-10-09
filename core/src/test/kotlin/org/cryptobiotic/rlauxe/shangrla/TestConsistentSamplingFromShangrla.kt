package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.workflow.MvrManagerClcaForTesting
import org.cryptobiotic.rlauxe.audit.ContestRound
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
        var cards = cvrs.mapIndexed { idx, it ->
            AuditableCard.fromCvr( it, idx, prng.next()) // here we assign sample number deterministically
        }
        cards = cards.sortedBy { it.prn }

        val contestsUA = contestInfos.mapIndexed { idx, it ->
            makeContestUAfromCvrs( it, cvrs)
        }
        val contestRounds = contestsUA.map{ contest -> ContestRound(contest, 1) }
        contestRounds[0].estSampleSize = 3
        contestRounds[1].estSampleSize = 4

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())

        val ballotCards = MvrManagerClcaForTesting(cvrs, cvrs, 12345678901L)
        consistentSampling(auditRound, ballotCards)
        assertEquals(3, auditRound.samplePrns.size)  // TODO was 5

        // assertEquals(listOf(3, 2, 1, 5, 0), auditRound.sampleNumbers)
    }

    @Test
    fun testSamplingWithPhantoms() {
        val infos: List<ContestInfo> = listOf(
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
        val contestRounds = contestsUA.map{ contest -> ContestRound(contest, 1) }
        contestRounds[0].estSampleSize = 3
        contestRounds[1].estSampleSize = 3
        contestRounds[2].estSampleSize = 2

        // val ncvrs = makeNcvrsPerContest(contests, cvrs)
        val phantomCVRs = makePhantomCvrs(contests)

        val prng = Prng(123456789012L)
        var cards = (cvrs + phantomCVRs).mapIndexed { idx, it -> AuditableCard.fromCvr( it, idx, prng.next()) }
        assertEquals(9, cards.size)

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        val ballotCards = MvrManagerClcaForTesting(cvrs, cvrs, 123456789012L)
        consistentSampling(auditRound, ballotCards)
        assertEquals(6, auditRound.samplePrns.size)
        // assertEquals(listOf(7, 2, 8, 3, 5, 1), auditRound.sampleNumbers)
    }
}