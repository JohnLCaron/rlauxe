package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCloseableCvrs
import org.cryptobiotic.rlauxe.workflow.*

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwitchWinnerMinAttack {
    val name = "switchWinnerMinAttack"
    var dirName = "$testdataDir/attack/$name"

    val N = 10000
    val nruns = 10000
    var phantomPct = .00
    var fuzzPct = .00

    @Test
    fun switchWinnerMinAttack() {
        val allMargins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .015, .02, .03, .04, .05)
        val margins = allMargins.filter { it > phantomPct }
        val stopwatch = Stopwatch()
        val hasStyle = false

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            val clcaGenerator = ClcaSingleRoundWorkflowTaskGenerator(
                N,
                true, fuzzPct, margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "hasStyle", "reportedMargin" to margin),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGenerator2 = ClcaSingleRoundWorkflowTaskGenerator(
                N,
                false, fuzzPct, margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "noStyle", "reportedMargin" to margin),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator2))
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks)
        // val results: List<WorkflowResult> = runWorkflows(tasks)
        println(stopwatch.took())

        val writer = WorkflowResultsIO("$dirName/${name}.csv")
        writer.writeResults(results)

        regenPlots()
    }

    @Test
    fun regenPlots() {
        val subtitle = "Nc=${N} nruns=${nruns}"
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsMargin(dirName, name, subtitle, ScaleType.LogLinear)
        showFalsePositivesVsMargin(dirName, name, subtitle)
    }

}

class ClcaSingleRoundWorkflowTaskGenerator(
    val N: Int,
    val hasStyle: Boolean,
    val fuzzPct: Double,
    val margin: Double,
    val otherParameters: Map<String, Any> = emptyMap(),
    val quiet: Boolean = true,
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundWorkflowTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        // generate anew for each task, to get differenct permutation
        val (workflow, manager) = createWorkflow(N, hasStyle, fuzzPct, margin)

        return ClcaSingleRoundWorkflowTask(
            name(),
            workflow,
            auditor = ClcaAssertionAuditor(),
            manager.sortedMvrs,
            otherParameters,
            quiet,
        )
    }


// 1. Clca, hasStyles = true
//
//* There are 11000 cards, contest1 Nc = 10000. contest2 Nc = 5000
//* Contest1 cards have votes = 4975 for candA and 5025 to candB. reported winner is candB with margin = 50/10000 = .005
//* Contest1 mvrs has votes = 5050 for candA and 4950 to candB. actual winner is candA with margin = 100/10000 = .01

    // see testHasStyleClcaMultiCard(), also might need testHasStyleClcaSingleCard?
    fun createWorkflow(
        Nc: Int,
        hasStyle: Boolean,
        fuzzPct: Double,
        margin: Double
    ): Pair<AuditWorkflow, MvrManagerFromManifest> {
        val diff = roundToClosest(margin * Nc)
        val win = (Nc + diff) / 2
        val lose = Nc - win

        // the contest must have the reported votes
        val contestB = Contest(
            ContestInfo("B", 1, mapOf("Wes" to 1, "Les" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to lose, 2 to win),
            Nc,
            Nc
        )
        // assertEquals(.005, contestB.margin(2, 1))

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 550, 2 to 450),
            1000,
            1000
        )

        val contests = listOf(contestB, contestS)
        val infos = contests.map { it.info }.associateBy { it.id }

        val poolId = if (hasStyle) null else 1
        val testData = MultiContestCombineData(listOf(contestB, contestS), contestB.Nc, poolId = poolId)

        // so this has to be the cards
        val cards = testData.makeCardsFromContests()
        assertEquals(Nc, cards.size)
        val cardTabs = tabulateAuditableCards(Closer(cards.iterator()), infos).toSortedMap()
        // cardTabs.forEach { println(it) }
        contests.forEach { contest ->
            assertEquals(contest.votes, cardTabs[contest.id]!!.votes)
        }

        // to make the mvrs, we change diff/2 votes for candB to candA. cards will get randomized in the mvrManager
        var countFlips = 0
        val wantFlips = (diff + 3) / 2
        val mvrs = cards.mapIndexed { idx, card ->
            val cardVotes = card.votes!!
            if (countFlips < wantFlips && cardVotes[1] != null && cardVotes[1]!!.contains(2)) {
                countFlips++
                val flippedVotes = cardVotes.toMutableMap()
                flippedVotes[1] = intArrayOf(1)
                Cvr(card.location, flippedVotes, false, poolId)
            } else {
                card.cvr()
            }
        }
        val mvrTabs = tabulateCloseableCvrs(Closer(mvrs.iterator()), infos).toSortedMap()
        // mvrTabs.forEach { println(it) }
        val mvrVotes = mvrTabs[contestB.id]!!.votes
        // println("mvrVotes=${mvrVotes}")
        assertTrue(mvrVotes[1]!! > mvrVotes[2]!!)

        val config = AuditConfig(AuditType.CLCA, seed = 12356667890L)

        val Nbs = mapOf(1 to Nc)

        val mvrManager =
            MvrManagerFromManifest(cards, mvrs, contests.map { it.info() }, seed=Random.nextLong(), simFuzzPct = fuzzPct)
        return Pair(
            WorkflowTesterClca(config, listOf(contestB), emptyList(), mvrManager, Npops = Nbs),
            mvrManager
        )
    }
}


fun showSampleSizesVsMargin(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "reportedMargin", xfld = { it.margin },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "strategy", catfld = { category(it) },
        scaleType = scaleType
    )
}

fun showFalsePositivesVsMargin(dirName: String, name:String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name successPct",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}FalsePositives",
        wrs = data,
        xname = "reportedMargin", xfld = { it.margin },
        yname = "successPct", yfld = { 100.0 - it.failPct },
        catName = "type", catfld = { category(it) },
    )
}
