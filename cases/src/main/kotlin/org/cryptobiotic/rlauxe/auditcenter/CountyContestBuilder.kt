package org.cryptobiotic.rlauxe.auditcenter

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
        contests = corlaContestBuilders.map { it.build() }
    }

    private fun makeContestBuilders(): List<CorlaContestBuilder> {
        val mergedContestMap = coloradoInput.mergedContestMap
        val strataMap = coloradoInput.strataMap

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonical drives the boat
        mergedContestMap.values.forEach{ mcontest ->
            val contestTabAllCounties = coloradoInput.contestTabsAllCounties[mcontest.contestName]
            if (contestTabAllCounties == null) {
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
                    contestTabAllCounties,
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

class CorlaContestBuilder(val info: ContestInfo, val mcontest: MergedContestInfo, strata: StrataInfo, val contestTabAllCounties: ContestTabAllCounties) {
    val contestId = info.id
    val Nc: Int     // taken from contestRound.contestBallotCardCount
    var Npop: Int? = null     // taken from contestRound.contestBallotCardCount
    val candidateVoteCount = mutableMapOf<String, Int>()
    val totalVotesAllCounties = contestTabAllCounties.totalVotesAllCounties
    val counties = mcontest.counties

    var poolTotalCards: Int = 0
    var poolTotalVotes: Int = 0

    init {
        contestTabAllCounties.choices.forEach { (name, choice) ->
            val canonicalCandidateName = mcontest.canonicalContest.matchCanonicalCandidate(name)
            if (canonicalCandidateName == null || info.candidateNames[canonicalCandidateName] == null) {
                logger.error { "contestTab candidate name $name not found" }
            } else {
                val accum = candidateVoteCount.getOrDefault(canonicalCandidateName, 0)
                candidateVoteCount[canonicalCandidateName] =  accum + choice.totalVotes
            }
        }

        // candidateVotes = contestTab.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        val minCardsNeeded = roundUp(candidateVoteCount.map { it.value }.sum() / info.voteForN.toDouble())

        var useNc = mcontest.nc
        if (useNc < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${mcontest.nc} - using totalVotes")
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

    fun build(): Contest {
        val candVotesById = candidateVoteCount.filter { info.candidateNames[it.key] != null } // get rid of writeins?
                                                .mapKeys { info.candidateNames[it.key]!! }
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / Nc).toInt().toString()
        return Contest(info, candVotesById, Nc, Nc)
    }

    override fun toString(): String {
        return "CorlaContestBuilder(info=$info, contestId=$contestId, Nc=$Nc, candidateVoteCount=$candidateVoteCount, poolTotalCards=$poolTotalCards)"
    }
}

