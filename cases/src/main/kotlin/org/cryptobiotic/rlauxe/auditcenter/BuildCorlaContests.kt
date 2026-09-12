package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.MergedContestInfo
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CountyContestBuilder")

// Build Corla Contests from ColoradoInput. used by CorlaStateElection
// TODO should add up the county votes, not use the statewide total since some counties are missing
open class BuildCorlaContests(val coloradoInput: ColoradoInput) {
    val corlaContestBuilders: List<CorlaContestBuilder> = makeContestBuilders() // 181
    val infos: Map<Int, ContestInfo> = corlaContestBuilders.map { it.info }.associateBy{ it.id }
    val infosByName = infos.mapKeys { it.value.name }

    // TODO set Ncast ??
    fun contests(ncast: Map<Int, Int>): List<Contest> {
        return corlaContestBuilders.map { it.build( ncast[it.contestId] ?: it.Nc) }
    }

    private fun makeContestBuilders(): List<CorlaContestBuilder> {
        val mergedContestMap = coloradoInput.mergedContestMap
        val strataMap = coloradoInput.strataMap

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonical drives the boat
        mergedContestMap.values.forEach{ mcontest ->
            val contestTabAllCounties = coloradoInput.contestTabsAllCounties()[mcontest.contestName]
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
                    (mcontest.counties.size == 1) -> {
                        val county1 = mcontest.counties.first()
                        val strata1 = strataMap[county1]
                        if (strata1 == null) {
                            strataMap.values.first()
                        } else strata1
                    }
                    /* (mcontest.counties.size > 60) -> {
                    val contestsPlus = mcontest.counties // + listOf("Statewide")
                    computeStrataMinRate(mcontest.contestName, contestsPlus, strataMap)
                } */
                    else -> computeStrataMinRate(mcontest.contestName, mcontest.counties, strataMap)
                }
                info.metadata["CORLAhaveMvrs"] = strata.nmvrs.toString()
                info.metadata["CORLAstrataNcards"] = strata.ballotCardCount.toString()
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
                s.nmvrs / s.ballotCardCount.toDouble()
            }
        }.min()

        var npop = 0
        var nmvrs = 0
        counties.forEach {
            val strata = strataMap[it]
            if (strata != null) {
                npop += strata.ballotCardCount
                val truncSamples = roundToClosest(strata.ballotCardCount * minRate)
                nmvrs += truncSamples
            }
        }
        return StrataInfo(name, nmvrs, npop)
    }
}

/////////////////////////////////////////////////////////////////////////////
// one contest

class CorlaContestBuilder(val info: ContestInfo, val mcontest: MergedContestInfo, val strata: StrataInfo, val contestTabAllCounties: ContestTabAllCounties) {
    val contestId = info.id
    val Nc: Int     // taken from contestRound.contestBallotCardCount
    val minCardsNeeded: Int

    val candidateVoteCount: Map<String, Int> // canonicalCandidateName -> votes
    val counties = mcontest.counties

    var poolTotalCards: Int = 0
    var poolTotalVotes: Int = 0

    init {
        candidateVoteCount = contestTabAllCounties.canonicalChoices(mcontest.canonicalContest)

        // candidateVotes = contestTab.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        minCardsNeeded = roundUp(candidateVoteCount.map { it.value }.sum() / info.voteForN.toDouble())

        var useNc = mcontest.nc
        if (useNc < minCardsNeeded) {
            logger.warn {"*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${mcontest.nc} - using totalVotes" }
            useNc = minCardsNeeded
        }
        Nc = useNc
    }

    fun setTotalCardsFromPools(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        poolTotalVotes = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.contestTab(info.id)!!.nvotes() }
    }

    fun expectedPoolNCards() = Nc

    fun build(ncast:Int): Contest {
        val candVotesById = candidateVoteCount.filter { info.candidateNames[it.key] != null } // get rid of writeins?
                                                .mapKeys { info.candidateNames[it.key]!! }
        var useNc = this.Nc
        if (useNc < ncast) {
            println("*** Contest '${info.name}' has Nc=${this.Nc}, but ncast is ${ncast} - using ncast")
            useNc = ncast
        }
        // we know cvrs are missing lots of cards, so maybe this is dubious
        var useNcast = ncast
        if (useNcast < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but useNcast is ${useNcast}")
            useNcast = minCardsNeeded
        }
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
        return Contest(info, candVotesById, useNc, useNcast)
    }

    override fun toString(): String {
        return "CorlaContestBuilder(info=$info, contestId=$contestId, Nc=$Nc, candidateVoteCount=$candidateVoteCount, poolTotalCards=$poolTotalCards)"
    }
}