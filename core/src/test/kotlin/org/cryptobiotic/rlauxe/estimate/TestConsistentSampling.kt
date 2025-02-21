package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.BallotUnderAudit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class TestConsistentSampling {

    @Test
    fun testConsistentCvrSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        contestsUA.forEach { it.estSampleSize = it.Nc / 11 } // random

        val prng = Prng(Random.nextLong())
        val cvrsUAP = test.makeCvrsFromContests().map { CvrUnderAudit( it, prng.next()) }

        val sampleIndices = consistentSampling(contestsUA, cvrsUAP)
        println("nsamples needed = ${sampleIndices.size}\n")
        sampleIndices.forEach {
            assertTrue(it < cvrsUAP.size)
        }
        contestsUA.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestsUA.forEach { contest ->
            val cvrs = cvrsUAP.filter { it.hasContest(contest.id) }
            var count = 0
            cvrs.forEachIndexed { idx, it ->
                if (it.sampled) count++
            }
            assertTrue(contest.estSampleSize <= cvrs.size)
            assertTrue(contest.estSampleSize <= count)
            // TODO what else can we check ??
        }
    }

    @Test
    fun testConsistentPollingSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        contestsUA.forEach { it.estSampleSize = it.Nc / 11 } // random

        val ballotManifest = test.makeBallotManifest(true)

        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }

        val sampleIndices = consistentSampling(contestsUA, ballotsUA)
        println("nsamples needed = ${sampleIndices.size}\n")
        sampleIndices.forEach {
            assertTrue(it < ballotsUA.size)
        }
        contestsUA.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestsUA.forEach { contest ->
            val ballotsForContest = ballotsUA.filter {
                it.ballot.hasContest(contest.id)
            }
            var count = 0
            ballotsUA.forEachIndexed { idx, it ->
                if (it.sampled) count++
            }
            assertTrue(contest.estSampleSize <= ballotsForContest.size)
            assertTrue(contest.estSampleSize <= count)
        }
    }

    @Test
    fun testUniformPollingSampling() {
        val N = 20000
        val test = MultiContestTestData(20, 11, N)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        contestsUA.forEach { it.estSampleSize = 100 + Random.nextInt(it.Nc/2) }

        val ballotManifest = test.makeBallotManifest(false)
        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }

        //    contests: List<ContestUnderAudit>,
        //    ballots: List<BallotUnderAudit>, // all the ballots available to sample
        //    samplePctCutoff: Double,
        //    N: Int,
        //    roundIdx: Int,
        val estPctCutoff = .50
        val sampleIndices = uniformSampling(contestsUA, ballotsUA, estPctCutoff, 0)
        println("nsamples needed = ${sampleIndices.size}\n")
        sampleIndices.forEach {
            assertTrue(it < ballotsUA.size)
        }
        contestsUA.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestsUA.forEach { contest ->
            assertTrue(contest.estSampleSize <= sampleIndices.size)
            assertTrue(contest.done || contest.estSampleSizeNoStyles <= sampleIndices.size)

            val estPct = contest.estSampleSize / contest.Nc.toDouble()
            println("contest ${contest.id} estPct=$estPct done=${contest.done}")
            if (estPct > estPctCutoff)
                assertTrue(contest.done)
        }
    }
}