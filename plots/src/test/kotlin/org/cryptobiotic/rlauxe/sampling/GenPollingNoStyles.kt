package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.rlaplots.makeSRTnostyle
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import kotlin.test.Test

class GenPollingNoStyles {

    @Test
    fun plotPollingNoStyle() {
        val Nc = 10000
        val Ns = listOf(10000, 20000, 50000, 100000)
        val margins = listOf(.01, .02, .04, .06, .08, .10, .12, .16, .20)

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles=false, seed = 123556667890L, quantile=.80, fuzzPct=null, ntrials = 100)
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile}")

        val tasks = mutableListOf<EstimationTask>()
        Ns.forEach { N ->
            margins.forEach { margin ->
                val fcontest = ContestTestData(0, 4, margin, 0.0, 0.0)
                fcontest.ncards = Nc
                val contest = fcontest.makeContest()

                print("reportedMargin = $margin ${contest.votes} Nc=$Nc N=$N")
                val contestUA = ContestUnderAudit(contest, isComparison = false)
                contestUA.makePollingAssertions()
                val assertion = contestUA.minAssertion()!!
                val moreParameters = mapOf("N" to N.toDouble(), "reportedMargin" to margin)

                val task = SimulateSampleSizeTask(
                    auditConfig,
                    contestUA,
                    assertion,
                    emptyList(),
                    1.0,
                    0,
                    moreParameters=moreParameters
                )
                tasks.add(task)
            }
        }

        // run tasks concurrently
        val results: List<EstimationResult> = EstimationTaskRunner().run(tasks)
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