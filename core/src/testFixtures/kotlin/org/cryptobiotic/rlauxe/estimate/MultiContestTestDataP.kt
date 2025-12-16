package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.audit.CardManifest
import org.cryptobiotic.rlauxe.audit.CardsWithPopulationsToCardManifest
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.audit.makePhantomCards
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.oneaudit.calcOneAuditPoolsFromMvrs
import kotlin.Int
import kotlin.String
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val debugAdjust = false

data class MvrCardAndPops(val mvrs: List<Cvr>, val cardManifest: List<AuditableCard>, val pools: List<OneAuditPoolIF>, val styles: List<Population>)


/**
 * Creates a set of contests and cardStyles, with randomly chosen candidates and margins.
 * It can create cvrs that reflect the contests' exact votes.
 * Not for OneAudit, use makeOneContestUA()
 */
data class MultiContestTestDataP(
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // including undervotes and phantoms
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
    val addPoolId: Boolean = false, // add cardStyle info to cvrs and cards
    val ncands: Int? = null,
    val poolPct: Double? = null,  // if not null, make a pool with this pct with two ballotStyles
) {
    val poolId = if (poolPct == null) null else 1
    // generate with ballotStyles
    val ballotStylePartition: Map<Int,Int>

    val contestTestBuilders: List<ContestTestDataBuilder>
    val contests: List<Contest>
    val populations: List<Population>
    var countBallots = 0

    init {
        require(ncontest > 0)
        require(nballotStyles > 0)
        require(totalBallots > nballotStyles * ncontest) // TODO

        ballotStylePartition = if (poolPct == null) partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)
            else partitionWithPool(totalBallots, nballotStyles, poolPct).toMap()

        // between 2 and 4 candidates, margin is a random number in marginRange
        contestTestBuilders = List(ncontest) { it }.map {// id same as index
            val useNcands = ncands ?: max(Random.nextInt(5), 2)
            ContestTestDataBuilder(it, useNcands,
                marginRange.start + if (marginRange.endInclusive <= marginRange.start) 0.0 else Random.nextDouble(marginRange.endInclusive - marginRange.start),
                underVotePctRange.start + if (underVotePctRange.endInclusive <= underVotePctRange.start) 0.0 else Random.nextDouble(underVotePctRange.endInclusive - underVotePctRange.start),
                phantomPctRange.start + if (phantomPctRange.endInclusive <= phantomPctRange.start) 0.0 else Random.nextDouble(phantomPctRange.endInclusive - phantomPctRange.start),
                // TODO ChoiceFunction ??
            )
        }

        // every contest has between 1 and 4 ballot styles, randomly chosen
        val contestBstyles = mutableMapOf<ContestTestDataBuilder, Set<Int>>() // fcontest -> set(ballot style id)
        contestTestBuilders.forEach{ fcontest ->
            val nbs = min(nballotStyles, 1 + Random.nextInt(4))
            val bset = mutableSetOf<Int>() // the ballot style idx, 0 based
            while (bset.size < nbs) { // randomly choose nbs ballot styles
                bset.add(Random.nextInt(nballotStyles))
            }
            fcontest.ballotStyles = bset
            contestBstyles[fcontest] = bset
        }

        // partition totalBallots amongst the ballotStyles
        populations = List(nballotStyles) { it }.map { idx ->
            var contestsForThisBs = contestBstyles.filter{ (fc, bset) -> bset.contains( idx ) }
                .map { (fc, _) -> fc }

            // every ballot style needs at least one contest. just make it first contest I guess
            if (contestsForThisBs.isEmpty()) contestsForThisBs = listOf(contestTestBuilders.first())
            val contestIds = contestsForThisBs.map { it.info.id }
            val ncards = ballotStylePartition[idx]!!
            countBallots += ncards

            Population("style$idx", idx, contestIds.toIntArray(), false).setNcards(ncards)
        }
        require(countBallots == totalBallots)
        countCards()
        contests = contestTestBuilders.map { it.makeContest() }
    }

    // set contest.ncards
    fun countCards() {
        populations.forEach { bs ->
            bs.contests().forEach { contestId ->
                val contest = contestTestBuilders.find { it.info.id == contestId }!!
                contest.ncards += bs.ncards
            }
        }
    }

    // mvrs, cardPool, cardStyle
    fun makeCardPoolManifest(): MvrCardAndPops {
        // these are the mvr truth
        val mvrs = makeCardsFromContests()

        // the union of the first two styles
        val expandedContestIds = (populations[0].contests() + populations[1].contests()).toSet().sorted().toIntArray()

        val infos = contests.associate { it.id to it.info() }

        // expand the two cardStyles
        val expandedCardStyles = listOf(
            populations[0].copy(possibleContests = expandedContestIds),
            populations[1].copy(possibleContests = expandedContestIds),
        )

        // here we put the pool data into a single pool, and combine their contestIds, to get a diluted margin for testing
        val cardManifest = mutableListOf<AuditableCard>()
        val converter = CardsWithPopulationsToCardManifest(
            type = AuditType.ONEAUDIT,
            cards = Closer(mvrs.iterator()),
            expandedCardStyles,
        )
        converter.forEach { cardManifest.add(it) }

        // we need to populate the pool tab with the votes
        val pool = OneAuditPoolFromCvrs("pool", 1, false, infos)
        expandedContestIds.forEach { id -> pool.contestTabs[id] = ContestTabulation(infos[id]!!) }
        mvrs.forEach { card ->
            if (card.poolId == 1) pool.accumulateVotes(card.cvr())
        }

        // TODO kludge
        val mvrsAsCvr = mvrs.map { it.cvr() }

        return MvrCardAndPops(mvrsAsCvr, cardManifest, listOf(pool), populations)
    }

    fun makeCardLocationManifest(): CardManifest {
        val cards = CloseableIterable { makeCardsFromContests().iterator() }
        return CardManifest(cards, populations)
    }

    override fun toString() = buildString {
        append("ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        appendLine(" marginRange=$marginRange underVotePct=$underVotePctRange phantomPct=$phantomPctRange")
        contestTestBuilders.forEach { fcontest ->
            append("  $fcontest")
            val bs4id = populations.filter{ it.hasContest(fcontest.contestId) }.map{ it.name }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        populations.forEach { appendLine("  $it") }
    }

    // TODO not positive I have the poolId correct
    fun makeCvrsFromContests(): List<Cvr> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(contestTestBuilders.map { it.info })
        val result = mutableListOf<Cvr>()
        populations.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                // add regular Cvrs including undervotes
                val poolId = if ((poolPct != null) && cardStyle.id  < 2) 1 else null
                result.add(makeCvr(cvrbs, fcontests, poolId = poolId)) // TODO always just add ??
            }
        }

        val phantoms = makePhantomCvrs(contests)
        return result + phantoms
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTestDataBuilder>, poolId: Int?): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build(poolId)
    }

    fun makeCardsFromContests(startCvrId : Int = 0): List<AuditableCard> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<AuditableCard>()
        populations.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                val poolId = if ((poolPct != null) && cardStyle.id  < 2) 1 else null
                result.add(makeCard(nextCardId++, fcontests, poolId = poolId, cardStyle=cardStyle.name))
            }
        }

        result.addAll(makePhantomCards(contests, startIdx = result.size))
        result.shuffle(Random)
        return result
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestTestDataBuilder>, poolId: Int?, cardStyle: String?): AuditableCard {
        val cardBuilder = CardBuilder("card${nextCardId}", nextCardId, poolId, cardStyle)
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cardBuilder) }
        return cardBuilder.build(poolId)
    }
}

fun makeOneAuditTestContestsP(
    hasStyle: Boolean,
    infos: Map<Int, ContestInfo>, // all the contests in the pools
    contestsToAudit: List<Contest>, // the contests you want to audit
    cardStyles: List<PopulationIF>,
    cardManifest: List<AuditableCard>,
    mvrs: List<Cvr>, // this must be just for tests
    debug: Boolean = false,
): Pair<List<ContestUnderAudit>, List<OneAuditPoolIF>> {

    // The Nbs come from the cards
    val manifestTabs = tabulateAuditableCards(Closer(cardManifest.iterator()), infos)
    val Nbs = manifestTabs.mapValues { it.value.ncards }

    val contestsUA = contestsToAudit.map {
        val cua = ContestUnderAudit(it, true, hasStyle = hasStyle, NpopIn=Nbs[it.id])
        if (it is DHondtContest) {
            cua.addAssertionsFromAssorters(it.assorters)
        } else {
            cua.addStandardAssertions()
        }
    }
    if (debug) println(showTabs("manifestTabs", manifestTabs))

    // create from cardStyles and populate the pool counts from the mvrs
    val poolsFromCvrs = calcOneAuditPoolsFromMvrs(infos, cardStyles, mvrs)

    // The OA assort averages come from the mvrs
    addOAClcaAssortersFromMargin(contestsUA, poolsFromCvrs, hasStyle=true)

    // poolsFromCvrs record the complete pool contests,
    return Pair(contestsUA, poolsFromCvrs)
}