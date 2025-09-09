package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.ClcaNoErrorIterator
import org.cryptobiotic.rlauxe.audit.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.core.TestH0Result
import org.cryptobiotic.rlauxe.estimate.ConcurrentTaskG
import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReaderSkip
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.toPoolMap
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.createZipFile

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

        val rlauxAudit = PersistentAudit(auditDir, true)
        rlauxAudit.contestsUA().forEach { contestUA ->
            contestUA.clcaAssertions.forEach { cassertion ->
                val assertionRound = AssertionRound(cassertion, 1, null)

                val mvrManager = MvrManagerCardsSingleRound(
                    AuditableCardCsvReaderSkip(
                        "$auditDir/sortedCards.csv",
                        skipPerRun * run
                    )
                )
                val sampler =
                    ClcaNoErrorIterator(
                        contestUA.id,
                        contestUA.Nc,
                        mvrManager.sortedCvrs().iterator(),
                        cassertion.cassorter
                    )

                val runner = OneAuditAssertionAuditor()
                val result: TestH0Result = runner.run(
                    rlauxAudit.auditConfig(),
                    contestUA.contest,
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

class SfoaSingleRoundAuditTaskContest18(
    val run: Int,
    val auditDir: String,
    val otherParameters: Map<String, Any>,
    val quiet: Boolean,
) : ConcurrentTaskG<WorkflowResult> {

    override fun name() = "run$run"

    override fun run(): WorkflowResult {
        if (!quiet) println("SfoaSingleRoundAuditTask start ${name()}")

        val rlauxAudit = PersistentAudit(auditDir, true)
        val contest18 = rlauxAudit.contestsUA().find { it.contest.id == 18 }!!
        val minAssertion = contest18.minClcaAssertion()!!
        val assertionRound = AssertionRound(minAssertion, 1, null)

        val mvrManager = MvrManagerCardsSingleRound(AuditableCardCsvReaderSkip("$auditDir/sortedCards.csv", skipPerRun * run))
        val sampler =
            ClcaNoErrorIterator(
                contest18.id,
                contest18.Nc,
                mvrManager.sortedCvrs().iterator(),
                minAssertion.cassorter)

        val runner = OneAuditAssertionAuditor()
        val result: TestH0Result = runner.run(
            rlauxAudit.auditConfig(),
            contest18.contest,
            assertionRound,
            sampler,
            1,
        )
        if (!quiet) println("${name()} result $result")

        return WorkflowResult(
            name(),
            contest18.Nc,
            minAssertion.assorter.reportedMargin(),
            result.status,
            1.0,
            result.sampleCount.toDouble(),
            result.sampleCount.toDouble(),
            otherParameters,
        )
    }
}


////////////////////////////////////////////////////////

const val sortedCardsFile = "sortedCards.csv"
fun createSortedCards(topDir: String, auditDir: String, cvrCsvFilename: String, zip: Boolean = true, ballotPoolFile: String?) {
    val ballotPools = if (ballotPoolFile != null) readBallotPoolCsvFile(ballotPoolFile) else null
    val pools = ballotPools?.toPoolMap()

    SortMerge(auditDir, cvrCsvFilename, "$topDir/sortChunks", "$auditDir/$sortedCardsFile", pools = pools).run()
    if (zip) {
        createZipFile("$auditDir/$sortedCardsFile", delete = false)
    }
}