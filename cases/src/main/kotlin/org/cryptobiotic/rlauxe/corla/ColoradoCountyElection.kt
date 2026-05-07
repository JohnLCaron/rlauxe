package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger("ColoradoOneAudit")

private val debugUndervotes = false
private val debugNvotes = false
private var nextPoolId = 0

open class ColoradoCountyElection (
    // val auditType: AuditType,
    // val auditdir: String,
) {
    val corlaInput = Colorado2024Input
    val corlaContestBuilders = makeContestBuilders(corlaInput) // 181
    val cardPools: List<CountyPoolFromStyle>
    val contests: List<ContestIF>

    init {
        nextPoolId = 0
        cardPools = makeCardPoolsFromCountyStyles(corlaInput)
        contests = corlaContestBuilders.map { it.makeContest() }
    }

    private fun makeContestBuilders(
        corlaInput: Colorado2024Input,
    ): List<CorlaContestBuilder> {

        val contestsMap = corlaInput.contestsByCounty.associateBy { it.contestName }
        val resultsContestMap = corlaInput.resultsContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }
        val roundContestMap = corlaInput.roundContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }
        val xmlDetailMap = corlaInput.electionDetailXml.contests.associateBy { mutatisMutandi(contestNameCleanup(it.text)) }

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonicalContestMap one drives the boat
        contestsMap.forEach{ (orgContestName, contestByCounty) ->
            val contestName = mutatisMutandi(contestNameCleanup(orgContestName))
            // read 725 contests from src/test/data/corla/2024audit/round1/ResultsReportSummary.csv
            val resultsContest = resultsContestMap[contestName]
            if (resultsContest == null) {
                logger.warn{"*** Cant find resultsContest for $contestByCounty" }
            } else {
                // read 725 contests from src/test/data/corla/2024audit/round1/contest.csv
                // need Nc = contestRound.contestBallotCardCount
                val roundContest = roundContestMap[contestName]
                if (roundContest == null) {
                    logger.warn{ "*** Cant find CorlaContestRoundCsv $contestName" }
                }

                // detail.xml only has 295 out 725 contests, so dont depend on it.
                val corlaXmlContest = xmlDetailMap[contestName]

                val candidateNames =
                    contestByCounty.choices.values.mapIndexed { idx, choice -> Pair(candidateNameCleanup(choice.choiceName), idx) }.toMap()
                val voteForN = corlaXmlContest?.voteFor ?: roundContest?.nwinners ?: 1 // TODO

                val info = ContestInfo(
                    contestName,
                    contestBuilders.size + 1,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY, // TODO
                    voteForN
                )
                if (roundContest != null) info.metadata["CORLAsample"] = roundContest.optimisticSamplesToAudit.toString()
                info.metadata["CORLArisk"] = resultsContest.risk.toString()
                info.metadata["CORLAmargin"] = resultsContest.margin.toString()
                info.metadata["CORLAcounties"] = contestByCounty.counties().toList().toString()

                val contest = CorlaContestBuilder(
                    orgContestName,
                    info,
                    contestByCounty,
                    roundContest,
                    corlaXmlContest,
                )
                contestBuilders.add(contest)
            }
        }

        println("number of contestBuilders = ${contestBuilders.size}")

        return contestBuilders
    }

    private fun makeCardPoolsFromCountyStyles(corlaInput: Colorado2024Input): List<CountyPoolFromStyle> {
        val infos = corlaContestBuilders.associate { it.info.name to it.info }
        val builders = corlaContestBuilders.associateBy { it.info.name }

        val countyNc = distributeNc(builders) // county -> contest -> Nc for that contest in that county

        // convert from Contest -> County to County -> Contest
        val contestTabByCounty: Map<String, CountyContestTab> = convertToCountyContestTabs(corlaInput.contestsByCounty).associateBy { it.countyName }
        val stylesByCounty: Map<String, CountyStyles> =  corlaInput.countyStyles.associateBy { it.countyName }

        // merge the styles into the CountyContestTabs, pick out the contestTabs that dont have styles
        val tabsMissingStyles = mutableMapOf<String, MutableList<ContestTab>>()
        contestTabByCounty.map { (countyName, countyContest) ->
            val countyStyles: CountyStyles = stylesByCounty[countyName]!!
            countyContest.contests.forEach { (contestName, contestTab) ->
                val stylesForContest: List<Style> = countyStyles.styles.values.filter { it: Style -> it.contests.contains(contestName) }
                if (stylesForContest.isNotEmpty())
                    contestTab.stylesForContest.addAll(stylesForContest)
                else {
                    val missingStyles = tabsMissingStyles.getOrPut(countyName) { mutableListOf() }
                    missingStyles.add(contestTab)
                }
            }
        }

        val missingPools: Map<String, CountyPoolFromStyle> = makeMissingPools(tabsMissingStyles)

        val countyPools: List<CountyPools> = contestTabByCounty.map { (countyName, countyContest) ->
            CountyPools(countyName, stylesByCounty[countyName]!!, countyContest,
                missingPools[countyName], countyNc[countyName]!!, infos)
        }

        val poolsWithStyles = countyPools.map { it.makePools() }.flatten()
        val pools = poolsWithStyles + missingPools.values.toList()

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

        /* adjustments disabled
        // adjust so contest 0 undervotes agree with the sum of pool undervotes.
        // needed since we dont know number of cards in precincts or missing
        // hasExactContests should be true.
        // TODO dont we have to adjust Nc to agree with pools?
        //val contest0 = corlaContestBuilders.find { it.info.name == "Presidential Electors" }!!
        //distributeExpectedOvervotes(contest0, pools)

        if (debugUndervotes) {
            // estimate undervotes based on each precinct having a single ballot style
            val undervotesByContest = mutableMapOf<CorlaContestBuilder, Int>() // contestId ->
            corlaContestBuilders.forEach {
                undervotesByContest[it] = it.expectedPoolNCards() - it.poolTotalCards()
            }

            undervotesByContest.forEach { (cb, before) ->
                val needAfter = cb.expectedPoolNCards() - cb.poolTotalCards()
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

        return pools
    }

    //// from roundContest, we have total ncards, which we use as Nc:
    //City of Littleton Ballot Issue 3B,opportunistic_benefits,in_progress,1,946840,28308,"""Yes/For""",381,0.03000000,0,0,0,0,0,0,0,1.03905000,0,18110,18110
    //
    //// these are in countyTabs; we divide Nc in proportion to nvotes in county
    //Arapahoe,City of Littleton Ballot Issue 3B,Yes/For,12533
    //Arapahoe,City of Littleton Ballot Issue 3B,No/Against,12041
    //
    //Douglas,City of Littleton Ballot Issue 3B,No/Against,178
    //Douglas,City of Littleton Ballot Issue 3B,Yes/For,121
    //
    //Jefferson,City of Littleton Ballot Issue 3B,No/Against,800
    //Jefferson,City of Littleton Ballot Issue 3B,Yes/For,746
    //

    fun distributeNc(builders: Map<String, CorlaContestBuilder>): Map<String, Map<String, Int>> { // county -> contest -> Nc

        // for each contest, distribte Nc to the counties it is in, proportional to votesInCounty / totalVotes
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc
        corlaInput.contestsByCounty.forEach { contestTabByCounty ->
            val contestName = mutatisMutandi(contestNameCleanup(contestTabByCounty.contestName))
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
        countyNc.forEach { (countyName, countyVotes ) ->
            countyVotes.forEach { contestName, contestVotes ->
                val contestAccum = contestSum.getOrDefault(contestName, 0)
                contestSum[contestName] = contestAccum + contestVotes
            }
        }
        corlaInput.contestsByCounty.forEach { contestTab ->
            val contestName = mutatisMutandi(contestNameCleanup(contestTab.contestName))
            val sum = contestSum[contestName]!!
            val builder = builders[contestName]!!
            val contestNc = builder.Nc
            if (abs(contestNc-sum) > 5)
                logger.warn{"makeCardPoolsFromCountyStyles has (contestNc-sum) ${abs(contestNc-sum)} > 5" }
        }
        return countyNc
    }

    // problem is, we dont have styles for all of them
    //
    // Arapahoe,City of Littleton Ballot Issue 3B,Yes/For,12533
    // Arapahoe,City of Littleton Ballot Issue 3B,No/Against,12041
    //
    // Douglas,City of Littleton Ballot Issue 3B,No/Against,178
    // Douglas,City of Littleton Ballot Issue 3B,Yes/For,121
    //
    // Jefferson,City of Littleton Ballot Issue 3B,No/Against,800
    // Jefferson,City of Littleton Ballot Issue 3B,Yes/For,746

    // ccts CountyName -> CountyContestTab

    fun makeMissingPools(tabsMissingStyles: Map<String, List<ContestTab>>) : Map<String, CountyPoolFromStyle> {
        return tabsMissingStyles.map { (countyName, countestTabs) -> makeMissingPool(countyName, countestTabs) }.associateBy { it.countyName }
    }

    fun makeMissingPool(countyName: String, tabsMissingStyles: List<ContestTab>) : CountyPoolFromStyle {
        /* val problemName = "City of Littleton Ballot Issue 3B"
        val buildersByName = corlaContestBuilders.associate { it.info.name to it }
        val problem = buildersByName[problemName]

        val hasContests = mutableSetOf<String>()
        corlaInput.countyStyles.map { countyStyle ->
            countyStyle.styles.keys.forEach { keys: Set<String> ->
                keys.forEach {
                    val contestName = mutatisMutandi(contestNameCleanup(it))
                    hasContests.add( contestName )
                    if (contestName == problemName)
                        println("style in county ${countyStyle.countyName}")
                }
            }
        }
        // has 2 hits in Arapahoe, none otherwise
        val noStyleBuilders = mutableListOf<CorlaContestBuilder>()
        corlaContestBuilders.forEach {
            if (!hasContests.contains(it.info.name)) {
                noStyleBuilders.add(it)
            }
        }
        println("contests with no pooled data = ${noStyleBuilders.size} out of ${corlaContestBuilders.size}")
*/
        val buildersByName = corlaContestBuilders.associate { it.info.name to it }
        val contestBuilders = tabsMissingStyles.map {
            val cleanedName = mutatisMutandi(contestNameCleanup(it.contestName))
            buildersByName[cleanedName]!!
        } // clean
        val infos = contestBuilders.associate { it.info.id to it.info }

        val votesForStyle = mutableMapOf<Int, ContestTabulation>() // all contests, this style
        tabsMissingStyles.forEach { contestTab ->
            val cleanedName = mutatisMutandi(contestNameCleanup(contestTab.contestName))
            val builder = buildersByName[cleanedName]!!
            val info = builder.info
            val votes = mutableMapOf<Int, Int>() // this contest
            contestTab.choices.forEach { (choiceName, choiceVote) ->
                val candId = info.candidateNames[candidateNameCleanup(choiceName)]!!
                votes[candId] = choiceVote
            }
            votesForStyle[info.id] = ContestTabulation(info, votes, builder.Nc)
        }

        // data class CountyPoolFromStyles(
        //    override val poolName: String,
        //    override val poolId: Int,
        //    val hasExactContests: Boolean,
        //    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests and candidates with no votes
        //    val infos: Map<Int, ContestInfo>, // all contests
        //    val ncards: Int? = null
        //)
        return CountyPoolFromStyle(countyName, countyName, nextPoolId++, hasExactContests = true,
            voteTotals = votesForStyle, infos )
    }

    // where do we get ncards per county per contest? all we "know" total contest Nc across counties
    // look across all counties that have that contest and divide Nc in proportion to countyContest.totalVotes

    // each countyStyle generates a Pool
    // we have county styles and subtotals, which get distributed to the various county styles in (rough) proportion to their cardCount.
    // as usual, we dont know the undervotes, so we will distribute that also in proportion
    private data class CountyPools(
        val countyName: String,
        val countyStyles: CountyStyles, // Set<contestId> and reletive count within county
        val cct: CountyContestTab, // the votes subtotal for each contest in the county
        val missingPool: CountyPoolFromStyle?,
        val contestNc: Map<String, Int>, // contest name -> contest Nc for the county
        val infos: Map<String, ContestInfo> // contest name -> contest id
    ) {
        val missingNcards = missingPool?.ncards() ?: 0
        val adjContestNc = contestNc.mapValues { it.value - missingNcards}

        // divide up the votes among styles to create a pool, from which cvrs can be synthesized
        fun makePools(): List<CountyPoolFromStyle>  {
            val total = countyStyles.styles.values.sumOf { it.cardCount }
            if (total != countyStyles.cardCount)
                logger.warn { "total != countyStyles.cardCount"}

            val contestPcts = mutableMapOf<String, Double>()

            val pools = countyStyles.styles.values.map { style ->

                val votesForStyle = mutableMapOf<Int, ContestTabulation>() // all contests, this style

                style.contests.forEach { contestName: String ->
                    val cleanedName = mutatisMutandi(contestNameCleanup(contestName))
                    // the denominator is sum of cardCounts of Style's that contain this contest; could do once above
                    val totalCardsForContest = countyStyles.styles.values.filter{ it.contests.contains(contestName) }.sumOf{ it.cardCount }
                    val stylePct = style.cardCount / totalCardsForContest.toDouble()
                    val contestPct = contestPcts.getOrDefault(contestName, 0.0)
                    contestPcts[contestName] = contestPct + stylePct

                    val info = infos[cleanedName]!!
                    val votes = mutableMapOf<Int, Int>() // this contest
                    val contestTab = cct.contests[contestName]!!
                    contestTab.choices.forEach { (choiceName, choiceVote) ->
                        val candId = info.candidateNames[candidateNameCleanup(choiceName)]!!
                        votes[candId] = (stylePct * choiceVote).roundToInt() // scale by stylePct
                    }
                    // needs to be ajusted across the styles in proportion to how many cards used it
                    val Nc = adjContestNc[cleanedName]!!  // total Nc for this contest over all styles
                    val ncards = (stylePct * Nc).roundToInt() // scale by stylePct

                    votesForStyle[info.id] = ContestTabulation(info, votes, ncards)
                }
                nextPoolId++

                CountyPoolFromStyle( countyName,
                    "${countyName}-${nfz(style.id,2)}", nextPoolId,
                    hasExactContests = true, voteTotals=votesForStyle, infos.mapKeys { it.value.id } )
            }

            contestPcts.forEach { contestName, pct ->
            if (!doubleIsClose(pct, 1.0))
                logger.warn { "$contestName sum of style pctTotal ${pct} != 1.0"}
            }

            return pools
        }
    }
}

/////////////////////////////////////////////////////////////////////////////

class CorlaContestBuilder(val orgContestName: String, val info: ContestInfo, val contestByCounty: ContestTabByCounty,
                          val contestRound: CorlaContestRoundCsv?, val corlaXmlContest: CorlaXmlContest?) {
    val contestId = info.id
    val Nc: Int     // taken from contestRound.contestBallotCardCount
    var Npop: Int? = null     // taken from contestRound.contestBallotCardCount
    val candidateVotes: Map<Int, Int>
    val totalVotesAllCounties = contestByCounty.totalVotesAllCounties
    val counties = contestByCounty.counties()

    var poolTotalCards: Int = 0
    var poolTotalVotes: Int = 0

    init {
        candidateVotes = contestByCounty.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        val minCardsNeeded = roundUp(candidateVotes.map { it.value }.sum() / info.voteForN.toDouble())

        if (contestRound != null) {
            var useNc = contestRound.contestBallotCardCount
            if (useNc < minCardsNeeded) {
                println("*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${contestRound.contestBallotCardCount} - using totalVotes")
                useNc = minCardsNeeded
            }
            Nc = useNc
            Npop = contestRound.ballotCardCount
        } else {
            Nc = minCardsNeeded
        }
    }

    fun setTotalCardsFromPools(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        poolTotalVotes = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.contestTab(info.id)!!.nvotes() }
    }

    fun makeContest(): Contest {
        val candVotes = candidateVotes.filter { info.candidateIds.contains(it.key) } // get rid of writeins?
        // val totalVotes = candVotes.map {it.value}.sum()
        // val ncards = max(builder.poolTotalCards(), totalVotes)
        // val useNc = max(ncards, builder.Nc)
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / Nc).toInt().toString()
        // assume Ncast = Nc; maybe Ncase = builder.poolTotalCards() ??
        return Contest(info, candVotes, Nc, Nc)
    }

    override fun toString(): String {
        return "CorlaContestBuilder(info=$info, contestId=$contestId, Nc=$Nc, candidateVotes=$candidateVotes, poolTotalCards=$poolTotalCards)"
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
    val infos: Map<Int, ContestInfo>, // all contests
    // val Ncs:  Map<Int, Int> // contestName, Nc
): CardPoolIF, StyleIF {

    val maxMinCardsNeeded: Int
    var adjustCards = 0 // adjusted number of cards, using distributeExpectedOvervotes() on one or more contests

    init {
        val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
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

    //fun adjustCards(adjust: Int, contestId : Int) {
    //    if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
    //    adjustCards = max( adjust, adjustCards)
    //}

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


/* we dont know how many cards are in the pool.
// so adjust the number of cards in the pools so that the sum of pool.undervotes agrees with the refContest
// this only works if the pool has a single style.
fun distributeExpectedOvervotes(refContest: CorlaContestBuilder, cardPools: List<CountyPoolFromStyle>) {
    val contestId = refContest.contestId
    val poolCards = refContest.poolTotalCards()
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
} */


