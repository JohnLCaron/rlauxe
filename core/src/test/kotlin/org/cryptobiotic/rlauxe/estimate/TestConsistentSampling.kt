package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.AuditRound
import org.cryptobiotic.rlauxe.workflow.BallotUnderAudit
import org.cryptobiotic.rlauxe.workflow.ContestRound

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestConsistentSampling {

    @Test
    fun testConsistentClcaSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map {
            ContestUnderAudit(it, isComparison = false)
        }
        contestsUAs.forEach { it.makePollingAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Nc / 11 } // random

        val prng = Prng(Random.nextLong())
        val cvrsUAP = test.makeCvrsFromContests().mapIndexed { idx, it -> CvrUnderAudit( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, sampledIndices = emptyList())
        val sampleIndices = consistentSampling(auditRound, cvrsUAP)
        println("nsamples needed = ${sampleIndices.size}\n")
        assertEquals(sampleIndices.size, auditRound.nmvrs)
        assertEquals(sampleIndices, auditRound.sampledIndices)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        sampleIndices.forEach {
            assertTrue(it < cvrsUAP.size)
        }
        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val cvrs = cvrsUAP.filter { it.hasContest(contest.id) }
            assertTrue(contest.estSampleSize <= cvrs.size)
            // TODO what else can we check ??
        }
    }

    @Test
    fun testConsistentPollingSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Nc / 11 } // random

        val ballotManifest = test.makeBallotManifest(true)

        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, sampledIndices = emptyList())
        val sampleIndices = consistentSampling(auditRound, ballotsUA)
        println("nsamples needed = ${sampleIndices.size}\n")
        sampleIndices.forEach {
            assertTrue(it < ballotsUA.size)
        }
        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val ballotsForContest = ballotsUA.filter {
                it.ballot.hasContest(contest.id)
            }
            assertTrue(contest.estSampleSize <= ballotsForContest.size)
        }
    }

    @Test
    fun testUniformPollingSampling() {
        val N = 20000
        val test = MultiContestTestData(20, 11, N)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = 100 + Random.nextInt(it.Nc/2) }

        val ballotManifest = test.makeBallotManifest(false)
        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }

        //    contests: List<ContestUnderAudit>,
        //    ballots: List<BallotUnderAudit>, // all the ballots available to sample
        //    samplePctCutoff: Double,
        //    N: Int,
        //    roundIdx: Int,
        val estPctCutoff = .50

        val auditRound = AuditRound(1, contestRounds, sampledIndices = emptyList())
        val sampleIndices = uniformSampling(auditRound, ballotsUA, 0, estPctCutoff, 0)
        println("nsamples needed = ${sampleIndices.size}\n")
        sampleIndices.forEach {
            assertTrue(it < ballotsUA.size)
        }
        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            assertTrue(contest.estSampleSize <= sampleIndices.size)
            assertTrue(contest.done || contest.estSampleSizeNoStyles <= sampleIndices.size)

            val estPct = contest.estSampleSize / contest.Nc.toDouble()
            println("contest ${contest.id} estPct=$estPct done=${contest.done}")
            if (estPct > estPctCutoff)
                assertTrue(contest.done)
        }
    }
}