package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

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
): WorkflowTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val useConfig = auditConfig ?:
        AuditConfig(AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror))

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs =  if (p2flips != null || p1flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clcaWorkflow = ClcaWorkflow(useConfig, listOf(sim.contest), emptyList(), testCvrs)
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
    val workflow: RlauxWorkflowIF,
    val testMvrs: List<Cvr>,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
    val auditor: ClcaAssertionAuditor,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name

    override fun run(): WorkflowResult {
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        val nmvrs = runClcaSingleRoundAudit(workflow, contestRounds, testMvrs, quiet = quiet, auditor)

        val contest = contestRounds.first() // theres only one
        val minAssertion = contest.minAssertion()!!
        val assorter = minAssertion.assertion.assorter

        val mvrMargin = assorter.calcAssorterMargin(contest.id, testMvrs, usePhantoms = true)
        if (debug) println(" mvrMargin=$mvrMargin")
        if (mvrMargin > .5) {
            println(" **** mvrMargin=$mvrMargin")
        }

        return if (minAssertion.auditResult == null) { // TODO why might this this empty?
            WorkflowResult(
                contest.Nc,
                assorter.reportedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRound = minAssertion.auditResult!!
            WorkflowResult(
                contest.Nc,
                assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                lastRound.samplesNeeded.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0,
                mvrMargin=mvrMargin,
            )
        }
    }
}

// runs test workflow with fake mvrs already generated, and the cvrs are variants of those
// return number of mvrs hand counted
fun runClcaSingleRoundAudit(workflow: RlauxWorkflowIF, contestRounds: List<ContestRound>, testMvrs: List<Cvr>, quiet: Boolean = false,
                            auditor: ClcaAssertionAuditor): Int {
    val stopwatch = Stopwatch()
    var roundIdx = 1

    // the ballot indices sorted by the random sampleNum,
    val sortedIndices = workflow.sortedBallotsOrCvrs().map{ it.index() }

    runSingleClcaAudit(workflow.auditConfig(), contestRounds, sortedIndices, testMvrs, workflow.cvrs(), auditor)

    if (!quiet) println("round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms")
    var maxSamples = 0
    contestRounds.forEach { contest->
        contest.assertionRounds.forEach { assertion ->
            maxSamples = max( maxSamples, assertion.estSampleSize)
        }
    }
    return maxSamples
}

fun runSingleClcaAudit(
    auditConfig: AuditConfig,
    contests: List<ContestRound>,
    sortedIndices: List<Int>,
    mvrs: List<Cvr>,
    cvrs: List<Cvr>,
    auditor: ClcaAssertionAuditor,
): Boolean {

    val contestsNotDone = contests.filter { !it.done }
    val sampledCvrs = sortedIndices.map { cvrs[it] }
    val sampledMvrs = sortedIndices.map { mvrs[it] }

    require(sampledCvrs.size == mvrs.size)
    val cvrPairs: List<Pair<Cvr, Cvr>> = sampledMvrs.zip(sampledCvrs)
    cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) } // prove that sampledCvrs correspond to mvrs

    contestsNotDone.forEach { contest ->
        contest.assertionRounds.forEach { assertion ->
            val testH0Result = auditor.run(auditConfig, contest.contestUA.contest, assertion, cvrPairs, 1)
            if (debug) {
                println(" testH0Result=$testH0Result")
            }
            assertion.status = testH0Result.status
            assertion.round = 1
        }
    }
    return true
}

private val debug = false


