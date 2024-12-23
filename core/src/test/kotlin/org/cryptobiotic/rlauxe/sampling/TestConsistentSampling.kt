package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.makeNcvrsPerContest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestConsistentSampling {

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
            ContestUnderAudit( it, cvrs)
        }
        contestsUA[0].estSampleSize = 3
        contestsUA[1].estSampleSize = 4

        val sample_cvr_indices = consistentCvrSampling(contestsUA, cvrsUA)
        assertEquals(5, sample_cvr_indices.size)

        assertEquals(listOf(3, 2, 1, 5, 0), sample_cvr_indices)
        //assertEquals(3769430703478411547, contestsUA[0].sampleThreshold)
        //assertEquals(6830268459859750345, contestsUA[1].sampleThreshold)
        //assertEquals(0, contestsUA[2].sampleThreshold)
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



        val contests =  makeContestsFromCvrs(cvrs)
        val contestsUA = contests.mapIndexed { idx, it -> ContestUnderAudit( it) }
        contestsUA[0].estSampleSize = 3
        contestsUA[1].estSampleSize = 3
        contestsUA[2].estSampleSize = 2

        val ncvrs = makeNcvrsPerContest(contests, cvrs)
        val phantomCVRs = makePhantomCvrs(contests, ncvrs)

        val prng = Prng(123456789012L)
        val cvrsUAP = (cvrs + phantomCVRs).map { CvrUnderAudit( it, prng.next()) }
        assertEquals(9, cvrsUAP.size)

        val sample_cvr_indices = consistentCvrSampling(contestsUA, cvrsUAP)
        assertEquals(6, sample_cvr_indices.size)
        assertEquals(listOf(7, 2, 8, 3, 5, 1), sample_cvr_indices)
    }

    // the cvrs include all the contests, and always have a vote in that contest
    @Test
    fun testSamplingNoSkip() {
        TestSamplingWithSkip(0).runTest()
    }

    // the cvrs include all the contests, but dont always have a vote in that contest
    @Test
    fun testSamplingSkipSome() {
        TestSamplingWithSkip(1).runTest()
    }

    class TestSamplingWithSkip(val skipSomeContests: Int) {

        fun runTest() {
            val test = MultiContestTestData(20, 11, 20000)
            val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
            contestsUA.forEach { it.estSampleSize = it.Nc / 11 } // random

            val prng = Prng(secureRandom.nextLong())
            val cvrsUAP = test.makeCvrsFromContests().map { CvrUnderAudit( it, prng.next()) }

            val sample_cvr_indices = consistentCvrSampling(contestsUA, cvrsUAP)
            println("nsamples = ${sample_cvr_indices.size}\n")
            contestsUA.forEach { contest ->
                println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
            }

            // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
            println("contest.name (id) == sampleSize")
            contestsUA.forEach { contest ->
                val cvrs = cvrsUAP.filter { it.hasContest(contest.id)}
                var count = 0
                cvrs.forEachIndexed { idx, it ->
                    if (it.sampled) count++
                }
                assertTrue(contest.estSampleSize <= cvrs.size)
                assertTrue(contest.estSampleSize <= count)
                // TODO what else can we check ??
            }
        }
    }
}