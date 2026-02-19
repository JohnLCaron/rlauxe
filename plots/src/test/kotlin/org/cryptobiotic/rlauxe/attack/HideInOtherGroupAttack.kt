package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.concur.RepeatedWorkflowRunner
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.MultiContestCombineData
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCloseableCvrs
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.random.Random
import kotlin.sequences.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class HideInOtherGroupAttack {
    val name = "hideInOtherGroupAttack"
    var dirName = "$testdataDir/attack/$name"

    val N = 10000
    val nruns = 100
    var phantomPct = .00
    var fuzzPct = .00

    @Test
    fun hideInOtherGroupAttack() {
        val margins = listOf(.001, .003, .005, .075, .01, .02, .03, .04, .05, .10)
        val stopwatch = Stopwatch()

        val tasks = mutableListOf<RepeatedWorkflowRunner>()
        margins.forEach { margin ->
            val clcaGenerator = ClcaSingleRoundWorkflowTaskGeneratorG(
                N,
                true, fuzzPct, margin=margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "hasStyle", ),
            )
            tasks.add(RepeatedWorkflowRunner(nruns, clcaGenerator))

            val clcaGenerator2 = ClcaSingleRoundWorkflowTaskGeneratorG(
                N,
                false, fuzzPct, margin=margin,
                otherParameters = mapOf("nruns" to nruns, "cat" to "noStyle", ),
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
        showFalsePositivesVsMargin(dirName, name, subtitle)
    }
}

class ClcaSingleRoundWorkflowTaskGeneratorG(
    val N: Int,
    val hasStyle: Boolean, // TODO hasStyle=false
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

    fun createWorkflow(
        Nc: Int,
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

        val cardsu = testData.makeCardsFromContests()
        val cardTabs = tabulateAuditableCards(Closer(cardsu.iterator()), infos).toSortedMap()

        contests.forEach { contest ->
            assertEquals(contest.votes, cardTabs[contest.id]!!.votes)
        }
        // now change the groups in the cardManifest
        val cardStyles = listOf(
            Population("group1",  1,intArrayOf(1,2), false),
            Population("group2", 2, intArrayOf(2), false),
        )
        val modifiedCards = mutableListOf<AuditableCard>()
        val cardAttacker = CardsWithStylesAttack(AuditType.CLCA, cards=Closer(cardsu.iterator()), styles=cardStyles, wantFlips=diff+1)
        while (cardAttacker.hasNext()) {
            modifiedCards.add(cardAttacker.next())
        }
        assertEquals(diff+1, cardAttacker.flipCount)

        val mcardTabs = tabulateAuditableCards(Closer(modifiedCards.iterator()), infos).toSortedMap()
        if (contestB.votes != mcardTabs[1]!!.votes) {
            println("unmodified cards")
            cardTabs.forEach { println("  $it") }
            println("modified cards")
            mcardTabs.forEach { println("  $it") }
            fail()
        }
        contests.forEach { contest ->
            assertEquals(contest.votes, mcardTabs[contest.id]!!.votes)
        }

        // now form the mvrs with the flips
        var countFlips = 0
        val mvrs = modifiedCards.map { mcard ->
            if (mcard.cardStyle == "group2" && mcard.votes!!.contains(1)) { // find the flips
                countFlips++
                val org = mcard.cvr()
                val mvotes = org.votes.toMutableMap()
                mvotes[1] = intArrayOf(1) // switch vote to candidate A
                org.copy(votes = mvotes)
            } else
                mcard.cvr()
        }

        val mvrTabs = tabulateCloseableCvrs(Closer(mvrs.iterator()), infos).toSortedMap()
        val mvrVotes = mvrTabs[contestB.id]!!.votes
        if (mvrVotes[1]!! <= mvrVotes[2]!!) {
            mvrTabs.forEach { println(it) }
            println("mvrVotes=${mvrVotes}")
        }
        assertTrue(mvrVotes[1]!! > mvrVotes[2]!!)

        val config = AuditConfig(AuditType.CLCA, seed = 12356667890L)

        val Nbs = mapOf(1 to Nc)

        val mvrManager =
            MvrManagerFromManifest(modifiedCards, mvrs, contests.map { it.info() }, seed=Random.nextLong(), simFuzzPct=fuzzPct)
        return Pair(
            WorkflowTesterClca(config, listOf(contestB), emptyList(), mvrManager, Npops = Nbs),
            mvrManager
        )
    }
}

class CardsWithStylesAttack(
    val type: AuditType,
    val cvrsAreComplete: Boolean = true,
    val cards: CloseableIterator<AuditableCard>,
    phantomCards : List<AuditableCard>? = null,
    styles: List<Population>,
    val wantFlips: Int
): CloseableIterator<AuditableCard> {

    val poolMap = styles.associateBy{ it.name() }
    val allCards: Iterator<AuditableCard>
    var cardIndex = 1
    var flipCount = 0

    init {
        allCards = if (phantomCards == null) {
            cards
        } else {
            val cardSeq = cards.iterator().asSequence()
            val phantomSeq = phantomCards.asSequence()
            (cardSeq + phantomSeq).iterator()
        }
    }

    override fun hasNext() = allCards.hasNext()

    override fun next(): AuditableCard {
        val org = allCards.next()
        val hasCvr = type.isClca()

        // original
        var style = if (org.hasContest(2)) poolMap["group2"]!! else poolMap["group1"]!!
        if (org.hasContest(1) && org.votes!![1]!!.contains(2) && flipCount < wantFlips) {
            style = poolMap["group2"]!!
            flipCount++
        }

        val contests = style.contests()
        val votes = if (hasCvr) org.votes else null

        return AuditableCard(org.location, cardIndex++, 0, phantom=org.phantom,
            //contests,
            votes,
            poolId=null,
            style.name(),
        )
    }

    override fun close() = cards.close()
}
