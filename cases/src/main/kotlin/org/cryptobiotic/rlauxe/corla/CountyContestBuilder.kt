package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

private val debugUndervotes = false

open class CountyContestBuilder {
    val corlaInput = Colorado2024Input
    val corlaContestBuilders: List<CorlaContestBuilder> = makeContestBuilders() // 181
    val contests: List<ContestIF>

    init {
        contests = corlaContestBuilders.map { it.makeContest() }
    }

    private fun makeContestBuilders(): List<CorlaContestBuilder> {

        /* val canonical = corlaInput.canonicalContests
        val resultsContestMap = corlaInput.resultsContests.associateBy { it.contestName }
        val roundContestMap = corlaInput.roundContests
        val xmlDetailMap = corlaInput.detailXmlContests.contests.associateBy { it.text }
        val contestMvrs = corlaInput.contestMvrs.associateBy { it.contestName } */

        // data class MergedContestInfo(
        //    // canonical
        //    val contestName: String,
        //    val choices: List<String>,
        //    val counties: Set<String>,
        //
        //    // contestRound
        //    val auditReason: AuditReason,
        //    val npop:Int,
        //    val nc:Int,
        //    val voteForN: Int,
        //    val nsamples: Int,
        //    val marginInVotes: Int,
        //
        //    // mvr file
        //    val countyMvrs: Int,
        //    val statewideMvrs: Int,
        //)
        //
        //data class StrataInfo(
        //    val strataName: String,
        //    val nmvrs: Int,
        //    val Npop: Int,
        //)
        //
        //data class MergedInfo(
        //    val mergedContestInfo: List<MergedContestInfo>,
        //    val strataInfo: List<StrataInfo>,
        //    val statewideContests: List<CorlaContestRoundCsv>,
        //)
        val mergedContestMap = Colorado2024Input.mergedContestMap
        val strataMap = Colorado2024Input.strataMap
        val contestTabs = corlaInput.contestTabsByCounty

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonical drives the boat
        mergedContestMap.values.forEach{ mcontest ->
            val contestTab = contestTabs[mcontest.contestName]
            if (contestTab == null) {
                logger.warn{"*** Cant find contestTab for '${mcontest.contestName}'" }
                throw RuntimeException()
            }

            val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

            val info = ContestInfo(
                mcontest.contestName,
                contestBuilders.size + 1,
                candidateNames,
                SocialChoiceFunction.PLURALITY, // TODO
                mcontest.voteForN
            )

            val strata = when{
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

        println("number of contestBuilders = ${contestBuilders.size}")

        return contestBuilders
    }

    // Neals algorithm: use the minimum rate across strata
    // depends only on the set of counties, could make common one
    fun computeStrataMinRate(name: String, counties: Set<String>, strataMap: Map<String, StrataInfo>): StrataInfo {
        var orgSamples = 0
        val minRate = counties.map {
            val s = strataMap[it]!!
            orgSamples += s.nmvrs
            s.nmvrs / s.Npop.toDouble()
        }.min()

        var npop = 0
        var nmvrs = 0
        counties.forEach {
            val strata = strataMap[it]!!
            npop += strata.Npop
            val truncSamples = roundToClosest(strata.Npop * minRate)
            nmvrs += truncSamples
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
            Pair(info.candidateNames[name]!!, choice.totalVotes)
        }.toMap()

        // candidateVotes = contestTab.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        val minCardsNeeded = roundUp(candidateVotes.map { it.value }.sum() / info.voteForN.toDouble())

        var useNc = contest.nc
        if (useNc < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${contest.nc} - using totalVotes")
            useNc = minCardsNeeded
        }
        Nc = useNc
        Npop = strata.Npop
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

