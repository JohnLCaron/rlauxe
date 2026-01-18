package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.estimate.*
import kotlin.math.max

class PollingContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val Npop: Int,
    val nsimEst: Int = 100,
    ) : ContestAuditTaskGenerator {

    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.POLLING, true, nsimEst = nsimEst, simFuzzPct = mvrsFuzzPct,
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val ballotCards = MvrManagerForTesting(testMvrs, testMvrs, useConfig.seed)
        val pollingWorkflow = WorkflowTesterPolling(useConfig, listOf(sim.contest), ballotCards)
        return ContestAuditTask(
            name(),
            pollingWorkflow,
            // testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 2.0)
        )
    }
}
// Do the audit in a single round, dont use estimateSampleSizes
class PollingSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val Npop: Int = Nc,
    val quiet: Boolean = true,
    ): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): PollingSingleRoundAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.POLLING, true, simFuzzPct = mvrsFuzzPct
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val ballotCards = MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed)
        val pollingWorkflow = WorkflowTesterPolling(useConfig, listOf(sim.contest), ballotCards)

        return PollingSingleRoundAuditTask(
            name(),
            pollingWorkflow,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 2.0),
            quiet,
        )
    }
}

class PollingSingleRoundAuditTask(
    val name: String,
    val workflow: AuditWorkflow,
    val testMvrs: List<Cvr>,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name

    override fun run(): WorkflowResult {
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runPollingAuditRound(workflow.auditConfig(), contestRounds, workflow.mvrManager(), 1)

        var maxSamples = 0
        contestRounds.forEach { contest->
            contest.assertionRounds.forEach { assertion ->
                maxSamples = max( maxSamples, assertion.estMvrs)
            }
        }
        val nmvrs = maxSamples

        val contest = contestRounds.first() // theres only one
        val minAssertion = contest.minAssertion()!!
        val assorter = minAssertion.assertion.assorter
        val mvrMargin = assorter.calcAssorterMargin(contest.id, testMvrs, usePhantoms = true) // TODO needed or debugging?

        return if (minAssertion.auditResult == null) { // TODO why might this this empty?
            WorkflowResult(
                name,
                contest.Npop,
                assorter.dilutedMargin(),
                TestH0Status.ContestMisformed,
                0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRound = minAssertion.auditResult!!
            WorkflowResult(
                name,
                contest.Npop,
                assorter.dilutedMargin(),
                lastRound.status,
                minAssertion.roundProved.toDouble(),
                lastRound.samplesUsed.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0,
                mvrMargin=mvrMargin,
            )
        }
    }
}