package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import kotlin.Int

private val logger = KotlinLogging.logger("CorlaStateContestInfoBuilder")

// Build Corla Contests from StatewideInput. used by CorlaStateElection
open class CorlaStateContestInfoBuilder(val statewideInput: StatewideInput) {
    val infos: Map<Int, ContestInfo> = makeContestInfos().associateBy { it.id }
    val infosByName = infos.mapKeys { it.value.name }

    // all contests
    fun makeContestInfos(): List<ContestInfo> {
        val contestInfos = mutableListOf<ContestInfo>()

        // this determines the ordering of the contest ids
        // val contestmSorted = statewideInput.mergedContestMap.toList().sortedByDescending { it.second.corlaPop }.toMap()

        // mergedContestInfo in canonical list order, which seems to be alphabetic; this determines contest id ordering.
        statewideInput.mergedContestMap.values.forEach { mcontest ->
            val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

            val info = ContestInfo(
                mcontest.contestName,
                contestInfos.size + 1,
                candidateNames,
                SocialChoiceFunction.PLURALITY, // TODO IRV
                mcontest.voteForN
            )

            val strata = mcontest.strata!!
            info.metadata["StrataNmvrs"] = strata.nmvrs.toString()
            info.metadata["CORLAstrataNcards"] = strata.population.toString()
            info.metadata["CORLAballotCardCount"] = mcontest.corlaPop.toString()
            info.metadata["CORLAcontestBallotCardCount"] = mcontest.corlaNc.toString()
            info.metadata["CORLAauditReason"] = mcontest.corlaReason.toString()
            info.metadata["CORLAmarginInVotes"] = mcontest.marginInVotes.toString()
            info.metadata["CanonicalCounties"] = mcontest.counties.toList().toString()
            info.metadata["CORLAestMvrs"] = mcontest.corlaEstSamples.toString()
            info.metadata["CORLAcountyMvrs"] = strata.countyMvrs.toString()
            info.metadata["CORLAstatewideNmvrs"] = strata.stateMvrs.toString()
            info.metadata["rlauxeEstMvrs"] = mcontest.rlauxeEstSamples.toString()

            contestInfos.add(info)
        }

        // println("number of contestBuilders = ${contestBuilders.size}")
        return contestInfos
    }
}

