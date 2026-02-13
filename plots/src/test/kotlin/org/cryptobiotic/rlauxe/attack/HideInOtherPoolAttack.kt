package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.MergePopulationsIntoCards
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTestContests
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.showTabs
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateOneAuditPools
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HideInOtherPoolAttack {
    val name = "hideInOtherPoolAttack"
    var dirName = "$testdataDir/attack/$name"

    val N = 20000
    val Npool = 10000
    val nruns = 10000
    var phantomPct = .00
    var fuzzPct = .00

    @Test
    fun hideInOtherPoolAttack() {
        val margins = listOf(.001, .003, .005, .075, .01, .02, .03, .04, .05, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            val oaGenerator = OASingleRoundWorkflowTaskGeneratorG(
                N, Npool,
                true, fuzzPct, margin=margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "hasStyle", ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oaGenerator))

            val oaGenerator2 = OASingleRoundWorkflowTaskGeneratorG(
                N,Npool,
                false, fuzzPct, margin=margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "noStyle", ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, oaGenerator2))
        }

        // run tasks concurrently
        val results: List<WorkflowResult> = runRepeatedWorkflowsAndAverage(tasks) // , nthreads=1)
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
        showFalsePositivesVsMargin(dirName, name, subtitle)
    }
}

private const val debug = false

class OASingleRoundWorkflowTaskGeneratorG(
    val N: Int,
    val Npool: Int,
    val hasStyle: Boolean,
    val fuzzPct: Double,
    val margin: Double,
    val otherParameters: Map<String, Any> = emptyMap(),
): ContestAuditTaskGenerator {

    override fun name(): String {
        return "ClcaSingleRoundWorkflowTaskGenerator"
    }

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        // generate anew for each task, to get differenct permutation
        val (workflow, manager) = createWorkflow(N, Npool, hasStyle, fuzzPct, margin)

        return ClcaSingleRoundWorkflowTask(
            name(),
            workflow,
            auditor = OneAuditAssertionAuditor(workflow.mvrManager().oapools()!!),
            manager.sortedMvrs,
            otherParameters,
        )
    }

    fun createWorkflow(
        Nc: Int,
        Npool: Int,
        hasStyle: Boolean,
        fuzzPct: Double,
        margin: Double,
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
        val testData1 = MultiContestCombineData(listOf(contestB), contestB.Nc)
        val testCvrs1 = testData1.makeCardsFromContests()

        val contestS = Contest(
            ContestInfo("S", 2, mapOf("Del" to 1, "Mel" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to (Nc/2 + 100)/2, 2 to (Nc/2-100)/2),
            Nc/2,
            Nc/2
        )
        val testData2 = MultiContestCombineData(listOf(contestS), contestS.Nc)
        val testCvrs2 = testData2.makeCardsFromContests(testCvrs1.size)

        val cardsu = mutableListOf<AuditableCard>()
        cardsu.addAll(testCvrs1)
        cardsu.addAll(testCvrs2)
        assertEquals(30000, cardsu.size)
        cardsu.shuffle(Random)

        val contests = listOf(contestB, contestS)
        val infos = contests.map{ it.info }.associateBy { it.id }
        val tabs = tabulateAuditableCards(Closer(cardsu.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, tabs[contest.id]!!.votes)
        }

        // now divide the cards into pools and cvrs
        val cardStyles = listOf(
            Population("group1", 1, intArrayOf(1, 2), false),
            Population("group2", 2, intArrayOf(2), false),
        )

        // cards with pools
        var countPool = 0
        val cardsp = cardsu.map { mcard ->
            if (countPool < Npool) { // put first Npool into the pool
                countPool++
                mcard.copy(poolId=1, cardStyle = "group1")
            } else
                mcard
        }
        val cardTabsp = tabulateAuditableCards(Closer(cardsp.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, cardTabsp[contest.id]!!.votes)
        }

        // modify the cardManifest to lie about cards contain contest1
        // can only modify pool votes, because those dont have the cvr

        // flip diff mvrs from candB to candA
        val flippedLocations = mutableSetOf<String>()
        val wantFlips = diff
        var countFlips = 0
        val mvrs = cardsp.map { mcard ->
            val org = mcard.cvr()

            if (countFlips < wantFlips && mcard.cardStyle == "group1" && mcard.votes!!.contains(1)) {
                val votesForContest1 = mcard.votes!!.get(1)!!
                if (votesForContest1.contains(2)) { // if they voted for candB
                    countFlips++
                    flippedLocations.add(mcard.location())

                    val mvotes = org.votes.toMutableMap()
                    mvotes[1] = intArrayOf(1) // switch vote to candidate A
                    org.copy(votes = mvotes)
                } else org

            } else {
                org
            }
        }
        if (debug) {
            val mvrTabs = tabulateCvrs(Closer(mvrs.iterator()), infos)
            println("mvrTabs")
            mvrTabs.forEach { (_, tab) ->
                println(" $tab")
            }
            println()
        }

        // now claim that the flipped cards are in group 2
        val modifiedCards = cardsp.map { mcard ->
            if (flippedLocations.contains(mcard.location())) {
                mcard.copy(poolId=2)
            }
            else
                mcard
        }
        val mtabs = tabulateAuditableCards(Closer(modifiedCards.iterator()), infos).toSortedMap()
        contests.forEach { contest ->
            assertEquals(contest.votes, mtabs[contest.id]!!.votes)
        }

        val converter = MergePopulationsIntoCards(
            cards = modifiedCards,
            cardStyles,
        )
        val cardManifest = mutableListOf<AuditableCard>()
        converter.forEach { cardManifest.add(it) }
        val cmtabs = tabulateAuditableCards(Closer(cardManifest.iterator()), infos).toSortedMap()
        if (debug) {
            println(showTabs("cardManifestTabs", cmtabs))
        }

        // TODO need the fake mvrs i think ??
        val fakeMvrs = cardsp.map { it.cvr() }
        val config = AuditConfig(AuditType.ONEAUDIT, hasStyle = hasStyle, seed = 12356667890L)
        val (contestsUA, cardPools) =
            makeOneAuditTestContests(
                infos,
                listOf(contestB),
                cardStyles,
                cardManifest,
                fakeMvrs
            )

        val poolSums = tabulateOneAuditPools(cardPools, infos)
        if (debug) println(showTabs("poolSums", poolSums))

        val sumWithPools = mutableMapOf<Int, ContestTabulation>()
        sumWithPools.sumContestTabulations(cmtabs)
        sumWithPools.sumContestTabulations(poolSums)
        if (debug) println(showTabs("sumWithPools", sumWithPools))

        contests.forEach { contest ->
            assertEquals(contest.votes, sumWithPools[contest.id]!!.votes)
        }

        // must be the real mvrs
        val mvrManager = MvrManagerFromManifest(cardManifest, mvrs, infos.values.toList(), seed=Random.nextLong(), simFuzzPct=fuzzPct)

        val workflow =  WorkflowTesterOneAudit(config, contestsUA, mvrManager)

        return Pair(workflow, mvrManager)
    }
}
