package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.util.CloseableIterable
import kotlin.use

val skipPerRun = 8_000

interface WorkflowResultListTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<List<WorkflowResult>>
}

class SFoaSingleRoundAuditTaskGenerator(
    val run: Int,
    val auditDir: String,
    val mvrsFuzzPct: Double = 0.0,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
): WorkflowResultListTaskGenerator {

    override fun name() = "SFoaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<List<WorkflowResult>> {

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
) : ConcurrentTaskG<List<WorkflowResult>> {

    override fun name() = "run$run"

    override fun run(): List<WorkflowResult> {
        println("SfoaSingleRoundAuditTask start ${name()}")
        val wresults = mutableListOf<WorkflowResult>()

        val rlauxAudit = PersistedWorkflow(auditDir, true)
        rlauxAudit.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { cassertion ->
                val assertionRound = AssertionRound(cassertion, 1, null)
                val contestRound = ContestRound(contestUA, listOf(assertionRound), 1)

                val mvrManager = MvrManagerClcaSingleRound(
                    AuditableCardCsvReaderSkip("$auditDir/sortedCards.csv", skipPerRun * run)
                )

                val sampler =
                    ClcaNoErrorIterator(
                        contestUA.id,
                        contestUA.Nc,
                        cassertion.cassorter,
                        mvrManager.sortedCards().iterator(),
                    )

                val runner = OneAuditAssertionAuditor()
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

        println("SfoaSingleRoundAuditTask finish ${name()}")
        return wresults
    }
}

class MvrManagerClcaSingleRound(val sortedCards: CloseableIterable<AuditableCard>, val maxSamples: Int = -1) : MvrManager {

    override fun sortedCards() = sortedCards

    override fun makeMvrCardPairsForRound(): List<Pair<Cvr, Cvr>> {
        val cvrs = mutableListOf<Cvr>()
        var count = 0
        var countPool = 0
        sortedCards().iterator().use { cardIter ->
            while (cardIter.hasNext() && (maxSamples < 0 || count < maxSamples)) {
                val cvr = cardIter.next().cvr()
                cvrs.add(cvr)
                count++
                if (cvr.poolId != null) countPool++
            }
        }
        println("makeCvrPairsForRound: count=$count poolCount=$countPool")
        return cvrs.zip(cvrs)
    }

}