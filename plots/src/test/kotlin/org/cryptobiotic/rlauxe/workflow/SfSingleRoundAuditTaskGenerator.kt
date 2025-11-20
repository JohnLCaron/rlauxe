package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.estimate.ClcaNoErrorIterator
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator

class SfSingleRoundAuditTaskGenerator(
    val run: Int,
    val auditDir: String,
    val mvrsFuzzPct: Double = 0.0,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
): WorkflowResultListTaskGenerator {

    override fun name() = "SfSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<List<WorkflowResult>> {

        return SfSingleRoundAuditTask(
            run,
            auditDir,
            parameters,
            quiet = false,
        )
    }
}

class SfSingleRoundAuditTask(
    val run: Int,
    val auditDir: String,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTaskG<List<WorkflowResult>> {

    override fun name() = "run$run"

    override fun run(): List<WorkflowResult> {
        println("SfSingleRoundAuditTask start ${name()}")
        val wresults = mutableListOf<WorkflowResult>()

        val rlauxAudit = PersistedWorkflow(auditDir, true)
        rlauxAudit.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { cassertion ->
                val assertionRound = AssertionRound(cassertion, 1, null)
                val contestRound = ContestRound(contestUA, listOf(assertionRound), 1)

                val mvrManager = MvrManagerClcaSingleRound(
                    AuditableCardCsvReaderSkip(
                        "$auditDir/sortedCards.csv",
                        skipPerRun * run
                    )
                )
                val sampler =
                    ClcaNoErrorIterator(
                        contestUA.id,
                        contestUA.Nc,
                        cassertion.cassorter,
                        mvrManager.sortedCvrs().iterator(),
                    )

                val runner = ClcaAssertionAuditor()
                val result: TestH0Result = runner.run(
                    rlauxAudit.auditConfig(),
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
                        cassertion.assorter.reportedMargin(),
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

class AuditableCardCsvReaderSkip(val filename: String, val skip: Int): CloseableIterable<AuditableCard> {
    override fun iterator(): CloseableIterator<AuditableCard> {
        val iter = readCardsCsvIterator(filename)
        repeat(skip) { if (iter.hasNext()) (iter.next()) }
        return iter
    }
}