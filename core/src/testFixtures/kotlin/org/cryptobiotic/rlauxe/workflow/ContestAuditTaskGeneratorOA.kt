package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsFrom
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.collections.map
import kotlin.random.Random
import kotlin.test.assertTrue

// mvrsFuzzPct=fuzzPct, nsimEst = nsimEst
class OneAuditContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
) : ContestAuditTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean)
        )

        val (contestOA, _, oaCvrs) = makeOneContestUA(
            margin,
            Nc,
            cvrFraction = cvrPercent,
            undervoteFraction = underVotePct,
            phantomFraction = phantomPct
        )
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)

        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA),
            MvrManagerClcaForTesting(oaCvrs, oaMvrs, config.seed))
        return ContestAuditTask(
            name(),
            oneaudit,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to mvrsFuzzPct, "auditType" to 1.0)
        )
    }
}

// Do audit on one contest, in a single round
class OneAuditSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundSingleContestAuditTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, )
        )

        val (contestOA, _, oaCvrs) =
            makeOneContestUA(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct
            )

        val oaMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(oaCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)
        }

        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA), MvrManagerClcaForTesting(oaCvrs, oaMvrs, config.seed))
        return ClcaSingleRoundSingleContestAuditTask(
            name(),
            oneaudit,
            oaMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}

// Do audit on multiple contests, in a single round
class OneAuditSingleRoundMultipleContests(
    val N: Int,
    val simFuzzPct: Double,
    val ncontests: Int,
    val nballotStyles: Int,
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
    val poolPct: Double = .11,
    val configIn: AuditConfig? = null,
    val show: Boolean = false,
): ContestAuditTaskGenerator {

    val infos: Map<Int, ContestInfo>
    val config: AuditConfig
    val cards: CloseableIterable<AuditableCard>
    val auditContest: ContestUnderAudit

    init {
        //     val ncontest: Int,
        //    val nballotStyles: Int,
        //    val totalBallots: Int, // including undervotes and phantoms
        //    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
        //    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
        //    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
        //    val addStyle: Boolean = false, // add cardStyle info to cvrs and cards
        //    val ncands: Int? = null,
        //    val poolPct: Double? = null
        val test = MultiContestTestData(ncontests, nballotStyles, N, marginRange, underVotePctRange,
            phantomPctRange, addStyle = false, poolPct=poolPct)
        infos = test.contests.associate { it.id to it.info }

        config = configIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = simFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.optimalComparison, )
        )

        val (cardIter, pools) = test.makeCardPoolManifest()
        println(pools)
        this.cards = cardIter

        val manifestTabs = tabulateAuditableCards(cards.iterator(), infos)
        val Nbs = manifestTabs.mapValues { it.value.ncards }

        val contestsOA = test.contests.map { contest->
            val contestUA = ContestUnderAudit(contest, isClca = true, hasStyle = true, Nbin=Nbs[contest.id]).addStandardAssertions()
            if (show) {
                val tab = manifestTabs[contest.id]!!
                println(contestUA.show())
                println("tab $tab")
                println("extra cards= ${tab.ncards - contest.Ncast} is ${pfn((tab.ncards - contest.Ncast) / contest.Ncast.toDouble())}\n")
                assertTrue(tab.ncards >= contest.Ncast)
            }
            contestUA
        }
        addOAClcaAssortersFromMargin(contestsOA, pools, hasStyle=true)

        // use the first contest in the pool
        val useContestId = pools.first().contests().first()
        auditContest = contestsOA.find { it.id == useContestId }!!
    }

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundSingleContestAuditTask {
        // different seed each time
        val auditContests = listOf(auditContest)
        val mvrManager = MvrManagerClcaFromCards(cards, auditContests, config.simFuzzPct, Random.nextLong())
        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, auditContests, mvrManager)

        return ClcaSingleRoundSingleContestAuditTask(
            name(),
            oneaudit,
            emptyList(), // TODO could get this from mvrManager
            mapOf("mvrsFuzzPct" to simFuzzPct, "auditType" to 1.0),
            quiet = false,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}

class MvrManagerClcaFromCards(val completeCards: CloseableIterable<AuditableCard>, val contestsUA: List<ContestUnderAudit>, val simFuzzPct: Double?, seed:Long) : MvrManagerClcaIF, MvrManagerTestIF {
    private var mvrsRound: List<AuditableCard> = emptyList()
    val sortedCards: List<AuditableCard>

    init {
        // the order of the sortedCards cannot be changed once set.
        val prng = Prng(seed)
        val cards = mutableListOf<AuditableCard>()
        completeCards.iterator().forEach { cards.add(it.copy(prn=prng.next())) }
        sortedCards = cards.sortedBy { it.prn }
    }

    override fun sortedCards() = completeCards

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>>  {
        if (mvrsRound.isEmpty()) {  // wants all of em, for SingleRoundAudit
            val sampledMvrs = if (simFuzzPct == null) {
                sortedCards // use the cvrs - ie, no errors
            } else { // fuzz the cvrs
                makeFuzzedCardsFrom( contestsUA.map { it.contest.info() }, sortedCards, simFuzzPct) // TODO, undervotes=false)
            }
            return sampledMvrs.map{ it.cvr() }.zip(sortedCards.map{ it.cvr() })
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, completeCards.iterator())

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.location == cvr.location)
            require(mvr.index == cvr.index)
            require(mvr.prn== cvr.prn)
        }

        return mvrsRound.map{ it.cvr() }.zip(sampledCvrs.map{ it.cvr() })
    }

    // MvrManagerTest
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val sampledMvrs = findSamples(sampleNumbers, completeCards.iterator())
        require(sampledMvrs.size == sampleNumbers.size)

        // debugging sanity check
        var lastRN = 0L
        sampledMvrs.forEach { mvr ->
            require(mvr.prn > lastRN)
            lastRN = mvr.prn
        }

        mvrsRound = sampledMvrs
        return sampledMvrs
    }

}



