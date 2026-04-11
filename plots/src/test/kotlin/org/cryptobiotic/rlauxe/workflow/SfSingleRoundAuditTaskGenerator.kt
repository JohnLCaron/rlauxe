package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.merge
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator

// only used by SfAuditVariance
class SfSingleRoundAuditTaskGenerator(
    val run: Int,
    val auditDir: String,
    val mvrsFuzzPct: Double = 0.0,
    val parameters : Map<String, Any>,
    val auditConfigIn: Config? = null,
): WorkflowResultListTaskGenerator {

    override fun name() = "SfSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ConcurrentTask<List<WorkflowResult>> {

        return SfSingleRoundAuditTask(
            run,
            auditDir,
            parameters,
            quiet = false,
        )
    }
}

// doing all the assertions in a single task, so its slow
// skips 8000 cards each run
// 1. original probably didnt use diluted margin
class SfSingleRoundAuditTask(
    val run: Int,
    val auditDir: String,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTask<List<WorkflowResult>> {

    override fun name() = "run$run"

    override fun run(): List<WorkflowResult> {
        println("SfSingleRoundAuditTask start ${name()}")
        val wresults = mutableListOf<WorkflowResult>()

        val rlauxAudit = PersistedWorkflow.readFrom(auditDir)!!
        val mvrManager = rlauxAudit.mvrManager()
        val batches = mvrManager.batches()

        rlauxAudit.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { cassertion ->
                val assertionRound = AssertionRound(cassertion, 1, null)
                val contestRound = ContestRound(contestUA, listOf(assertionRound), 1)

                val skipper = AuditableCardCsvReaderSkip("$auditDir/sortedCards.csv", skipPerRun * run, batches)
                val manifestWithSkipper = CardManifest(skipper, 0, )

                val sampler =
                    ClcaSamplerErrorTracker.withNoErrors(
                        contestUA.id,
                        cassertion.cassorter,
                        manifestWithSkipper.cards.iterator(),
                    )

                val runner = ClcaAssertionAuditor()
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

        println("SfSingleRoundAuditTask finish ${name()}")
        return wresults
    }
}

class AuditableCardCsvReaderSkip(val filename: String, val skip: Int, val batches: List<StyleIF>?): CloseableIterable<AuditableCard> {
    override fun iterator(): CloseableIterator<AuditableCard> {
        val cardsNoBatchSkipped = readCardsCsvIterator(filename)
        repeat(skip) { if (cardsNoBatchSkipped.hasNext()) (cardsNoBatchSkipped.next()) }
        return merge(cardsNoBatchSkipped, batches)
    }
}