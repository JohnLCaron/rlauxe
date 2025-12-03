package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

// Simulate single Contest, do regular audit
class ClcaContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val config: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Nb: Int = Nc,
    val nsimEst: Int = 100,
    val p2flips: Double? = null,
): ContestAuditTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val useConfig = config ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs =  if (p2flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, 0.0) else
            makeFuzzedCvrsFrom(listOf(sim.contest.info()), testCvrs, mvrsFuzzPct)

        if (!useConfig.hasStyle && Nb > Nc) { // TODO wtf?
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeUndervoteForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clcaWorkflow = WorkflowTesterClca(useConfig, listOf(sim.contest), emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed))

        return ContestAuditTask(
            name(),
            clcaWorkflow,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

// Simulate single Contest with ContestSimulation.make2wayTestContest
class ClcaSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val config: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundAuditTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val useConfig = config ?:
        AuditConfig(
            AuditType.CLCA, true,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(sim.contest.info()), testCvrs, mvrsFuzzPct)
        }

        // TODO not adding the Nbs...
        val clcaWorkflow = WorkflowTesterClca(useConfig, listOf(sim.contest), emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed))

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

        return ClcaSingleRoundWorkflowTask(
            name(),
            clcaWorkflow,
            auditor = ClcaAssertionAuditor(),
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0),
            quiet,
        )
    }
}

// AuditWorkflow is given, audit one contest in a single round
class ClcaSingleRoundWorkflowTask(
    val name: String,
    val workflow: AuditWorkflow,
    val auditor: ClcaAssertionAuditorIF, // can be used for both Clca and OneAudit
    val testMvrs: List<Cvr>, // needed for tracking the true margin of the mvrs, for plotting
    val otherParameters: Map<String, Any> = emptyMap(),
    val quiet: Boolean = true,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name

    override fun run(): WorkflowResult {
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        val nmvrs = runClcaSingleRoundAudit(workflow, contestRounds, quiet = quiet, auditor)

        val contest = contestRounds.first()
        val minAssertion = contest.minAssertion()!!
        val assorter = minAssertion.assertion.assorter
        val mvrMargin = assorter.calcAssorterMargin(contest.id, testMvrs, usePhantoms = true)

        return if (minAssertion.auditResult == null) { // TODO why might this be empty?
            WorkflowResult(
                name,
                contest.Nb,
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
                contest.Nb,
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
fun runClcaSingleRoundAudit(workflow: AuditWorkflow, contestRounds: List<ContestRound>, quiet: Boolean = true,
                            auditor: ClcaAssertionAuditorIF
): Int {
    val stopwatch = Stopwatch()
    runClcaAuditRound(workflow.auditConfig(), contestRounds, workflow.mvrManager(), 1, auditor = auditor)
    if (!quiet) println("runClcaSingleRoundAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")

    var maxSamples = 0
    contestRounds.forEach { contest->
        contest.assertionRounds.forEach { assertion ->
            maxSamples = max( maxSamples, assertion.estSampleSize)
        }
    }
    return maxSamples
}


