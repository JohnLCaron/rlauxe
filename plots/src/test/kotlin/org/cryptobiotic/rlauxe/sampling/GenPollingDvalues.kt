package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.rlaplots.SRTcsvWriter
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import kotlin.test.Test
import kotlin.math.ceil

class GenPollingDvalues {

    @Test
    fun genPollingDvalues() {
        val reportedMeans = listOf(.55) // .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        // val thetas = listOf(.501, .502, .503, .504, .505, .506, .507, .508, .51, .52, .53, .54)
        val reportedMeanDiffs = listOf(0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.0, -.005, -.01, -.025, -.05, -0.1, -0.2)
        val dlist = listOf(10, 50, 250, 1250)

        val Nc = 10000

        val tasks = mutableListOf<ConcurrentTaskG<RunTestRepeatedResult>>()
        reportedMeans.forEach { reportedMean ->
            reportedMeanDiffs.forEach { reportedMeanDiff ->
                val theta = reportedMean + reportedMeanDiff // the true mean, used to generate the mvrs
                val mvrs = makeCvrsByExactMean(Nc, theta)
                val contestUA = ContestUnderAudit(make2wayContestFromMargin(Nc, reportedMean), isComparison = false)
                val assorter = contestUA.makePollingAssertions().minPollingAssertion()!!.assorter

                dlist.forEach { d ->
                    val reportedMargin = mean2margin(reportedMean)
                    val otherParameters =
                        mapOf("reportedMean" to reportedMean, "reportedMeanDiff" to reportedMeanDiff, "d" to d.toDouble())

                    val auditConfig = AuditConfig(
                        AuditType.POLLING,
                        hasStyles = true,
                        seed = 12356667890L,
                        quantile = .80,
                        fuzzPct = null,
                        ntrials = 10,
                        d1 = d
                    )

                    val sampler = PollWithoutReplacement(contestUA.contest as Contest, mvrs, assorter)
                    tasks.add(
                        AlphaTask(
                            name = "theta = $theta, reportedMargin = $reportedMargin",
                            auditConfig = auditConfig,
                            sampler,
                            margin = reportedMargin,
                            upperBound = 1.0,
                            maxSamples = Nc,
                            Nc = Nc,
                            otherParameters = otherParameters,
                        )
                    )
                }
            }
        }

        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunnerG<RunTestRepeatedResult>().run(tasks)
        val srts = results.map { it.makeSRT(it.testParameters["reportedMean"]?: 0.0, it.testParameters["reportedMeanDiff"]?: 0.0) }

        val dirName = "/home/stormy/temp/polling"
        val filename = "PollingDvalues"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showMeanDifference(catfld = { "d=${nfn(it.d,4)}" })
    }
}


fun make2wayContestInfo(type: SocialChoiceFunction = SocialChoiceFunction.PLURALITY) = ContestInfo(
    "standard2way",
    0,
    mapOf("cand0" to 0, "cand1" to 1),
    type,
)

fun make2wayContestFromMargin(Nc: Int, margin:Double): Contest {
    require(margin > 0.0)
    val winningVote = ceil(Nc*margin2mean(margin)).toInt()
    return Contest(
        info = make2wayContestInfo(),
        voteInput = mapOf(0 to winningVote, 1 to Nc-winningVote),
        Nc = Nc,
        Np=0, // TODO
    )
}