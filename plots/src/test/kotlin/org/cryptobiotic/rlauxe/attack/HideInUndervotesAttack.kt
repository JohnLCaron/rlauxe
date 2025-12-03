package org.cryptobiotic.rlauxe.attack

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
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCloseableCvrs
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HideInUndervotesAttack {
    val name = "hideInUndervotesAttack"
    var dirName = "/home/stormy/rla/attack/$name"

    val N = 10000
    val nruns = 10000
    var phantomPct = .00
    var fuzzPct = .00

    @Test
    fun hideInUndervotesAttack() {
        val undervotes = listOf(.001, .003, .005, .075, .01, .02, .03, .04, .05, .10, .20, .30, .40, .50)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        undervotes.forEach { undervote ->
            val clcaGenerator = ClcaSingleRoundWorkflowTaskGeneratorU(
                N,
                true, fuzzPct, margin=undervote, undervote=undervote,
                otherParameters = mapOf("nruns" to nruns, "cat" to "hasStyle", "undervotes" to undervote),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGenerator2 = ClcaSingleRoundWorkflowTaskGeneratorU(
                N,
                false, fuzzPct, margin=undervote, undervote=undervote,
                otherParameters = mapOf("nruns" to nruns, "cat" to "noStyle", "undervotes" to undervote),
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
        showSampleSizesVsUndervote(dirName, name, subtitle, ScaleType.Linear)
        showSampleSizesVsUndervote(dirName, name, subtitle, ScaleType.LogLog)
        showSampleSizesVsUndervote(dirName, name, subtitle, ScaleType.LogLinear)
        showFalsePositivesVsUndervote(dirName, name, subtitle)
    }
}

class ClcaSingleRoundWorkflowTaskGeneratorU(
    val N: Int,
    val hasStyle: Boolean,
    val fuzzPct: Double,
    val margin: Double,
    val undervote: Double,
    val otherParameters: Map<String, Any> = emptyMap(),
    val quiet: Boolean = true,
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundWorkflowTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        // generate anew for each task, to get differenct permutation
        val (workflow, manager) = createWorkflow(N, hasStyle, fuzzPct, margin, undervote)

        return ClcaSingleRoundWorkflowTask(
            name(),
            workflow,
            auditor = ClcaAssertionAuditor(),
            manager.sortedMvrs,
            otherParameters,
            quiet,
        )
    }

    fun createWorkflow(
        Nc: Int,
        hasStyle: Boolean,
        fuzzPct: Double,
        margin: Double,
        undervotePct: Double
    ): Pair<AuditWorkflow, MvrManagerFromManifest> {
        val undervotes = roundUp(Nc*undervotePct)
        val nvotes = Nc - undervotes
        val diff = undervotes - 1
        val win = (nvotes + diff) / 2
        val lose = nvotes - win

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

        // to make the mvrs, we change all undervotes to candA.
        var countFlips = 0
        val mvrs = cards.mapIndexed { idx, card ->
            val cardVotes = card.votes!!
            if (cardVotes[1] != null && cardVotes[1]!!.isEmpty()) {
                countFlips++
                val flippedVotes = cardVotes.toMutableMap()
                flippedVotes[1] = intArrayOf(1)
                Cvr(card.location, flippedVotes, false, poolId)
            } else {
                card.cvr()
            }
        }
        assertEquals(undervotes, countFlips)
        val mvrTabs = tabulateCloseableCvrs(Closer(mvrs.iterator()), infos).toSortedMap()
        // mvrTabs.forEach { println(it) }
        val mvrVotes = mvrTabs[contestB.id]!!.votes
        // println("mvrVotes=${mvrVotes}")
        assertTrue(mvrVotes[1]!! > mvrVotes[2]!!)

        val config = AuditConfig(AuditType.CLCA, hasStyle = hasStyle, seed = 12356667890L)

        val Nbs = mapOf(1 to Nc)

        val mvrManager =
            MvrManagerFromManifest(cards, mvrs, contests.map { it.info() }, simFuzzPct = fuzzPct, Random.nextLong())
        return Pair(
            WorkflowTesterClca(config, listOf(contestB), emptyList(), mvrManager, Npops = Nbs),
            mvrManager
        )
    }
}

fun showSampleSizesVsUndervote(dirName: String, name:String, subtitle: String, scaleType: ScaleType) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "$name samples needed",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}${scaleType.name}",
        wrs = data,
        xname = "undervotePct", xfld = { it.Dparam("undervotes") },
        yname = "samplesNeeded", yfld = { it.samplesUsed },
        catName = "strategy", catfld = { category(it) },
        scaleType = scaleType
    )
}

fun showFalsePositivesVsUndervote(dirName: String, name:String, subtitle: String) {
    val io = WorkflowResultsIO("$dirName/${name}.csv")
    val data = io.readResults()
    wrsPlot(
        titleS = "switchWinnerMin successPct",
        subtitleS = subtitle,
        writeFile = "$dirName/${name}FalsePositives",
        wrs = data,
        xname = "undervotePct", xfld = { it.Dparam("undervotes") },
        yname = "successPct", yfld = { 100.0 - it.failPct },
        catName = "type", catfld = { category(it) },
    )
}
