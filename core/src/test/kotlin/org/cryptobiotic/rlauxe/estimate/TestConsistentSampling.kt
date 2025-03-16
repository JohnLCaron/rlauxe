package org.cryptobiotic.rlauxe.estimate

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
        val ballotCards = BallotCardsClcaStart(testCvrs, testCvrs, Random.nextLong())

        contestsUAs.forEach { it.makeClcaAssertions(testCvrs) }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estSampleSize = it.Nc / 11 } // random

        val prng = Prng(Random.nextLong())
        val cvrsUAP = test.makeCvrsFromContests().mapIndexed { idx, it -> CvrUnderAudit( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, sampleNumbers = emptyList(), sampledBorc = emptyList())
        // fun consistentSampling(
        //    auditRound: AuditRound,
        //    ballotCards: BallotCards,
        //    previousSamples: Set<Int> = emptySet(),
        consistentSampling(auditRound, ballotCards)
        println("nsamples needed = ${auditRound.sampleNumbers.size}\n")
        assertEquals(auditRound.sampleNumbers.size, auditRound.nmvrs)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        // must be ordered
        var lastRN = 0L
        auditRound.sampleNumbers.forEach { it ->
            require(it > lastRN)
            lastRN = it
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
        val ballotCards = BallotCardsPollingStart(ballotManifest.ballots, test.makeCvrsFromContests(), Random.nextLong())

        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, sampleNumbers = emptyList(), sampledBorc = emptyList())
        consistentSampling(auditRound, ballotCards)
        println("nsamples needed = ${auditRound.sampleNumbers.size}\n")

        // must be ordered
        var lastRN = 0L
        auditRound.sampleNumbers.forEach { it ->
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }
        // double check the number of cvrs == sampleSize
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val ballotsForContest = ballotsUA.filter {
                it.ballot.hasContest(contest.id)
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

        val ballotManifest = test.makeBallotManifest(false)
        val ballotCards = BallotCardsPollingStart(ballotManifest.ballots, test.makeCvrsFromContests(), Random.nextLong())

        val prng = Prng(Random.nextLong())
        val ballotsUA = ballotManifest.ballots.mapIndexed { idx, it -> BallotUnderAudit( it, idx, prng.next()) }

        //    contests: List<ContestUnderAudit>,
        //    ballots: List<BallotUnderAudit>, // all the ballots available to sample
        //    samplePctCutoff: Double,
        //    N: Int,
        //    roundIdx: Int,
        val estPctCutoff = .50

        val auditRound = AuditRound(1, contestRounds, sampleNumbers = emptyList(), sampledBorc = emptyList())
        val sampleIndices = uniformSampling(auditRound, ballotCards, 0, estPctCutoff, 0)
        println("nsamples needed = ${auditRound.sampleNumbers.size}\n")

        // must be ordered
        var lastRN = 0L
        auditRound.sampleNumbers.forEach { it ->
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estSampleSize}")
        }

        // double check the number of cvrs == sampleSize
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            assertTrue(contest.estSampleSize <= auditRound.sampleNumbers.size)
            assertTrue(contest.done || contest.estSampleSizeNoStyles <= auditRound.sampleNumbers.size)

            val estPct = contest.estSampleSize / contest.Nc.toDouble()
            println("contest ${contest.id} estPct=$estPct done=${contest.done}")
            if (estPct > estPctCutoff)
                assertTrue(contest.done)
        }
    }
}