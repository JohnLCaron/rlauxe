package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestConsistentSampling {

    @Test
    fun testConsistentClcaSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map {
            ContestUnderAudit(it, isComparison = true)
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerClcaForTesting(testCvrs, testCvrs, Random.nextLong())

        contestsUAs.forEach { it.makeClcaAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Nc / 11 } // random

        val prng = Prng(Random.nextLong())
        val cards = testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        // fun consistentSampling(
        //    auditRound: AuditRound,
        //    ballotCards: BallotCards,
        //    previousSamples: Set<Int> = emptySet(),
        consistentSampling(auditRound, mvrManager)
        println("nsamples needed = ${auditRound.samplePrns.size}\n")
        assertEquals(auditRound.samplePrns.size, auditRound.nmvrs)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach { it ->
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val cvrs = cards.filter { it.hasContest(contest.id) }
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

        val ballotManifest = test.makeCardLocationManifest(true)
        val mvrManager = MvrManagerPollingForTesting(ballotManifest.cardLocations, test.makeCvrsFromContests(), Random.nextLong())

        //val prng = Prng(Random.nextLong())
        //val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        consistentSampling(auditRound, mvrManager)
        println("nsamples needed = ${auditRound.samplePrns.size}\n")

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach { it ->
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }
        // double check the number of cvrs == sampleSize
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val ballotsForContest = mvrManager.mvrsUA.filter {
                it.hasContest(contest.id)
            }.count()
            assertTrue(contest.estSampleSize <= ballotsForContest)
        }
    }

    @Test
    fun testUniformPollingSampling() {
        val N = 20000
        val test = MultiContestTestData(20, 11, N)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false).makePollingAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = 100 + Random.nextInt(it.Nc/2) }

        val ballotManifest = test.makeCardLocationManifest(false)
        val mvrManager = MvrManagerPollingForTesting(ballotManifest.cardLocations, test.makeCvrsFromContests(), Random.nextLong())

        //val prng = Prng(Random.nextLong())
        //val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }
        val sampleLimit = 10000

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        uniformSampling(auditRound, mvrManager, previousSamples=emptySet(), sampleLimit=sampleLimit, 0)
        println("nsamples needed = ${auditRound.samplePrns.size}\n")

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach { it ->
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            assertTrue(contest.estSampleSizeEligibleForRemoval() <= auditRound.samplePrns.size)
            assertTrue(contest.done || contest.estSampleSizeNoStyles <= auditRound.samplePrns.size)

            println("contest ${contest.id} estSampleSize=${contest.estSampleSizeEligibleForRemoval()} done=${contest.done}")
            if (contest.estSampleSizeEligibleForRemoval() > sampleLimit)
                assertTrue(contest.done)
        }
    }
}