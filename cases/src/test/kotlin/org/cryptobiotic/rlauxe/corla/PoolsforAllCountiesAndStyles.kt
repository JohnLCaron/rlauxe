package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestBuilder
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyStylesFromMvrs
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.auditcenter.MvrStyle
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.roundToInt

private val debugNvotes = false

// obsolete but used by createCorlaElection
class PoolsforAllCountiesAndStyles(
    val corlaContestBuilders: List<CorlaContestBuilder>,
    val coloradoInput: ColoradoInput
) {
    val builders = corlaContestBuilders.associateBy { it.info.name }
    val countyPools: List<CountyPoolFromStyle>

    init {
        val infos = corlaContestBuilders.associate { it.info.name to it.info }

        val countyNc: Map<String, Map<String, Int>> = distributeNc() // county -> contest -> Nc for that contest in that county

        val contestTabByCounty: Map<String, CountyTabAllContests> = coloradoInput.countyTabsAllContests
        val stylesByCounty: Map<String, CountyStylesFromMvrs> = coloradoInput.stylesFromMvrs.associateBy { it.countyName }

        // merge the styles into the CountyContestTabs, pick out the contestTabs that dont have styles
        val tabsMissingStyles = mutableMapOf<String, MutableList<CountyContestVotes>>()
        contestTabByCounty.map { (countyName, countyContest) ->
            val countyStyles: CountyStylesFromMvrs = stylesByCounty[countyName]!!
            countyContest.contests.forEach { (contestName, contestTab) ->
                val stylesForContest: List<MvrStyle> =
                    countyStyles.styles.values.filter { it: MvrStyle -> it.contests.contains(contestName) }
                if (stylesForContest.isEmpty()) {
                    val missingStyles = tabsMissingStyles.getOrPut(countyName) { mutableListOf() }
                    missingStyles.add(contestTab)
                }
            }
        }

        val missingPools: Map<String, CountyPoolFromStyle> = makeMissingPools(tabsMissingStyles)

        val countyPools: List<CountyPoolsBuilderOld> = contestTabByCounty.map { (countyName, countyContest) ->
            CountyPoolsBuilderOld(
                countyName, countyContest, stylesByCounty[countyName]!!,
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

        this.countyPools = pools
    }

    // for each contest, distribte Nc to the counties it is in, proportional to votesInCounty / totalVotes
    fun distributeNc(): Map<String, Map<String, Int>> { // county -> contest -> Nc
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc
        coloradoInput.contestTabsAllCounties.values.forEach { contestTabAllCounties ->
            val contestName = contestTabAllCounties.contestName
            val builder = builders[contestName]
            if (builder == null)
                throw RuntimeException()
            val contestTotalVotes = contestTabAllCounties.choices.values.sum()
            contestTabAllCounties.countyVotes.forEach { (countyName, countyVotes) ->
                val countyContest = countyNc.getOrPut(countyName) { mutableMapOf() }
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
        coloradoInput.contestTabsAllCounties.values.forEach { contestTab ->
            val contestName = contestTab.contestName
            val sum = contestSum[contestName]!!
            val builder = builders[contestName]!!
            val contestNc = builder.Nc
            //if (abs(contestNc-sum) > 5)
            //    logger.warn{"makeCardPoolsFromCountyStyles has (contestNc-sum) ${abs(contestNc-sum)} > 5" }
        }
        return countyNc
    }

    fun makeMissingPools(tabsMissingStyles: Map<String, List<CountyContestVotes>>): Map<String, CountyPoolFromStyle> {
        return tabsMissingStyles.map { (countyName, countestTabs) -> makeMissingPool(countyName, countestTabs) }
            .associateBy { it.countyName }
    }

    fun makeMissingPool(countyName: String, tabsMissingStyles: List<CountyContestVotes>): CountyPoolFromStyle {
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
            val cleanedName = it.contestName
            buildersByName[cleanedName]!!
        } // clean
        val infos = contestBuilders.associate { it.info.id to it.info }

        val votesForStyle = mutableMapOf<Int, ContestTabulation>() // all contests, this style
        tabsMissingStyles.forEach { contestTab ->
            val cleanedName = contestTab.contestName
            val builder = buildersByName[cleanedName]!!
            val info = builder.info
            val votes = mutableMapOf<Int, Int>() // this contest
            contestTab.choices.forEach { (choiceName, choiceVote) ->
                val candId = info.candidateNames[choiceName]!!
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
        return CountyPoolFromStyle(
            countyName, CountyPoolsBuilderOld.nextPoolId++, hasExactContests = true,
            voteTotals = votesForStyle, infos
        )
    }
}