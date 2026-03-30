package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.persist.CardManifest

val skipPerRun = 8_000

interface WorkflowResultListTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTask<List<WorkflowResult>>
}

class SFoaSingleRoundAuditTaskGenerator(
    val run: Int,
    val auditDir: String,
    val mvrsFuzzPct: Double = 0.0,
    val parameters : Map<String, Any>,
    val auditConfigIn: Config? = null,
): WorkflowResultListTaskGenerator {

    override fun name() = "SFoaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ConcurrentTask<List<WorkflowResult>> {

        return SfoaSingleRoundAuditTask(
            run,
            auditDir,
            parameters,
            quiet = false,
        )
    }
}

class SfoaSingleRoundAuditTask(
    val run: Int,
    val auditDir: String,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTask<List<WorkflowResult>> {

    override fun name() = "run$run"

    override fun run(): List<WorkflowResult> {
        println("SfoaSingleRoundAuditTask start ${name()}")
        val wresults = mutableListOf<WorkflowResult>()

        val rlauxAudit = PersistedWorkflow.readFrom(auditDir)!!
        val mvrManager = rlauxAudit.mvrManager()
        val batches = mvrManager.batches()

        rlauxAudit.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { cassertion ->
                val assertionRound = AssertionRound(cassertion, 1, null)
                val contestRound = ContestRound(contestUA, listOf(assertionRound), 1)

                val skipper = AuditableCardCsvReaderSkip("$auditDir/sortedCards.csv", skipPerRun * run, batches)
                val manifestWithSkipper = CardManifest(skipper, 0)

                val sampler =
                    ClcaSamplerErrorTracker.withNoErrors(
                        contestUA.id,
                        cassertion.cassorter,
                        manifestWithSkipper.cards.iterator(),
                    )

                val runner = OneAuditAssertionAuditor(rlauxAudit.mvrManager().pools() as List<CardPoolIF>)

                val result: TestH0Result = runner.run(
                    rlauxAudit.config(),
                    contestRound,
                    assertionRound,
                    sampler,
                    1,
                )
                if (!quiet) println("${name()} contest ${contestUA.id} assertion ${cassertion.cassorter.shortName()} result $result")

                wresults.add(
                    WorkflowResult(
                        name(),
                        contestUA.Nc,
                        cassertion.assorter.dilutedMargin(),
                        result.status,
                        1.0,
                        result.sampleCount.toDouble(),
                        result.sampleCount.toDouble(),
                        otherParameters + mapOf("contest" to "${contestUA.id}", "assertion" to cassertion.assorter.winLose() ),
                    )
                )
            }
        }

        println("SfoaSingleRoundAuditTask finish ${name()}")
        return wresults
    }
}