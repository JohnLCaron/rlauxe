package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CorlaStateContestInfoBuilder")

// Build Corla Contests from ColoradoInput. used by CorlaStateElection
// TODO should add up the county votes, not use the statewide total since some counties are missing
open class CorlaStateContestInfoBuilder(val coloradoInput: ColoradoInput) {
    // val corlaContestBuilders: List<CorlaContestBuilder> = makeContestBuilders() // 181
    val infos: Map<Int, ContestInfo> = makeContestInfos().associateBy { it.id }
    val infosByName = infos.mapKeys { it.value.name }

    /* TODO set Ncast ??
    fun contests(ncast: Map<Int, Int>): List<Contest> {
        return corlaContestBuilders.map { it.build( ncast[it.contestId] ?: it.Nc) }
    } */

    // all contests
    fun makeContestInfos(): List<ContestInfo> {
        val mergedContestMap = coloradoInput.mergedContestMap
        val strataMap = coloradoInput.strataMap

        val contestInfos = mutableListOf<ContestInfo>()

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
                    contestInfos.size + 1,
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

                contestInfos.add(info)
            }
        }

        // println("number of contestBuilders = ${contestBuilders.size}")
        return contestInfos
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