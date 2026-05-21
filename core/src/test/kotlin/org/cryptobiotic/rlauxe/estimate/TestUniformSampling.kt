package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.estRiskFromMargin
import org.cryptobiotic.rlauxe.util.estSamplesFromMarginUpper
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestUniformSampling {

    @Test
    fun testUniformSampling() {
        val test = MultiContestTestData(20, 11, 20000, marginRange= .001.rangeTo(.05))
        val contestsUAs: List<ContestWithAssertions> = test.contests.map {
            ContestWithAssertions(it, isClca = true, hasStyle = false).addStandardAssertions()
        }
        val testCvrs = test.makeCvrsFromContests()
        val mvrManager = MvrManagerForTesting(testCvrs, testCvrs, Random.nextLong())

        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }
        // contestRounds.forEach { it.estMvrs = it.Npop / 11 } // random

        val auditRound = AuditRound(1, contestRounds, samplePrns = emptyList())
        auditRound.auditorWantNewMvrs = 1111

        //// main side effects:
        ////    auditRound.nmvrs = sampledCards.size
        ////    auditRound.newmvrs = newMvrs
        ////    auditRound.samplePrns = sampledCards.map { it.prn }
        ////    contestRound.maxSampleAllowed = sampledCards.size
        uniformSampling(auditRound, mvrManager.samplingCards())

        println("uniformSampling samples needed = ${auditRound.samplePrns.size}\n")
        assertEquals(auditRound.samplePrns.size, auditRound.nmvrs)
        assertEquals(auditRound.nmvrs, auditRound.newmvrs)

        // must be ordered
        var lastRN = 0L
        auditRound.samplePrns.forEach {
            require(it > lastRN)
            lastRN = it
        }

        println("           name    id, estNeed,  have,  estRisk")
        contestRounds.forEach { contestRound ->
            val contestUA = contestRound.contestUA
            val margin = contestUA.minMargin()
            val bet = 2.0 / 1.03905
            val estSamples = if (margin == null) 0 else
                roundUp(estSamplesFromMarginUpper(bet, margin, .05))
            val estRisk = if (margin == null) 1.0 else
                estRiskFromMargin(2.0 / 1.03905, margin, contestRound.haveSampleSize)
            val nameId = "${contestUA.name} (${contestUA.id})"
            println(" ${trunc(nameId, 20)}, ${nfn(estSamples, 6)}, ${nfn(contestRound.haveSampleSize, 6)},  ${dfn(estRisk, 4)}")
            val assorter = contestRound.minAssertion()!!.assertion.assorter
            // println(" recountMargin=${contestUA.contest.recountMargin(assorter)} margin=${assorter.margin(contestUA.hasStyle)} marginInVotes=${contestUA.contest.marginInVotes(assorter)}")
        }

        /*ouble check the number of cvrs == sampleSize, and the cvrs are marked as sampled
        contestRounds.forEach { contest ->
            val cvrs = mvrManager.sortedCards.filter { it.hasContest(contest.id) }
            assertTrue(contest.estMvrs <= cvrs.size)
            // TODO what else can we check ??
        } */
    }

    @Test
    fun testRemoveContestsAndSample() {
        val sampleControl = ContestSampleControl(minRecountMargin=.005, minMargin=.002, maxSamplePct=.25, contestSampleCutoff=2000, auditSampleCutoff=3000,
            sampling = Sampling.uniform)
        println(" $sampleControl")

        repeat (2) {
            println("run $it")

            val test = MultiContestTestData(33, 3, 200000, marginRange= .001.rangeTo(.01))
            val contestsUAs: List<ContestWithAssertions> = test.contests.map { ContestWithAssertions(it, isClca = true, hasStyle = false).addStandardAssertions() }
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

            // TODO hoe does uiform sampling affect EstimateAudit ??
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
                mvrManager.styles(),
                mvrManager.sortedManifest()
            )
            estimate.run(nthreads=null, contestOnly=null)

            /* contestRounds.forEach { contestRound ->
                val contest = contestRound.contestUA
                print(" ${contest.name} (${contest.id}) estMvrs=${contestRound.estMvrs} pct=${contestRound.estMvrs/contest.Npop.toDouble()}")
                val assorter = contestRound.minAssertion()!!.assertion.assorter
                println(" recountMargin=${contest.contest.recountMargin(assorter)} dilutedMargin=${assorter.margin(contestUA.hasStyle)} marginInVotes=${contest.contest.marginInVotes(assorter)}")
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
                mvrManager.samplingCards(),
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
                    val dilutedMargin=assorter.margin(contestRound.contestUA.hasStyle)
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