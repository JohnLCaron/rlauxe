package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConsistentSampling {

    // SHANGRLA test_CVR.py
    @Test
    fun test_consistent_sampling() {
        val contests: List<Contest> = listOf(
            Contest("city_council", 0, candidateNames= listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
                winnerNames= listOf("Alice"), choiceFunction = SocialChoiceFunction.PLURALITY),
            Contest("measure_1", 1, candidateNames= listToMap("yes", "no"),
                winnerNames= listOf("yes"), SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666),
            Contest("dont_care", 1, candidateNames= listToMap("yes", "no"),
                winnerNames= listOf("yes"), SocialChoiceFunction.PLURALITY),
        )

        val cvrs = CvrBuilders()
            .addCrv().addContest("city_council", "Alice").done()
            .addContest("measure_1", "yes").done().done()
            .addCrv().addContest("city_council", "Bob").done()
            .addContest("measure_1", "yes").done().done()
            .addCrv().addContest("city_council", "Bob").done()
            .addContest("measure_1", "no").done().done()
            .addCrv().addContest("city_council", "Charlie").done().done()
            .addCrv().addContest("city_council", "Doug").done().done()
            .addCrv().addContest("measure_1", "no").done().done()
            .build()

        val prng = Prng(12345678901L)
        val cvrsUA = cvrs.mapIndexed { idx, it ->
            CvrUnderAudit( it as Cvr, false, prng.next()) // here we assign sample number deterministically
        }
        val cards =  cardsPerContest(cvrs)

        val contestsUA = contests.mapIndexed { idx, it ->
            val ncards = cards[it.id]!!
            ContestUnderAudit( it, ncards, ncards)
        }
        contestsUA[0].sampleSize = 3
        contestsUA[1].sampleSize = 4

        val sample_cvr_indices = consistentSampling(contestsUA, cvrsUA)
        assertEquals(5, sample_cvr_indices.size)

        assertEquals(listOf(3, 2, 1, 5, 0), sample_cvr_indices)
        assertEquals(3769430703478411547, contestsUA[0].sampleThreshold)
        assertEquals(6830268459859750345, contestsUA[1].sampleThreshold)
        assertEquals(0, contestsUA[2].sampleThreshold)
    }

    @Test
    fun testSamplingWithPhantoms() {
        val contests: List<Contest> = listOf(
            Contest("city_council", 0, candidateNames= listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
                winnerNames= listOf("Alice"), choiceFunction = SocialChoiceFunction.PLURALITY),
            Contest("measure_1", 1, candidateNames= listToMap("yes", "no"),
                winnerNames= listOf("yes"), SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666),
            Contest("measure_2", 1, candidateNames= listToMap("yes", "no"),
                winnerNames= listOf("no"), SocialChoiceFunction.PLURALITY),
        )

        val cvrs = CvrBuilders()
            .addCrv().addContest("city_council", "Alice").done()
                    .addContest("measure_1", "yes").done().done()
            .addCrv().addContest("city_council", "Bob").done()
                    .addContest("measure_1", "yes").done().done()
            .addCrv().addContest("city_council", "Bob").done()
                    .addContest("measure_1", "no").done().done()
            .addCrv().addContest("city_council", "Charlie").done().done()
            .addCrv().addContest("city_council", "Doug").done().done()
            .addCrv().addContest("measure_1", "no").done().done()
            .addCrv().addContest("measure_2", "no").done().done()
            .addCrv().addContest("measure_2", "no").done().done()
            .addCrv().addContest("measure_2", "yes").done().done()
            .build()

        val prng = Prng(123456789012L)
        val cvrsUA = cvrs.mapIndexed { idx, it ->
            CvrUnderAudit( it as Cvr, false, prng.next())
        }
        val cards =  cardsPerContest(cvrs)

        val contestsUA = contests.mapIndexed { idx, it ->
            val ncards = cards[it.id]!!
            ContestUnderAudit( it, ncards, ncards+2)
        }
        contestsUA[0].sampleSize = 3
        contestsUA[1].sampleSize = 3
        contestsUA[2].sampleSize = 2

        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val cvrsUAP = cvrsUA + phantomCVRs
        assertEquals(11, cvrsUAP.size)

        val sample_cvr_indices = consistentSampling(contestsUA, cvrsUAP)
        assertEquals(4, sample_cvr_indices.size)
        assertEquals(listOf(2, 3, 5, 1), sample_cvr_indices)

        assertEquals(6461562665860220490, contestsUA[0].sampleThreshold)
        assertEquals(6461562665860220490, contestsUA[1].sampleThreshold)
        assertEquals(5277299285729679461, contestsUA[2].sampleThreshold)
    }
}