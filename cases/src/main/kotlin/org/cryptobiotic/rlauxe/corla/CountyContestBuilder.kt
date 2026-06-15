package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CountyContestBuilder")

// Build the Contests from ColoradoInput
open class CountyContestBuilder(val coloradoInput: ColoradoInput) {
    val corlaContestBuilders: List<CorlaContestBuilder> = makeContestBuilders() // 181
    val contests: List<Contest>

    init {
        contests = corlaContestBuilders.map { it.makeContest() }
    }

    private fun makeContestBuilders(): List<CorlaContestBuilder> {
        val mergedContestMap = coloradoInput.mergedContestMap
        val strataMap = coloradoInput.strataMap
        val contestTabs = coloradoInput.contestTabsByCounty

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonical drives the boat
        mergedContestMap.values.forEach{ mcontest ->
            val contestTab = contestTabs[mcontest.contestName]
            if (contestTab == null) {
                logger.warn{"*** Cant find contestTab for '${mcontest.contestName}': remove from audit" }
                // throw RuntimeException()
            } else {

                val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

                val info = ContestInfo(
                    mcontest.contestName,
                    contestBuilders.size + 1,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY, // TODO
                    mcontest.voteForN
                )

                val strata = when {
                    (mcontest.counties.size == 1) -> strataMap[mcontest.counties.first()]!!
                    /* (mcontest.counties.size > 60) -> {
                    val contestsPlus = mcontest.counties // + listOf("Statewide")
                    computeStrataMinRate(mcontest.contestName, contestsPlus, strataMap)
                } */
                    else -> computeStrataMinRate(mcontest.contestName, mcontest.counties, strataMap)
                }
                info.metadata["CORLAhaveMvrs"] = strata.nmvrs.toString()
                info.metadata["CORLAsample"] = mcontest.nsamples.toString()
                info.metadata["CORLAauditReason"] = mcontest.auditReason.toString()
                info.metadata["CORLAmarginInVotes"] = mcontest.marginInVotes.toString()
                info.metadata["CORLAcounties"] = mcontest.counties.toList().toString()
                info.metadata["CORLAcountyMvrs"] = mcontest.countyMvrs.toString()
                info.metadata["CORLAstatewideNmvrs"] = mcontest.statewideMvrs.toString()

                val contest = CorlaContestBuilder(
                    info,
                    mcontest,
                    strata,
                    contestTab,
                )
                contestBuilders.add(contest)
            }
        }

        // println("number of contestBuilders = ${contestBuilders.size}")
        return contestBuilders
    }

    // Neals algorithm: use the minimum rate across strata
    // depends only on the set of counties, could make common one
    fun computeStrataMinRate(name: String, counties: Set<String>, strataMap: Map<String, StrataInfo>): StrataInfo {
        var orgSamples = 0
        val minRate = counties.map {
            val s = strataMap[it]
            if (s == null) 1.0 else {
                orgSamples += s.nmvrs
                s.nmvrs / s.ncards.toDouble()
            }
        }.min()

        var npop = 0
        var nmvrs = 0
        counties.forEach {
            val strata = strataMap[it]
            if (strata != null) {
                npop += strata.ncards
                val truncSamples = roundToClosest(strata.ncards * minRate)
                nmvrs += truncSamples
            }
        }
        return StrataInfo(name, nmvrs, npop)
    }
}

/////////////////////////////////////////////////////////////////////////////

// TODO ContestTabByCounty is what you need to break out the votes by county....
class CorlaContestBuilder(val info: ContestInfo, val contest: MergedContestInfo, strata: StrataInfo, val contestTab: ContestTabByCounty) {
    val contestId = info.id
    val Nc: Int     // taken from contestRound.contestBallotCardCount
    var Npop: Int? = null     // taken from contestRound.contestBallotCardCount
    val candidateVotes: Map<Int, Int>
    val totalVotesAllCounties = contestTab.totalVotesAllCounties
    val counties = contest.counties

    var poolTotalCards: Int = 0
    var poolTotalVotes: Int = 0

    init {

        candidateVotes = contestTab.choices.map { (name, choice) ->
            Pair( info.candidateNames[name] ?: 0, choice.totalVotes)
        }.toMap()

        // candidateVotes = contestTab.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        val minCardsNeeded = roundUp(candidateVotes.map { it.value }.sum() / info.voteForN.toDouble())

        var useNc = contest.nc
        if (useNc < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${contest.nc} - using totalVotes")
            useNc = minCardsNeeded
        }
        Nc = useNc
        Npop = strata.ncards
    }

    fun setTotalCardsFromPools(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        poolTotalVotes = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.contestTab(info.id)!!.nvotes() }
    }

    fun expectedPoolNCards() = Nc

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

