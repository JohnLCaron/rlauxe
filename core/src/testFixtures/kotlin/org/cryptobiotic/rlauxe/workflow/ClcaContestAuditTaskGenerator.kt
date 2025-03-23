package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ClcaContestAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Nb: Int = Nc,
    val nsimEst: Int = 100,
    val p2flips: Double? = null,
): ContestAuditTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val useConfig = auditConfig ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs =  if (p2flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, 0.0) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        if (!useConfig.hasStyles && Nb > Nc) { // TODO wtf?
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeUndervoteForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clcaWorkflow = ClcaAudit(useConfig, listOf(sim.contest), emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, useConfig.seed), testCvrs)

        return ContestAuditTask(
            name(),
            clcaWorkflow,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

// Do the audit in a single round, dont use estimateSampleSizes
class ClcaSingleRoundAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val nsimEst: Int = 100,
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val useConfig = auditConfig ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        }

        val clcaWorkflow = ClcaAudit(useConfig, listOf(sim.contest), emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, useConfig.seed), testCvrs)

        /* make sure margins are below 0
        if (p2flips != null || p1flips != null) {
            val contestUA = clcaWorkflow.contestsUA().first() //  theres only one
            val minAssertion = contestUA.minClcaAssertion()!!
            val assorter = minAssertion.assorter
            val mvrMargin = assorter.calcAssorterMargin(contestUA.id, testMvrs, usePhantoms = true)
            if (mvrMargin >= 0.0) {
                println("ERROR: mvrMargin = $mvrMargin >= 0")
            }
        } */

        return ClcaSingleRoundAuditTask(
            name(),
            clcaWorkflow,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0),
            quiet,
            auditor = AuditClcaAssertion(),
        )
    }
}

class ClcaSingleRoundAuditTask(
    val name: String,
    val workflow: RlauxAuditIF,
    val testMvrs: List<Cvr>,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
    val auditor: ClcaAssertionAuditor,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name

    override fun run(): WorkflowResult {
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        val nmvrs = runClcaSingleRoundAudit(workflow, contestRounds, quiet = quiet, auditor)

        val contest = contestRounds.first() // theres only one
        val minAssertion = contest.minAssertion()!!
        val assorter = minAssertion.assertion.assorter
        val mvrMargin = assorter.calcAssorterMargin(contest.id, testMvrs, usePhantoms = true) // TODO needed or debugging?

        return if (minAssertion.auditResult == null) { // TODO why might this this empty?
            WorkflowResult(
                name,
                contest.Nc,
                assorter.reportedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRound = minAssertion.auditResult!!
            WorkflowResult(
                name,
                contest.Nc,
                assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0,
                mvrMargin=mvrMargin,
            )
        }
    }
}

// keep this seperate function for testing
fun runClcaSingleRoundAudit(workflow: RlauxAuditIF, contestRounds: List<ContestRound>, quiet: Boolean = true,
                            auditor: ClcaAssertionAuditor
): Int {
    val stopwatch = Stopwatch()
    runClcaAudit(workflow.auditConfig(), contestRounds, workflow.mvrManager() as MvrManagerClca, 1, auditor = auditor)
    if (!quiet) println("runClcaSingleRoundAudittook ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")

    var maxSamples = 0
    contestRounds.forEach { contest->
        contest.assertionRounds.forEach { assertion ->
            maxSamples = max( maxSamples, assertion.estSampleSize)
        }
    }
    return maxSamples
}


