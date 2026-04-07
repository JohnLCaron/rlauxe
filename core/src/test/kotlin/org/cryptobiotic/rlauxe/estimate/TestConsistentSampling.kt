package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import org.cryptobiotic.rlauxe.workflow.*

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestConsistentSampling {

    @Test
    fun testConsistentClcaSampling() {
        val test = MultiContestTestData(20, 11, 20000, marginRange= .001.rangeTo(.05))
        val contestsUAs: List<ContestWithAssertions> = test.contests.map {
            ContestWithAssertions(it, isClca = true).addStandardAssertions()
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, Random.nextLong())

        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        contestRounds.forEach { it.estMvrs = it.Npop / 11 } // random

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())

        //// main side effects:
        ////    auditRound.nmvrs = sampledCards.size
        ////    auditRound.newmvrs = newMvrs
        ////    auditRound.samplePrns = sampledCards.map { it.prn }
        ////    contestRound.maxSampleAllowed = sampledCards.size
        consistentSampling(auditRound, mvrManager.sortedManifest())
        println("nsamples needed = ${auditRound.samplePrns.size}\n")
        assertEquals(auditRound.samplePrns.size, auditRound.nmvrs)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach {
            require(it > lastRN)
            lastRN = it
        }

        contestRounds.forEach { contestRound ->
            val contest = contestRound.contestUA
            print(" ${contest.name} (${contest.id}) estMvrs=${contestRound.estMvrs} pct=${contestRound.estMvrs/contest.Npop.toDouble()}")
            val assorter = contestRound.minAssertion()!!.assertion.assorter
            println(" recountMargin=${contest.contest.recountMargin(assorter)} dilutedMargin=${assorter.dilutedMargin()} marginInVotes=${contest.contest.marginInVotes(assorter)}")
        }

        // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        println("contest.name (id) == sampleSize")
        contestRounds.forEach { contest ->
            val cvrs = mvrManager.sortedCards.filter { it.hasContest(contest.id) }
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
        consistentSampling(auditRound, mvrManager.sortedManifest())
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
            val ballotsForContest = mvrManager.sortedMvrs.count { it.hasContest(contest.id) }
            assertTrue(contest.estMvrs <= ballotsForContest)
        }
    }

    @Test
    fun testSampleAndRemoveContests() {
        val sampleControl = ContestSampleControl(minRecountMargin=.005, minMargin=.002, maxSamplePct=.25, contestSampleCutoff=2000, auditSampleCutoff=3000)
        println(" $sampleControl")

        repeat (11) {
            println("run $it")

            val test = MultiContestTestData(33, 3, 200000, marginRange= .001.rangeTo(.01))
            val contestsUAs: List<ContestWithAssertions> = test.contests.map { ContestWithAssertions(it, isClca = true).addStandardAssertions() }
            val testCvrs = test.makeCvrsFromContests()
            val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, Random.nextLong())

            val results = VerifyResults()
            preAuditContestCheck(contestsUAs, results) // contestUA.preAuditStatus is set
            if (results.hasErrors) println( results.toString() )
            val countCheckRemoved = contestsUAs.count { it.preAuditStatus != TestH0Status.InProgress }
            println(" checkContestsCorrectlyFormed removed ${countCheckRemoved} / ${contestsUAs.size}")

            val contestRounds = contestsUAs.filter { it.preAuditStatus == TestH0Status.InProgress }
                .map{ contest -> ContestRound(contest, 1) }
            val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())

            // side effects:
            //   contestRound.estMvrs = estMvrs
            //   contestRound.estNewMvrs = newMvrs
            //
            //   useAssertionRound.estMvrs = estMvrs
            //   useAssertionRound.estNewMvrs = newMvrs
            //   assertionRound.estimationResult = estimationResult
            val estimate = EstimateAudit(
                mvrManager.auditdir(),
                Config.from(AuditType.CLCA),
                auditRound.roundIdx,
                auditRound.contestRounds,
                mvrManager.pools(),
                mvrManager.batches(),
                mvrManager.sortedManifest()
            )
            estimate.run(nthreads=null, contestOnly=null)

            /* contestRounds.forEach { contestRound ->
                val contest = contestRound.contestUA
                print(" ${contest.name} (${contest.id}) estMvrs=${contestRound.estMvrs} pct=${contestRound.estMvrs/contest.Npop.toDouble()}")
                val assorter = contestRound.minAssertion()!!.assertion.assorter
                println(" recountMargin=${contest.contest.recountMargin(assorter)} dilutedMargin=${assorter.dilutedMargin()} marginInVotes=${contest.contest.marginInVotes(assorter)}")
            } */

            //// side effects:
            //    auditRound.nmvrs = sampledCards.size
            //    auditRound.newmvrs = newMvrs
            //    auditRound.samplePrns = sampledCards.map { it.prn }
            //    contestRound.maxSampleAllowed = sampledCards.size

            //    contestRound.status = TestH0Status.FailMaxSamplesAllowed
            //    contestRound.included = false
            //    contestRound.done = true
            removeContestsAndSample(
                sampleControl,
                mvrManager.sortedManifest(),
                auditRound,
                emptySet(),
            )

            var countRemoved = 0
            contestRounds.forEach { contestRound ->
                val contest = contestRound.contestUA

                if (contestRound.status != TestH0Status.InProgress) {
                    // println("  ${contestRound.name} (${contestRound.id}) estMvrs=${contestRound.estMvrs} status=${contestRound.status}")
                    countRemoved++

                } else {
                    val estMvrs=contestRound.estMvrs
                    val samplePct=contestRound.estMvrs/contest.Npop.toDouble()
                    val assorter = contestRound.minAssertion()!!.assertion.assorter
                    val recountMargin=contest.contest.recountMargin(assorter)
                    val dilutedMargin=assorter.dilutedMargin()
                    val marginInVotes= contest.contest.marginInVotes(assorter)

                    assertTrue(contest.Nphantoms < marginInVotes)
                    assertTrue(recountMargin > sampleControl.minRecountMargin)
                    assertTrue(dilutedMargin > sampleControl.minMargin,
                        " ${contestRound.name} (${contestRound.id}) estMvrs=${contestRound.estMvrs} dilutedMargin $dilutedMargin <= ${sampleControl.minMargin}")
                    assertTrue(samplePct < sampleControl.maxSamplePct)
                    assertTrue(estMvrs < (sampleControl.contestSampleCutoff ?: Int.MAX_VALUE))
                    assertTrue(auditRound.nmvrs < (sampleControl.auditSampleCutoff ?: Int.MAX_VALUE))
                }

            }
            println(" sampleAndRemoveContests removed=$countRemoved / ${contestRounds.size} ; auditRound.nmvrs=${auditRound.nmvrs}")
        }
    }
}