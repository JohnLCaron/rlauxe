package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corla.CorlaStateContestInfoBuilder
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.irv.IrvContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.map
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CorlaCountyElectionNoCvrs")

// create election out of a single county. Creates AuditRecord, not CountyAuditRecord.
// by writing to disk, we can redo individual counties if needed, befire we do the state
class CorlaCountyElectionNoCvrs(
    val countyInput: CountyInput,
    val stateInput: StatewideInput, // in order to do missing correctly, we need the countyTabs from stateInput,
    val stateContestInfoBuilder: CorlaStateContestInfoBuilder, // infos consistent across counties
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
): ElectionBuilder {
    val variant = ElectionVariant(ElectionVariantEnum.OnePool)

    val county: String = countyInput.countyName
    val contests: List<ContestIF>
    val cards = mutableListOf<AuditableCard>()
    val cardPool: CardPool
    val cardStyle: CardStyle

    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        val countyAcTabs = stateInput.countyTabsAllContests()[county]!!
        val converter = CountyTabsConverter(countyInput.countyName, countyAcTabs, stateInput, stateContestInfoBuilder.infosByName)
        val convertedCountyAcTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyAcTabs)
        val allContestIds = convertedCountyAcTabs.keys.toList()

        var index = 1
        countyInput.readCountyManifest().forEach { manifestEntry ->

            // data class AuditableCard (
            //    val id: String, // enough info to find the card for a manual audit.
            //    val location: String?, // enough info to find the card for a manual audit.
            //    val index: Int,  // index into the original, canonical list of cards, aka manifest index
            //    val prn: Long,   // psuedo random number
            //    val phantom: Boolean,
            //    val styleId: Int, // must have a styleId
            //    val contestIds: IntArray,   // these 3 form the cvr.votes map. will be empty if no cvr.
            //    val contestStarts: IntArray,
            //    val candidates: IntArray,
            //    val poolId: Int?, // must be set if its from a OneAudit CardPool; if not OneAudit, ok to set anyway for visibility
            //)

            val card = AuditableCard(
                manifestEntry.imprintedId(),
                manifestEntry.location(),
                index++, 0, false,
                styleId = 1,
                IntArray(0), IntArray(0),IntArray(0),
                poolId = 2,
            )
            cards.add(card)
        }

        // TODO these arent the mvrs, this is the card manifest; mvrs must simulate the pool subtotals
        // need to iterate over CountyCards to fill in the counts
        // transform to canonical AuditableCards, all are in memory in cards
        val npops = mutableMapOf<Int, Int>() // count contests' population size
        ncards = cards.size
        cardStyle = CardStyle(county, 1,allContestIds.toIntArray(), hasExactContests=false).setNcards(ncards)

        cardPool = CardPool("${county}Pool", 2, hasExactContests=false, convertedCountyAcTabs, ncards)

        contests = makeContests(stateContestInfoBuilder.infos, convertedCountyAcTabs, ncards)

        contestsUA = makeContestWAs(contests, npops, emptyMap(), listOf(cardPool), variant, hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            countyInput.electionName,
            variant.auditType,
            ncards(),
            contestsUA.size,
            true,
            mvrSource = mvrSource
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = listOf(cardPool, cardStyle) // cardPools are also styleIFs
    override fun cardPools() = listOf(cardPool)
    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = null

    override fun cards() = Closer(cards.iterator())
    override fun ncards() = ncards

    fun makeContests(
        infos: Map<Int, ContestInfo>,
        countyTabs: Map<Int, ContestTabulation>,
        ncards: Int
    ): List<ContestIF> {

        val contests = mutableListOf<ContestIF>()
        countyTabs.forEach { (contestId, contestTab) ->
            val info = infos[contestId]!!
            val candVotes: Map<Int, Int> = contestTab.votes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            if (ncards > 0 && candVotes.values.sum() <= info.voteForN * ncards) {
                // info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
                val contest = if (info.isIrv) // TODO IRV
                    IrvContest(info, winners = listOf(0), ncards, Ncast = ncards, undervotes = 0) // TODO this is fake...
                else
                    Contest(info, candVotes, ncards, ncards)
                contests.add(contest)
            }
        }
        return contests
    }
}
