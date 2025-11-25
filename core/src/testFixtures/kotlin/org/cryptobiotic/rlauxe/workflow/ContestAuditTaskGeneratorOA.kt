package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.computeBassortValues
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.MvrCardAndPools
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestWithExtraInPool
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.collections.map
import kotlin.random.Random
import kotlin.test.assertTrue

// Generate OA contest, do full audit
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
            MvrManagerForTesting(oaCvrs, oaMvrs, config.seed))
        return ContestAuditTask(
            name(),
            oneaudit,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to mvrsFuzzPct, "auditType" to 1.0)
        )
    }
}

// Generate OA contest, do audit in a single round
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

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
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

        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA), MvrManagerForTesting(oaCvrs, oaMvrs, config.seed))
        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            oaMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}

// Generate OA contest where the pools have 2 card styles so Nb > Nc, then do audit in a single round
class OneAuditSingleRoundWithDilutedMargin(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val extraInPool: Int,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val quiet: Boolean = true,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, )
        )

        val (contestOA, mvrs, cards) =
            makeOneContestWithExtraInPool(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct,
                hasStyle = config.hasStyle,
                extraInPool = extraInPool
            )

        // different seed each time
        val manager =  MvrManagerOAFromManifest(cards, mvrs, listOf(contestOA.contest.info()), simFuzzPct= mvrsFuzzPct, Random.nextLong())
        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA), manager)
        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            mvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}

// Generate multiple OA contest, pick one and do audit in a single round
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
    val cardPoolManifest: MvrCardAndPools
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
        val test = MultiContestTestData(
            ncontests, nballotStyles, N, marginRange, underVotePctRange,
            phantomPctRange, poolPct = poolPct
        )
        infos = test.contests.associate { it.id to it.info }
        test.contestTestBuilders.forEach { println(it)}

        config = configIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = simFuzzPct,
            oaConfig = OneAuditConfig(strategy = OneAuditStrategyType.optimalComparison,)
        )

        cardPoolManifest = test.makeCardPoolManifest()
        println(cardPoolManifest.pools)

        val manifestTabs = tabulateAuditableCards(Closer(cardPoolManifest.cards.iterator()), infos)
        val Nbs = manifestTabs.mapValues { it.value.ncards }

        val extras = mutableMapOf<Int, Int>()
        val contestsOA = test.contests.map { contest ->
            val contestUA = ContestUnderAudit(
                contest,
                isClca = true,
                hasStyle = true,
                Nbin = Nbs[contest.id]
            ).addStandardAssertions()
            val tab = manifestTabs[contest.id]!!
            if (show) {
                println(contestUA.show())
                println("tab $tab")
                println("extra cards= ${tab.ncards - contest.Ncast} is ${pfn((tab.ncards - contest.Ncast) / contest.Ncast.toDouble())}\n")
                assertTrue(tab.ncards >= contest.Ncast)
            }
            extras[contest.id] = tab.ncards - contest.Ncast
            contestUA
        }
        addOAClcaAssortersFromMargin(contestsOA, cardPoolManifest.pools, hasStyle = config.hasStyle)

        // use the first contest in the pooled data
        val sex = extras.toList().sortedByDescending { it.second }
        val useContestId = sex.first().first
        auditContest = contestsOA.find { it.id == useContestId }!!
        println("*** audit contest  ${sex.first()} ${auditContest}")
        println("    contest ${auditContest.contest.show()}\n")

        val minAssorter = (auditContest.minAssertion() as ClcaAssertion).cassorter
        println("minAssorter = $minAssorter")
        val bassortValues = computeBassortValues(noerror=minAssorter.noerror(), upper=minAssorter.assorter.upperBound())
        println("bassortValues = $bassortValues\n")
    }

    override fun name() = "OneAuditSingleRoundMultipleContests"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        // different seed each time
        val auditContests = listOf(auditContest)
        val mvrManager = MvrManagerOAFromManifest(cardPoolManifest.cards, cardPoolManifest.mvrs, auditContests.map { it.contest.info() }, simFuzzPct=simFuzzPct, Random.nextLong())
        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, auditContests, mvrManager)

        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            emptyList(), // TODO could get this from mvrManager
            mapOf("mvrsFuzzPct" to simFuzzPct, "auditType" to 1.0),
            quiet = false,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}

// this assumes that the cards and mvrs correspond one-to-one
class MvrManagerOAFromManifest(cards: List<AuditableCard>, mvrs: List<Cvr>, val infos: List<ContestInfo>, val simFuzzPct: Double?, seed:Long)
    : MvrManager {

    private var mvrsRound: List<AuditableCard> = emptyList()
    val sortedCards: List<AuditableCard>
    val sortedMvrs: List<Cvr>

    init {
        require(cards.size == mvrs.size)

        val pairs = cards.zip(mvrs)
        val prng = Prng(seed)
        val cardsWithPrn = mutableListOf<Pair<AuditableCard, Cvr>>()
        pairs.forEach { cardsWithPrn.add(Pair(it.first.copy(prn=prng.next()), it.second)) }
        val sortedPairs = pairs.sortedBy { it.first.prn }
        sortedCards = sortedPairs.map { it.first }
        sortedMvrs = sortedPairs.map { it.second }
    }

    override fun sortedCards() = CloseableIterable { sortedCards.iterator() }

    override fun makeMvrCardPairsForRound(): List<Pair<CardIF, CardIF>>  {
        if (mvrsRound.isEmpty()) {  // for SingleRoundAudit.
            val sampledMvrs = if (simFuzzPct == null) {
                sortedMvrs // use the mvrs as they are - ie, no errors
            } else { // fuzz the mvrs
                makeFuzzedCvrsFrom( infos, sortedMvrs, simFuzzPct) // TODO, undervotes=false)
            }
            return sampledMvrs.map{ it }.zip(sortedCards.map{ it })
        }

        val sampleNumbers = mvrsRound.map { it.prn }
        val sampledCvrs = findSamples(sampleNumbers, Closer(sortedCards.iterator()))

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrsRound.size)
        val cvruaPairs: List<Pair<AuditableCard, AuditableCard>> = mvrsRound.zip(sampledCvrs)
        cvruaPairs.forEach { (mvr, cvr) ->
            require(mvr.location == cvr.location)
            require(mvr.index == cvr.index)
            require(mvr.prn== cvr.prn)
        }

        return mvrsRound.zip(sampledCvrs)
    }

}



