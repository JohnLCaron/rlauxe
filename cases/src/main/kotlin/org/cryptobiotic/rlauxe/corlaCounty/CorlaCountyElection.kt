package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corla.CorlaStateContestInfoBuilder
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.irv.IrvContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("CorlaCountyElection")

enum class ElectionVariantEnum { Phantoms, OnePool, Styles, Sim }

class ElectionVariant(variantEnum: ElectionVariantEnum) {
    val phantoms = (variantEnum == ElectionVariantEnum.Phantoms)
    val onePool = (variantEnum == ElectionVariantEnum.OnePool)
    val styles = (variantEnum == ElectionVariantEnum.Styles)
    val sim = (variantEnum == ElectionVariantEnum.Sim)

    val auditType = if (sim || phantoms) AuditType.CLCA else AuditType.ONEAUDIT
    fun isClca() = auditType.isClca()
    fun isOA() = auditType.isOA()
}

// in order to do missing correctly, we need the countyTabs from stateInput,
// which probably means we need to convert to canonical.

// create election out of a single county. Creates AuditRecord, not CountyAuditRecord.
class CorlaCountyElection(
    val countyInput: CountyInput,
    val stateInput: StatewideInput,
    val stateContestBuilder: CorlaStateContestInfoBuilder,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val county: String = countyInput.countyName

    val contests: List<ContestIF>
    val mvrs = mutableListOf<AuditableCard>()
    val cardPools = mutableListOf<CardPool>()
    val poolMap = mapOf("AGGREGATED" to 1, "missing" to 2)
    val cardStyles: List<StyleIF>

    val contestsUA: List<ContestWithAssertions>

    val ncards: Int

    init {
        val countyCards = CountyCardsFromManifest(countyInput)
        val converter = CountyCardConverter(countyInput.countyName, countyCards, stateInput, stateContestBuilder.infosByName)

        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)

        val convertedCvrTabs = converter.convertTabs(countyCards.cvrTabs) // unredacted
        val convertedGroupTabs = converter.convertTabs(countyCards.groupTabs) // should be aggregated when exists

        // aggregated tabs arent missing
        val notmissing = if (countyCards.aggGroup == null) convertedCvrTabs else sumContestTabulations(listOf(convertedCvrTabs, convertedGroupTabs))

        // convertedCountyTabs - notmissing: votes cant go below zero
        val missingTabs: Map<Int, ContestTabulation> = subtractContestTabulationsZ(convertedCountyTabs, notmissing)

        var styleId = 3
        cardStyles = countyCards.countyStyles().map { county ->
            CardStyle(county.cardStyle, styleId++,
                converter.convertCountyIdSet(county.contestIds).toList().toIntArray(),
                hasExactContests=true).setNcards(county.ncards)
        }
        val cardStyleMap = cardStyles.associate { it.name to it.id } + poolMap

        // need to iterate over CountyCards to fill in the counts
        val npops = mutableMapOf<Int, Int>() // count contests' population size
        var index = 1
        countyCards.iterator().forEach { countyCard ->
            if (null == cardStyleMap[countyCard.style])
                print("")
            val card = AuditableCard.fromVotes(
                countyCard.imprintedId,
                countyCard.location,
                index++, 0, false,
                styleId = cardStyleMap[countyCard.style] ?: throw RuntimeException(" cant find style name '${countyCard.style}'"),
                votes = converter.convertCountyVotes(countyCard.votes),
                poolId = poolMap[countyCard.poolName],
            )
            // keep track of the contest populations
            convertedCountyTabs.keys.forEach { contestId ->
                if (card.hasContest(contestId)) {
                    val pop = npops.getOrDefault(contestId, 0)
                    npops[contestId] = pop + 1
                }
            }
            mvrs.add(card)
        }
        ncards = countyCards.manifestCount

        if (countyCards.aggGroup != null) {
            val hasExactContests = false // unless it has a single Style
            convertedGroupTabs.values.forEach { it.setNcards(countyCards.aggCount) } // finally know the ncards after reading county Cvr file
            cardPools.add(CardPool(countyCards.aggGroup.groupName, 1, hasExactContests, convertedGroupTabs, countyCards.aggCount))
        }

        // countyTab doesnt know ncards per contest: notmissing knows, but missing doesnt know, so here is an estimation
        // TODO if no missing, can just use notmissing ncards
        val estNcardsByContest = estNcardsByContest(notmissing, missingTabs, countyCards.manifestCount)
        contests = makeContests(stateContestBuilder.infos, convertedCountyTabs, estNcardsByContest) // .associate { it.id to it }

        if (countyCards.missingCount > 0) {
            val hasExactContests = false // unless it has a single Style
            cardPools.add(CardPool("missing", 2, hasExactContests, missingTabs, countyCards.missingCount))
        }

        contestsUA = makeContestWAs(
            contests, npops, emptyMap(), cardPools,
            variant, hasStyle
        )

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

    override fun cardStyles() = cardPools + cardStyles // cardPools are also styleIFs
    override fun cardPools() = cardPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCardsFromMvrs(mvrs)
    override fun ncards() = ncards

    // TODO doesnt someone else add the indices ??
    fun addIndexToMvrs(mvrs: List<AuditableCard>): List<AuditableCard> {
        var cardIndex = 1 // 1 based index
        val result = mutableListOf<AuditableCard>()
        mvrs.forEach { org ->
            result.add(org.copy(index = cardIndex))
            cardIndex++
        }
        return result
    }

    fun createCardsFromMvrs(mvrs: List<AuditableCard>): CloseableIterator<AuditableCard> {
        // remove cvrs for cards in the pools
        val mvrIter = Closer(mvrs.iterator())
        val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
            if (org.poolId != null && variant.isOA()) AuditableCard.removeVotes(org) else org
        }
        return transformer
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //// contest building TODO why so complicated ?? state uses BuildCorlaContests. how differ?

    private fun makeContestInfo(): List<ContestInfo> {
        val mergedContestMap = stateInput.mergedContestMap
        val infos = mutableListOf<ContestInfo>()

        // use canonical contests for the contest and candidate names
        mergedContestMap.values.forEach { mcontest ->
            if (mcontest.counties.contains(countyInput.countyName)) {
                val contestTabAllCounties = stateInput.contestTabsAllCounties()[mcontest.contestName] // needed ?
                if (contestTabAllCounties == null) {
                    logger.warn { "*** Cant find contestTab for '${mcontest.contestName}': remove from audit" }
                } else {
                    val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

                    val info = ContestInfo(
                        mcontest.contestName,
                        infos.size + 1, // TODO here we are changing the Ids
                        candidateNames,     // canonical
                        SocialChoiceFunction.PLURALITY, // TODO
                        mcontest.voteForN
                    )
                    info.metadata["CORLAcontestCardCount"] = mcontest.nc.toString()
                    infos.add(info)
                }
            }
        }
        return infos
    }

    fun makeContests(
        infos: Map<Int, ContestInfo>,
        countyTabs: Map<Int, ContestTabulation>,
        estNcardsByContest: Map<Int, Int>
    ): List<ContestIF> {

        val contests = mutableListOf<ContestIF>()
        countyTabs.forEach { (contestId, contestTab) ->
            val info = infos[contestId]!!
            val ncards = estNcardsByContest[contestId]!!
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

fun makeContestWAs(
    contests: List<ContestIF>,
    npopMap: Map<Int, Int>,
    allIrvTabs: Map<Int, ContestTabulation>, // all tabs for IRV contests
    oneAuditPools: List<CardPool>,
    variant: ElectionVariant,
    hasStyle: Boolean,
): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()

    val regular = ContestWithAssertions.make(contests.filter { !it.isIrv() }, npopMap, true, hasStyle)
    if (variant.isOA()) setPoolAssorterAverages(regular, oneAuditPools)
    contestsUAs.addAll(regular)

    // TODO
    contests.filter { it.isIrv() }.forEach {
        // assumes contestTab.irvVotes are present
        val irvContest = if (!variant.isOA())
            makeRaireContest(it.info(), allIrvTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!)
        else
            makeRaireOneAuditContest(it.info(), allIrvTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!, oneAuditPools)

        contestsUAs.add(irvContest)
    }

    return contestsUAs
}

///////////////////////////////////////////////////////////////////////////
// TODO sometimes we know the redacted styles, so can calculate ncards directlry

// meanwhile, down the rabbit hole again
// estimate ncards(county, contest), return contestId -> ncards
// are we adding in the unredactedCvrTabs ? cause we have an exact count there
fun estNcardsByContest(notmissing: Map<Int, ContestTabulationIF>, missing: Map<Int, ContestTabulation>, manifestCount: Int): Map<Int, Int> {
    val ncardsByContest = mutableMapOf<Int, Int>() // contestId, adjust
    notmissing.forEach { ncardsByContest[it.key] = it.value.ncards() }

    // estimate redacted cards by assuming that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
    val undervotePct: Map<Int, Double> = notmissing.mapValues {
        // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs
        if (it.value.nvotes() == 0) 0.0 else it.value.undervotes() / it.value.nvotes().toDouble()
    }

    missing.forEach { (id, tab) ->
        val pct = undervotePct[id] ?: 0.1  // what if there are no cvrs for this contest ?
        // but it cant use more cards then there are
        // (tab.nvotes + tab.undervotes) / tab.voteForN < totalCvrs
        // tab.nvotes(1 + pct) < (totalCvrs * tab.voteForN)
        val maxPct = (manifestCount * tab.voteForN) / tab.nvotes().toDouble() - 1.0
        val usePct = max(0.0, min(pct, maxPct))

        // we are munging redactedTab directly
        tab.undervotes = roundToClosest(tab.nvotes() * usePct)
        tab.ncardsTabulated = roundToClosest((tab.nvotes() + tab.undervotes) / tab.voteForN.toDouble())
        val accum = ncardsByContest.getOrDefault(id, 0)
        ncardsByContest[id] = tab.ncardsTabulated + accum
    }

    return ncardsByContest
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaCountyElection(
    countyInput: CountyInput,
    stateInput: StatewideInput,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true, // TODO wtf ??
    variant: ElectionVariantEnum,
 ) {
    val stopwatch = Stopwatch()

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createCorlaCountyElection ${countyInput.electionName} in $topdir"}

    val stateContestBuilder = CorlaStateContestInfoBuilder(stateInput)
    val election = CorlaCountyElection(countyInput, stateInput, stateContestBuilder, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}