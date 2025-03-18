package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.estimate.*
import kotlin.math.max

class PollingWorkflowTaskGenerator(
        val Nc: Int, // including undervotes but not phantoms
        val margin: Double,
        val underVotePct: Double,
        val phantomPct: Double,
        val mvrsFuzzPct: Double,
        val parameters : Map<String, Any>,
        val auditConfig: AuditConfig? = null,
        val Nb: Int = Nc,
        val nsimEst: Int = 100,
    ) : WorkflowTaskGenerator {
    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.POLLING, true, nsimEst = nsimEst,
            pollingConfig = PollingConfig(simFuzzPct = mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        var ballotManifest = sim.makeBallotManifest(useConfig.hasStyles)

        if (!useConfig.hasStyles && Nb > Nc) {
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeUndervoteForContest(otherContestId) }
            testMvrs = testMvrs + otherCvrs

            val otherBallots = List<Ballot>(Nb - Nc) { Ballot("other${Nc+it}", false, null) }
            ballotManifest = BallotManifest(ballotManifest.ballots + otherBallots, emptyList())
        }

        val ballotCards = BallotCardsPollingStart(ballotManifest.ballots, testMvrs, useConfig.seed)
        val pollingWorkflow = PollingWorkflow(useConfig, listOf(sim.contest), ballotCards)
        return WorkflowTask(
            name(),
            pollingWorkflow,
            // testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 2.0)
        )
    }
}
// Do the audit in a single round, dont use estimateSampleSizes
class PollingSingleRoundAuditTaskGenerator(
        val Nc: Int, // including undervotes but not phantoms
        val margin: Double,
        val underVotePct: Double,
        val phantomPct: Double,
        val mvrsFuzzPct: Double,
        val parameters : Map<String, Any>,
        val auditConfig: AuditConfig? = null,
        val Nb: Int = Nc,
        val nsimEst: Int = 100,
        val quiet: Boolean = true,
    ): WorkflowTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): PollingSingleRoundAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.POLLING, true, nsimEst = nsimEst,
            pollingConfig = PollingConfig(simFuzzPct = mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        var ballotManifest = sim.makeBallotManifest(useConfig.hasStyles)

        val ballotCards = BallotCardsPollingStart(ballotManifest.ballots, testMvrs, useConfig.seed)
        val pollingWorkflow = PollingWorkflow(useConfig, listOf(sim.contest), ballotCards)

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
    val workflow: RlauxWorkflowIF,
    val testMvrs: List<Cvr>,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = name

    override fun run(): WorkflowResult {
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }
        runPollingAudit(workflow.auditConfig(), contestRounds, workflow.ballotCards() as BallotCardsPolling, 1)

        var maxSamples = 0
        contestRounds.forEach { contest->
            contest.assertionRounds.forEach { assertion ->
                maxSamples = max( maxSamples, assertion.estSampleSize)
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
                name,
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