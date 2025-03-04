package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.concur.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test

class GenPollingNoStylesOrg {

    // TODO candidate for removal

    @Test
    fun plotPollingNoStyleOrg() {
        val Nc = 10000
        val Ns = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.01, .02, .04, .06, .08, .10, .12, .16, .20)

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=false, nsimEst = 100)
        println("ntrials = ${auditConfig.nsimEst} quantile = ${auditConfig.quantile}")

        val tasks = mutableListOf<ConcurrentTaskG<EstimationResult>>()
        Ns.forEach { N ->
            margins.forEach { margin ->
                val fcontest = ContestTestData(0, 4, margin, 0.0, 0.0)
                fcontest.ncards = Nc
                val contest = fcontest.makeContest()

                print("reportedMargin = $margin ${contest.votes} Nc=$Nc N=$N")
                val contestUA = ContestUnderAudit(contest, isComparison = false)
                contestUA.makePollingAssertions()
                val moreParameters = mapOf("N" to N.toDouble(), "reportedMargin" to margin)
                val contestRound = ContestRound(contestUA, 1)

                val task = SimulateSampleSizeTask(
                    1,
                    auditConfig,
                    contestRound,
                    contestRound.minAssertion()!!,
                    emptyList(),
                    1.0,
                    0,
                    moreParameters=moreParameters
                )
                tasks.add(task)
            }
        }

        // run tasks concurrently
        val results: List<EstimationResult> = ConcurrentTaskRunnerG<EstimationResult>().run(tasks)

        // val results: List<EstimationResult> = EstimationTaskRunner().run(tasks)
        val srts = results.map { it.makeSRTnostyle(Nc) }

        val dirName = "/home/stormy/temp/estimate"
        val filename = "PollingNoStyle"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showSamples( catfld = { "N=${nfn(it.N, 6)}" } )
    }


}