package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

// Simulate single Contest, do regular audit
class ClcaContestAuditTaskGenerator(
    val name: String,
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val config: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Npop: Int = Nc,
    val nsimEst: Int = 100,
): ContestAuditTaskGenerator {
    override fun name() = name

    override fun generateNewTask(): SingleContestAuditTask {
        val useConfig = config ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        var (cu, testCvrs) = simulateCvrsWithDilutedMargin(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPct)
        var testMvrs = makeFuzzedCvrsForClca(listOf(cu.contest.info()), testCvrs, mvrsFuzzPct)

        if (!useConfig.hasStyle && Npop > Nc) { // TODO test this
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Npop - Nc) { makeUndervoteForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clcaWorkflow = WorkflowTesterClca(useConfig, listOf(cu.contest), emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, seed=useConfig.seed))

        return SingleContestAuditTask(
            name(),
            clcaWorkflow,
            parameters
        )
    }
}

// Simulate single Contest, do one-round audit
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
    val p2flips: Double? = null,  // used in attack
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

        val (cu, testCvrs) = simulateCvrsWithDilutedMargin(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPct)
        val testMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsForClca(listOf(cu.contest.info()), testCvrs, mvrsFuzzPct)
        }

        // TODO not adding the Nbs...
        val clcaWorkflow = WorkflowTesterClca(useConfig, listOf(cu.contest), emptyList(),
            MvrManagerForTesting(testCvrs, testMvrs, seed=useConfig.seed))

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
                contest.Npop,
                assorter.dilutedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
            )
        } else {
            val lastRound = minAssertion.auditResult!!
            WorkflowResult(
                name,
                Nc = contest.Npop,
                margin = assorter.dilutedMargin(),
                status = lastRound.status,
                nrounds = minAssertion.roundProved.toDouble(),
                samplesUsed = lastRound.samplesUsed.toDouble(),
                nmvrs = nmvrs.toDouble(),
                otherParameters + lastRound.params,
                failPct = if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0,
                wtf = (nmvrs - lastRound.samplesUsed) / minAssertion.roundProved.toDouble(),

                mvrMargin=mvrMargin,
                startingRates=null,
                measuredCounts=lastRound.measuredCounts,
            )
        }
    }
}

// keep this seperate function for testing
fun runClcaSingleRoundAudit(workflow: AuditWorkflow, contestRounds: List<ContestRound>, quiet: Boolean = true,
                            auditor: ClcaAssertionAuditorIF
): Int {
    val stopwatch = Stopwatch()

    val oneRound = AuditRound(1, contestRounds, samplePrns = emptyList())

    runClcaAuditRound(workflow.auditConfig(), oneRound, workflow.mvrManager(), 1, auditor = auditor)
    if (!quiet) println("runClcaSingleRoundAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")

    var maxSamples = 0
    contestRounds.forEach { contest->
        contest.assertionRounds.forEach { assertion ->
            maxSamples = max( maxSamples, assertion.estMvrs)
        }
    }
    return maxSamples
}


