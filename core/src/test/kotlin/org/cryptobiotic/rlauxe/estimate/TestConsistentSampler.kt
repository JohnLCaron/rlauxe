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

class TestConsistentSampler {

    @Test
    fun testConsistentClcaSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestWithAssertions> = test.contests.map {
            ContestWithAssertions(it, isClca = true).addStandardAssertions()
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, Random.nextLong())

        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estMvrs = it.Npop / 11 } // random

        val prng = Prng(Random.nextLong())
        val cards = testCvrs.mapIndexed { idx, it -> AuditableCard.fromCvr( it, idx, prng.next()) }

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())
        // fun consistentSampling(
        //    auditRound: AuditRound,
        //    ballotCards: BallotCards,
        //    previousSamples: Set<Int> = emptySet(),
        consistentSampling(auditRound, mvrManager.cardManifest())
        println("nsamples needed = ${auditRound.samplePrns.size}\n")
        assertEquals(auditRound.samplePrns.size, auditRound.nmvrs)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach {
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estMvrs}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val cvrs = cards.filter { it.hasContest(contest.id) }
            assertTrue(contest.estMvrs <= cvrs.size)
            // TODO what else can we check ??
        }
    }

    @Test
    fun testConsistentPollingSampling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestWithAssertions> = test.contests.map { ContestWithAssertions(it, isClca = false).addStandardAssertions() }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estMvrs = it.Npop / 11 } // random

        val cvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerForTesting(cvrs, cvrs, Random.nextLong())

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())
        consistentSampling(auditRound, mvrManager.cardManifest())
        println("nsamples needed = ${auditRound.samplePrns.size}\n")

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach {
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contest ->
            println(" ${contest.name} (${contest.id}) estSampleSize=${contest.estMvrs}")
        }
        // double check the number of cvrs == sampleSize
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val ballotsForContest = mvrManager.mvrsUA.count { it.hasContest(contest.id) }
            assertTrue(contest.estMvrs <= ballotsForContest)
        }
    }
}