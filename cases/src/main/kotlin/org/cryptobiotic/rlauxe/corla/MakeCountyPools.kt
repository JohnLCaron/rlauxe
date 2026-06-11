package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.CountyPoolMultipleStyles
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private val debugNvotes = false
private val debugUndervotes = true

// one CountyPoolMultipleStyles for each County
class MakeCountyPools(
    val corlaContestBuilders: List<CorlaContestBuilder>,
    val coloradoInput: ColoradoInput
) {
    val builders = corlaContestBuilders.associateBy { it.info.name }
    val infos = corlaContestBuilders.associate { it.info.id to it.info }

    val countyPoolsMS: List<CountyPoolMultipleStyles>

    init {
        val infosByName = corlaContestBuilders.associate { it.info.name to it.info }

        val distributeNc: Map<String, Map<String, Int>> = distributeNc() // county -> contest -> Nc for that contest in that county

        val contestTabByCounty: Map<String, CountyContestTabs> = coloradoInput.countyContestTabs.associateBy { it.countyName }

        val mvrStylesMap: Map<String, CountyStylesFromMvrs> = coloradoInput.mvrStyles.associateBy { it.countyName }

        // the mvr styles are not complete. This seriously sucks.
        // pick out the contests that dont have styles that contain it
        val missingContests = mutableMapOf<String, MutableList<CountyContestTab>>() // countyName -> contestTab
        contestTabByCounty.map { (countyName, countyContest) ->
            val mvrStyles: CountyStylesFromMvrs = mvrStylesMap[countyName]!!
            countyContest.contests.forEach { (contestName, contestTab) ->
                val stylesForContest: List<MvrStyle> =
                    mvrStyles.styles.values.filter { it: MvrStyle -> it.contests.contains(contestName) }
                if (stylesForContest.isEmpty()) {
                    val missingStyles = missingContests.getOrPut(countyName) { mutableListOf() }
                    missingStyles.add(contestTab)
                }
            }
        }

        // missingPools for each county
        val missingPools: Map<String, CountyPoolFromStyle> = makeMissingPools(missingContests)

        val countyPools: List<CountyPools> = contestTabByCounty.map { (countyName, countyContest) ->
            CountyPools(
                countyName, mvrStylesMap[countyName]!!, countyContest,
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

        // data class CountyPools(
        //    val countyName: String,
        //    val countyStyles: CountyStylesFromMvrs, // Set<contestId> and reletive count within county
        //    val cct: CountyContestTab, // the votes subtotal for each contest in the county
        //    val missingPool: CountyPoolFromStyle?,
        //    val contestNc: Map<String, Int>, // contest name -> contest Nc for the county
        //    val infos: Map<String, ContestInfo> // contest name -> contest id
        //)

        // data class CountyPoolMultipleStyles (
        //    val countyName: String,
        //    val countyId: Int,
        //    val infos: Map<Int, ContestInfo>, // do we really need this ??
        //    val contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
        //    val totalCards: Int,
        //    val cardStyles: List<CardStyle>,
        //    val cardStylesCount: List<Int>, // or CardStyleWithNCards ??
        //)
        // TODO can we just use CountyPools directly instead of this transform?

        var countyPoolId = 1
        this.countyPoolsMS = countyPools.map { countyPool ->
            val countyContestNc: Map<String, Int> = distributeNc[countyPool.countyName]!!  // contestName -> contest.Nc

            val tabs = countyPool.cct.contests.map { (name, contestTab) ->
                val info = infosByName[name]!!
                val ncards = countyContestNc[name] ?: 0
                contestTab.makeContestTabulation(info, ncards)
            }
            val totalCards = tabs.sumOf { it.ncards() }

            // TODO im not sure is missingStyle got assigned ncard ??
            val countyStyles: List<CountyPoolFromStyle> = if (countyPool.missingPool == null) countyPool.makePools() else
                countyPool.makePools() + countyPool.missingPool

            CountyPoolMultipleStyles(countyPool.countyName, countyPoolId++,
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

        return CountyPoolFromStyle(
            countyName, countyName, CountyPools.nextPoolId++, hasExactContests = true,
            voteTotals = votesForStyle, infos
        )
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

/*
fun convertPrecinctsToCardPools(
    precinctFile: String,
    infoMap: Map<Int, ContestInfo>,
    hasExactContests: Boolean,
): List<OneAuditPoolFromBallotStyle> {
    val reader = ZipReader(precinctFile)
    val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
    val precincts = readColoradoPrecinctLevelResults(input)
    println("precincts = ${precincts.size}")

    return precincts.mapIndexed { idx, precinct ->
        val contestTabs = mutableMapOf<Int, ContestTabulation>()
        precinct.contestChoices.forEach { (name, choices) ->
            val contestName = mutatisMutandi(contestNameCleanup(name))
            val info = infoMap.values.find { it.name == contestName }
            if (info != null) {
                val contestTab = ContestTabulation(info)
                contestTabs[info.id] = contestTab
                choices.forEach { choice ->
                    val choiceName = candidateNameCleanup(choice.choice)
                    val candId = info.candidateNames[choiceName]
                    if (candId == null) {
                        // logger.warn{"*** precinct ${precinct} candidate ${choiceName} writein missing in info ${info.id} $contestName infoNames= ${info.candidateNames}"}
                    } else {
                        contestTab.addVote(candId, choice.totalVotes) // cant use addVotes
                    }
                }
            }
        }
        OneAuditPoolFromBallotStyle(
            "${precinct.county}-${precinct.precinct}", idx+1,
            hasExactContests = hasExactContests, contestTabs, infoMap
        )
    }
} */


