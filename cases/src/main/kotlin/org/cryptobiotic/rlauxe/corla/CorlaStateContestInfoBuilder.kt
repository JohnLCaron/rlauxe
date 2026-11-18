package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CorlaStateContestInfoBuilder")

// Build Corla Contests from StatewideInput. used by CorlaStateElection
open class CorlaStateContestInfoBuilder(val statewideInput: StatewideInput) {
    val infos: Map<Int, ContestInfo> = makeContestInfos().associateBy { it.id }
    val infosByName = infos.mapKeys { it.value.name }

    // all contests
    fun makeContestInfos(): List<ContestInfo> {
        val countyMap = statewideInput.countyStrataMap

        val contestInfos = mutableListOf<ContestInfo>()

        // this determines the ordering of the contest ids
        // val contestmSorted = statewideInput.mergedContestMap.toList().sortedByDescending { it.second.corlaPop }.toMap()

        // mergedContestInfo in canonical list order, which seems to be alphabetic; this determines contest id ordering.
        // mergedContestMap include info from canonical, round and compare file. but not contestTabs.
        statewideInput.mergedContestMap.values.forEach { mcontest ->
            val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

            val info = ContestInfo(
                mcontest.contestName,
                contestInfos.size + 1,
                candidateNames,
                SocialChoiceFunction.PLURALITY, // TODO IRV
                mcontest.voteForN
            )

            val strata = when {
                (mcontest.counties.size == 1) -> {
                    val county1 = mcontest.counties.first()
                    countyMap[county1]!!
                }
                else -> computeStrataMinRate(mcontest.contestName, mcontest.counties, countyMap)
            } // strata.nmvrs is calculated from the "min rate" algorithm, or for single county, the count of mvrs in the county from comparison file

            info.metadata["StrataNmvrs"] = strata.nmvrs.toString()
            info.metadata["CORLAstrataNcards"] = strata.ballotCardCount.toString()
            info.metadata["CORLAballotCardCount"] = mcontest.corlaPop.toString()
            info.metadata["CORLAcontestBallotCardCount"] = mcontest.nc.toString()
            info.metadata["CORLAauditReason"] = mcontest.auditReason.toString()
            info.metadata["CORLAmarginInVotes"] = mcontest.marginInVotes.toString()
            info.metadata["CanonicalCounties"] = mcontest.counties.toList().toString()
            info.metadata["CORLAestMvrs"] = mcontest.estSamples.toString()
            info.metadata["CORLAcountyMvrs"] = mcontest.countyMvrs.toString()
            info.metadata["CORLAstatewideNmvrs"] = mcontest.statewideMvrs.toString()

            contestInfos.add(info)
        }

        // println("number of contestBuilders = ${contestBuilders.size}")
        return contestInfos
    }

    // Neals algorithm: use the minimum rate across strata
    // depends only on the set of counties used by the contest and the StrataIno for those contests
    fun computeStrataMinRate(name: String, counties: Set<String>, strataMap: Map<String, StrataInfo>): StrataInfo {
        var orgSamples = 0
        val rates = counties.map {
            val strata = strataMap[it]
            if (strata== null || strata.nmvrs == 0) 1.0 else {
                orgSamples += strata.nmvrs                              // includes both state and county samples that are in this county
                strata.nmvrs / strata.ballotCardCount.toDouble()        // but it does seem that the state samples should be added without scaling by min rate, at least for targeted contest ??
            }
        }
        val minRate = rates.min()

        var npop = 0
        var nmvrs = 0
        counties.forEach {
            val strata = strataMap[it]
            if (strata != null && strata.nmvrs != 0) {
                npop += strata.ballotCardCount
                val truncSamples = roundToClosest(strata.ballotCardCount * minRate)
                nmvrs += truncSamples
            }
        }
        // TODO state mvrs
        return StrataInfo(name, nmvrs, 0, npop)
    }
}