package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CountyPoolsIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.nfz
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private val debugNvotes = false
private val debugUndervotes = true

// obsolete
class MakeCountyPoolsOld(
    val corlaContestBuilders: List<CorlaContestBuilder>,
    val coloradoInput: ColoradoInput
) {
    val builders = corlaContestBuilders.associateBy { it.info.name }
    val infos = corlaContestBuilders.associate { it.info.id to it.info }

    val countyPoolsMS: List<CountyPoolsWithStyles>

    init {
        val infosByName = corlaContestBuilders.associate { it.info.name to it.info }

        val distributeNc: Map<String, Map<String, Int>> = distributeNc() // county -> contest -> Nc for that contest in that county

        val contestTabByCounty: Map<String, CountyContestTabs> = coloradoInput.countyContestTabs.associateBy { it.countyName }

        val mvrStylesMap: Map<String, CountyStylesFromMvrs> = coloradoInput.mvrStyles.associateBy { it.countyName }

        // the mvr styles are not complete. This seriously sucks.
        // pick out the contests that dont have styles that contain it
        val missingContestsByCounty = mutableMapOf<String, MutableList<CountyContestTab>>() // countyName -> contestTab
        contestTabByCounty.map { (countyName, countyContest) ->
            val mvrStyles: CountyStylesFromMvrs = mvrStylesMap[countyName]!!
            countyContest.contests.forEach { (contestName, contestTab) ->
                val stylesForContest: List<MvrStyle> =
                    mvrStyles.styles.values.filter { it: MvrStyle -> it.contests.contains(contestName) }
                if (stylesForContest.isEmpty()) {
                    val missingStyles = missingContestsByCounty.getOrPut(countyName) { mutableListOf() }
                    missingStyles.add(contestTab)
                }
            }
        }

        // missingPools for each county
        val missingPools: Map<String, CountyPoolFromStyle> = makeMissingPools(missingContestsByCounty)

        val countyPools: List<CountyPoolsBuilderOld> = contestTabByCounty.map { (countyName, countyContest) ->
            CountyPoolsBuilderOld(
                countyName, countyContest, mvrStylesMap[countyName]!!,
                missingPools[countyName], distributeNc[countyName]!!, infosByName)
        }

        // all the pools TODO seems wrong
        val poolsWithMvrStyles = countyPools.map { it.makePools() }.flatten()
        val pools = poolsWithMvrStyles + missingPools.values.toList()

        corlaContestBuilders.forEach {
            // set contest total cards as sum over pools
            it.setTotalCardsFromPools(pools)
            it.info.metadata["CORLApoolTotalCards"] = it.poolTotalCards.toString()
            it.info.metadata["CORLApoolTotalVotes"] = it.poolTotalVotes.toString()
        }

        // how close are we to desired Nvotes?
        val nvotesDiffByContestBefore = mutableMapOf<CorlaContestBuilder, Int>()
        var totalDiff = 0
        corlaContestBuilders.forEach {
            nvotesDiffByContestBefore[it] = it.totalVotesAllCounties - it.poolTotalVotes
            totalDiff += abs(it.totalVotesAllCounties - it.poolTotalVotes)
        }
        println("total diff nvotes = $totalDiff")
        if (debugNvotes) {
            corlaContestBuilders.forEach {
                val before = nvotesDiffByContestBefore[it]
                println("  contest ${it.info.id} nvotes (expect - pools) = $before")
            }
        }

        /* TODO by County
        // adjust so contest 0 undervotes agree with the sum of pool undervotes.
        // needed since we dont know number of cards in precincts or missing
        // hasExactContests should be true.
        // TODO dont we have to adjust Nc to agree with pools?
        val contest0 = corlaContestBuilders.find { it.info.name == "Presidential Electors" }!!
        distributeExpectedOvervotes(contest0, pools)

        if (debugUndervotes) {
            // estimate undervotes based on each precinct having a single ballot style
            val undervotesByContest = mutableMapOf<CorlaContestBuilder, Int>() // contestId ->
            corlaContestBuilders.forEach {
                undervotesByContest[it] = it.expectedPoolNCards() - it.poolTotalCards
            }

            undervotesByContest.forEach { (cb, before) ->
                val needAfter = cb.expectedPoolNCards() - cb.poolTotalCards
                println("  id=${cb.contestId} before adj=$before after adj=$needAfter ")
            }
        }

        val nvotesDiffByContestAfter = mutableMapOf<CorlaContestBuilder, Int>() // contestId ->
        var totalDiffAfter = 0
        corlaContestBuilders.forEach {
            nvotesDiffByContestAfter[it] = it.totalVotesAllCounties - it.poolTotalVotes
            totalDiffAfter += abs(it.totalVotesAllCounties - it.poolTotalVotes)
        }
        println("total diff nvotes = $totalDiff after adjustments")

        if (debugNvotes) {
            corlaContestBuilders.forEach {
                val before = nvotesDiffByContestAfter[it]
                val after = nvotesDiffByContestAfter[it]
                println("  contest ${it.info.id} before=$before after=$after")
            }
        } */
        // TODO can we just use CountyPools directly instead of this transform?

        var countyPoolId = 1
        this.countyPoolsMS = countyPools.map { countyPool ->
            val countyContestNc: Map<String, Int> = distributeNc[countyPool.countyName]!!  // contestName -> contest.Nc

            // CountyContestTabs from tabulateCounty.csv
            val tabs = countyPool.cct.contests.map { (name, countyContestTab) ->
                val info = infosByName[name]!!
                if (info.id == 180)
                    print("here")
                val ncards = countyContestNc[name] ?: 0
                countyContestTab.makeContestTabulation(info, ncards)
            }

            // TODO im not sure is missingStyle got assigned ncard ??
            val countyStyles: List<CountyPoolFromStyle> = if (countyPool.missingPool == null) countyPool.makePools() else
                countyPool.makePools() + countyPool.missingPool
            val totalCards = countyStyles.sumOf { it.ncards() }

            CountyPoolsWithStyles(countyPool.countyName, countyPoolId++,
                contestTabs = tabs, styles = countyStyles, totalCards = totalCards)
        }
    }

    // for each contest, distribte Nc to the counties it is in, proportional to votesInCounty / totalVotes
    fun distributeNc(): Map<String, Map<String, Int>> { // county -> contest -> Nc
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc
        coloradoInput.contestTabsByCounty.values.forEach { contestTabByCounty ->
            val contestName = contestTabByCounty.contestName
            val builder = builders[contestName]
            if (builder == null)
                throw RuntimeException()
            val contestTotalVotes = contestTabByCounty.totalVotesAllCounties

            val counties = contestTabByCounty.counties()
            counties.forEach { name ->
                val countyContest = countyNc.getOrPut(name) { mutableMapOf() }
                val countyVotes = contestTabByCounty.countyVotes(name)
                val fac = countyVotes / contestTotalVotes.toDouble()
                countyContest[contestName] = (builder.Nc * fac).roundToInt()
            }
        }

        //// consistency check
        // sum over counties to get the contest sum
        val contestSum = mutableMapOf<String, Int>()
        countyNc.forEach { (countyName, countyVotes) ->
            countyVotes.forEach { contestName, contestVotes ->
                val contestAccum = contestSum.getOrDefault(contestName, 0)
                contestSum[contestName] = contestAccum + contestVotes
            }
        }
        coloradoInput.contestTabsByCounty.values.forEach { contestTab ->
            val contestName = contestTab.contestName
            val sum = contestSum[contestName]!!
            val builder = builders[contestName]!!
            val contestNc = builder.Nc
            //if (abs(contestNc-sum) > 5)
            //    logger.warn{"makeCardPoolsFromCountyStyles has (contestNc-sum) ${abs(contestNc-sum)} > 5" }
        }
        return countyNc
    }

    fun makeMissingPools(missingContestsByCounty: Map<String, List<CountyContestTab>>): Map<String, CountyPoolFromStyle> {
        return missingContestsByCounty.map { (countyName, missingContests) -> makeMissingPool(countyName, missingContests) }
            .associateBy { it.countyName }
    }

    // the simplest thing to do is to munge all missing contests into a single style.
    // TODO something better ?
    fun makeMissingPool(countyName: String, missingContests: List<CountyContestTab>): CountyPoolFromStyle {

        val votesForStyle = mutableMapOf<Int, ContestTabulation>() // all contests, this style
        missingContests.forEach { contestTab ->
            val builder = builders[contestTab.contestName]!!
            val info = builder.info
            val votes = mutableMapOf<Int, Int>() // this contest
            contestTab.choices.forEach { (choiceName, choiceVote) ->
                val candId = info.candidateNames[choiceName]!!
                votes[candId] = choiceVote
            }
            votesForStyle[info.id] = ContestTabulation(info, votes, builder.Nc)
        }

        CountyPoolsBuilderOld.nextPoolId++
        return CountyPoolFromStyle(
            countyName, countyName, CountyPoolsBuilderOld.nextPoolId, hasExactContests = true,
            voteTotals = votesForStyle, infos
        )
    }
}

// obsolete, but used by PoolsforAllCounties
data class CountyPoolsBuilderOld(
    val countyName: String,
    val cct: CountyContestTabs, // the votes subtotal for each contest in the county
    val mvrStyles: CountyStylesFromMvrs, // Set<contestId> and reletive count within county
    val missingPool: CountyPoolFromStyle?, // all the contests that werent in an mvrStyle
    val contestNc: Map<String, Int>, // contest name -> contest Nc for the county
    val infos: Map<String, ContestInfo> // contest name -> ContestInfo
) {
    val missingNcards = missingPool?.ncards() ?: 0
    val adjContestNc = contestNc.mapValues { it.value - missingNcards}

    // also from the styles
    // divide up the votes among styles to create a pool, from which cvrs can be synthesized
    fun makePools(): List<CountyPoolFromStyle>  {
        val total = mvrStyles.styles.values.sumOf { it.cardCount }
        if (total != mvrStyles.cardCount)
            logger.warn { "total != countyStyles.cardCount"}

        val contestPcts = mutableMapOf<String, Double>()

        val pools = mvrStyles.styles.values.map { style ->
            val votesForStyle = mutableMapOf<Int, ContestTabulation>() // all contests, this style

            style.contests.forEach { contestName: String ->
                // the denominator is sum of cardCounts of Style's that contain this contest; could do once above
                val totalCardsForContest = mvrStyles.styles.values.filter{ it.contests.contains(contestName) }.sumOf{ it.cardCount }
                val stylePct = style.cardCount / totalCardsForContest.toDouble()
                val contestPct = contestPcts.getOrDefault(contestName, 0.0)
                contestPcts[contestName] = contestPct + stylePct

                val info = infos[contestName]!!
                val votes = mutableMapOf<Int, Int>() // this contest
                val contestTab = cct.contests[contestName]!!
                contestTab.choices.forEach { (choiceName, choiceVote) ->
                    val candId = info.candidateNames[choiceName]
                    if (candId != null) { // might be write in
                        votes[candId] = (stylePct * choiceVote).roundToInt() // scale by stylePct
                    }
                }
                // needs to be adjusted across the styles in proportion to how many cards used it
                val Nc = adjContestNc[contestName]!!  // total Nc for this contest over all styles
                val ncards = (stylePct * Nc).roundToInt() // scale by stylePct

                votesForStyle[info.id] = ContestTabulation(info, votes, ncards)
            }

            nextPoolId++
            CountyPoolFromStyle( countyName,
                "${countyName}-${nfz(style.id,2)}", nextPoolId,
                hasExactContests = true, voteTotals=votesForStyle, infos.mapKeys { it.value.id } )
        }

        // check
        contestPcts.forEach { contestName, pct ->
            if (!doubleIsClose(pct, 1.0))
                logger.warn { "$contestName sum of style pctTotal ${pct} != 1.0"}
        }

        return pools
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyPools")
        var nextPoolId = 0
    }
}

// a pool of cards based on a card style
// we dont know ncards - initial estimate from voteTotals, then can adjust cards
data class CountyPoolFromStyle(
    val countyName: String,
    override val poolName: String,
    override val poolId: Int,
    val hasExactContests: Boolean,
    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests and candidates with no votes
    val infos: Map<Int, ContestInfo>,
): CardPoolIF, StyleIF {
    val minCardsNeeded = mutableMapOf<Int, Int>()
    val maxMinCardsNeeded: Int
    var adjustCards = 0 // adjusted number of cards, using distributeExpectedOvervotes() on one or more contests

    init {
        // contestId -> minCardsNeeded
        voteTotals.forEach { (contestId, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[contestId]!!
            // based on the contest's votes, you need at least this many cards for this contest
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        // you need at least this many cards for this pool
        val fromMaxMin = minCardsNeeded.values.max()
        val fromTabs = voteTotals.values.maxOf { it.nvotes() }
        maxMinCardsNeeded = fromMaxMin
    }

    override fun name() = poolName
    override fun id() = poolId
    override fun hasExactContests() = hasExactContests

    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun possibleContests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun ncards() = (maxMinCardsNeeded + adjustCards)

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    override fun contestTab(contestId: Int) = voteTotals[contestId]

    //        val result = if (hasExactContests) {
    //            // if hasExactContests, then missing has to be zero
    //            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
    //            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
    //            val undervotes = npop * voteForN - voteSum
    //            Vunder(contestId, poolId, voteCounts, undervotes, 0, voteForN)
    //        } else {
    //            val missing = npop - (this.undervotes + voteSum) / voteForN
    //            Vunder(contestId, poolId, voteCounts, this.undervotes, missing, voteForN)
    //        }

    // TODO how to distinguish between undervotes and missing ?? You need independent setting for pool ncards
    // this assumes missing = 0; but then should set SingleBallotStyle = true ?
    fun undervoteForContest(contestId: Int): Int {
        val contestTab = voteTotals[contestId] ?: return 0
        val voteSum = contestTab.nvotes()
        val info = infos[contestId]!!
        return ncards() * info.voteForN - voteSum
    }

    // TODO IRV allowed ?
    override fun votesAndUndervotes(contestId: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val contestTab = voteTotals[contestId]!!

        // TODO why not use contestTab.votesAndUndervotes() ??

        val voteCounts = contestTab.votes.map { Pair(intArrayOf(it.key), it.value) }
        val voteSum = contestTab.votes.values.sum()

        return if (hasExactContests) {
            // if hasExactContests, then missing has to be zero
            // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
            val undervotes = ncards() * contestTab.voteForN - voteSum
            Vunder(contestId, poolId, voteCounts, undervotes, 0, contestTab.voteForN)
        } else {
            val missing = ncards() - (poolUndervotes + voteSum) / contestTab.voteForN
            Vunder(contestId, poolId, voteCounts, poolUndervotes, missing, contestTab.voteForN)
        }
    }

    override fun toString(): String {
        return "CountyPoolFromStyles(poolName='$poolName', poolId=$poolId, #contests=${voteTotals.size}, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CountyPoolFromStyle) return false

        if (poolId != other.poolId) return false
        if (hasExactContests != other.hasExactContests) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (voteTotals != other.voteTotals) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + voteTotals.hashCode()
        return result
    }
}

// CountyPool: pool with multiple CardStyles
data class CountyPoolsWithStyles (
    override val countyName: String,
    override val countyPoolId: Int,
    override val contestTabs: List<ContestTabulation>,  // contestId -> ContestTabulation
    override val totalCards: Int,
    override val styles: List<CardPoolIF>,
): CountyPoolsIF {
    override fun toString() = buildString {
        appendLine("CountyPools(countyName='$countyName', countyPoolId=$countyPoolId, totalCards=$totalCards")
        styles.forEach{ appendLine("cardStyle:  $it")}
        contestTabs.forEach{ appendLine("  $it")}
    }
}

// where do we get ncards per county per contest? all we "know" is total contest Nc across counties
// look across all counties that have that contest and divide Nc in proportion to countyContest.totalVotes

// each countyStyle generates a Pool
// we have county styles and subtotals, which get distributed to the various county styles in (rough) proportion to their cardCount.
// as usual, we dont know the undervotes, so we will distribute that also in proportion

// duplicate in BoulderContestBuilder
// fun distributeExpectedOvervotes(refContest: OneAuditContestBuilderIF, cardPools: List<OneAuditPoolFromBallotStyle>) {

// we dont know how many cards are in the pool.
// so adjust the number of cards in the pools so that the sum of pool.undervotes agrees with the refContest
// this only works if the pool has a single style.
fun distributeExpectedOvervotes(refContest: CorlaContestBuilder, cardPools: List<CountyPoolFromStyle>) {
    val contestId = refContest.contestId
    val poolCards = refContest.poolTotalCards
    val expectedCards = refContest.expectedPoolNCards()
    val need = expectedCards - poolCards
    println("${refContest.contestId} expectedCards=$expectedCards poolCards=$poolCards need = $need")

    var used = 0
    val allocDiffPool = mutableMapOf<Int, Int>() // poolId -> adjusted undervotes
    cardPools.forEach { pool ->
        val minCardsNeeded = pool.minCardsNeeded[contestId]
        if (minCardsNeeded != null) {
            // distribute cards as proportion of totalVotes
            val allocDiff = roundToClosest(need * (pool.maxMinCardsNeeded / poolCards.toDouble()))
            used += allocDiff
            allocDiffPool[pool.poolId] = allocDiff
        }
    }

    // adjust random pools until used == diff
    if (used < need) {
        val keys = allocDiffPool.keys.toList()
        while (used < need) {
            val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
            val prev = allocDiffPool[chooseOne]!!
            allocDiffPool[chooseOne] = prev + 1  // TODO one at a time !!
            used++
        }
    }
    if (used > need) {
        val keys = allocDiffPool.keys.toList()
        while (used > need) {
            val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
            val prev = allocDiffPool[chooseOne]!!
            if (prev > 0) {
                allocDiffPool[chooseOne] = prev - 1
                used--
            }
        }
    }

    // check used == diff
    if (allocDiffPool.values.sum()!= need) {
        println("distributeExpectedOvervotes: ${allocDiffPool.values.sum()} should equal == $need")
    }

    // adjust
    val cardPoolMap = cardPools.associateBy { it.poolId }
    allocDiffPool.forEach { (poolId, adjust) ->
        cardPoolMap[poolId]!!.adjustCards(adjust, contestId)
    }
}
