package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.MergedContestInfo
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
import kotlin.collections.plus
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
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val county: String = countyInput.countyName

    val contests: Map<Int, ContestIF>
    val contestsUA: List<ContestWithAssertions>
    val redactedCvrs: List<AuditableCard>  // redacted cvrs
    val allCards: List<AuditableCard>
    val redactedPools: List<CardPool>
    val mvrs: List<AuditableCard>
    val ncards: Int
    val cardStyles: List<StyleIF>

    init {
        val contestBuilder = CorlaStateContestInfoBuilder(stateInput)

        val countyCards = CountyCardsFromManifest(countyInput)
        val converter = CountyCardConverter(countyInput.countyName, countyCards, stateInput, contestBuilder.infosByName)

        val countyTab = stateInput.countyTabsAllContests()[county]!!
        val convertedCountyTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(countyTab)

        val unredactedCvrTabs = converter.convertTabs(countyCards.cvrTabs)
        val groupTabs = converter.convertTabs(countyCards.groupTabs)
        // aggregated tabs arent missing
        val notmissing = if (countyCards.aggGroup != null) unredactedCvrTabs else sumContestTabulations(listOf(unredactedCvrTabs, groupTabs))

        // TODO not sure about filtering on zero. probably should filter on ncards == 0 ?? Could have no votes and still need to be on
        val missing: Map<Int, ContestTabulation> = subtractContestTabulationsZ(convertedCountyTabs, notmissing).filter { it.value.nvotes() > 0 }

        if (countyCards.aggGroup != null) {
            // data class CardPool(
            //    override val poolName: String,
            //    override val poolId: Int,
            //    val hasExactContests: Boolean,    // aka single style
            //    val infos: Map<Int, ContestInfo>, // do we really need this ??
            //    val contestTabs: Map<Int, ContestTabulationIF>,  // contestId -> ContestTabulation
            //    val totalCards: Int,
            //)
            val aggPool = CardPool(countyCards.aggGroup.groupName, 1, false, groupTabs, countyCards.aggGroup.nlines)
        }

        // we havent yet iterated over CountyCards
        // countyTab doesnt know ncards per contest. notmissing knows, but not missing, so here is an estimation
        val estNcardsByContest = estNcardsByContest(notmissing, missing, countyCards.manifestCount, countyCards.missingCount)

        contests = makeContests(contestBuilder.infos, convertedCountyTabs, estNcardsByContest).associate { it.id to it }

        redactedPools = cvrsFromManifest.redactedPools
        cardStyles = cvrsFromManifest.countyCardStyles()

        // these are mvrs
        redactedCvrs = cvrsFromManifest.makeSimulatedCards()

        // need to know the phantoms to calculate allCards and Npops
        val phantoms = makePhantomCards(contests, 1)
        logger.info { "made ${phantoms.size} phantom cards" }

        allCards = cvrsFromManifest.convertedCvrs + redactedCvrs + phantoms // in memory
        this.ncards = allCards.size
        val npops = tabulateNpops(allCards, infoList)

        // TODO cvrTabs dont have the irv part, so will fail in the raire library
        // val allCardsTabs = tabulateCards(allCards.iterator(), infos)

        contestsUA = makeContestWAs(
            contests, npops, emptyMap(), redactedPools,
            variant, hasStyle
        )
        mvrs = addIndexToMvrs(allCards)
        logger.info { "made ${mvrs.size} mvrs" }

        //val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        //logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}
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

    override fun cardStyles() = cardStyles
    override fun cardPools() = redactedPools
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
            val candVotes = contestTab.votes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins

            // info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
            val contest = if (info.isIrv) // TODO IRV
                IrvContest(info, winners = listOf(0), ncards, Ncast = ncards, undervotes = 0) // TODO this is fake...
            else
                Contest(info, candVotes, ncards, ncards)

            contests.add(contest)
        }
        return contests
    }
}
        /*
        val mergedContestMap = stateInput.mergedContestMap

        val ccContests = mutableListOf<CCContestBuilder>()
        infos.forEach { info ->
            val mcontest = mergedContestMap[info.name]
            if (mcontest != null) {
                // its possible that all cvrs for a contest are redacted
                if ((cvrTabs[info.id] != null || redactedTabs[info.id] != null)) {
                    val cb = CCContestBuilder(
                        variant.auditType,
                        mcontest,
                        info,
                        cvrTabs[info.id],
                        redactedTabs[info.id],
                        variant
                    )
                    ccContests.add(cb)
                }
            } else logger.warn { "*** cant find contest '${info.name}' in stateInput.mergedContestMap" }
        }

        return ccContests
    }

    fun makeContests(contestBuilders: Map<Int, CCContestBuilder>): List<ContestIF> {
        return infoList.filter { contestBuilders[it.id] != null }.map { info ->
            val contestBuilder = contestBuilders[info.id]!!
            contestBuilder.build(info)
        }
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

class CCContestBuilder(
    val auditType: AuditType,
    mcontest2: MergedContestInfo, // not used i think
    val info: ContestInfo,
    cvrTab: ContestTabulation?,
    redactedTab: ContestTabulation?,
    val variant: ElectionVariant
) {
    val contestId = info.id
    val contestName = info.name

    val cvrsTotalCards: Int
    val poolTotalCards: Int
    val candVoteTotals: Map<Int, Int>
    var useNc: Int
    val ncvrs: Int

    init {

        // TODO heres where the contest ids and names will differ
        // candVoteTotals : candidateId -> candidateVote
        candVoteTotals = when {
            (cvrTab == null) -> redactedTab!!.votes
            (redactedTab) == null -> cvrTab.votes
            else -> {
                val sum = mutableMapOf<Int, Int>()
                sum.mergeReduce(listOf(cvrTab.votes, redactedTab.votes))
                sum
            }
        }

        cvrsTotalCards = cvrTab?.ncardsTabulated ?: 0
        poolTotalCards = redactedTab?.ncards() ?: 0
        ncvrs = cvrsTotalCards + poolTotalCards
        useNc = ncvrs

        // try to find Nc - number of votes for the contest in this county
        val minCardsNeededFromVotes = roundUp(candVoteTotals.map { it.value }.sum() / info.voteForN.toDouble())

        if (useNc < minCardsNeededFromVotes) {
            logger.warn {"*** Contest '${info.name}' has $minCardsNeededFromVotes minCardsNeededFromVotes, but ncvrs is ${ncvrs} - using minCardsNeeded" }
            useNc = minCardsNeededFromVotes
        }
    }

    fun build(info: ContestInfo): ContestIF {
        val candVotes = candVoteTotals.filter { info.candidateIds.contains(it.key) } // remove Write-Ins

        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
        return if (info.isIrv) // TODO
                IrvContest(info, winners=listOf(0), useNc, Ncast=ncvrs, undervotes=0) // TODO this is fake...
            else
                Contest(info, candVotes, useNc, ncvrs)
    }

    override fun toString() = buildString {
        append("${nfn(info.id,3)}, ${trunc(info.name, nameWidth)}, ")
        append(" ${nfn(useNc, 8)}, ${nfn(ncvrs, 7)}, ${nfn(useNc-ncvrs, 7)}")
    }

    companion object {
        val nameWidth = 50
        val header = " id, ${trunc("name", nameWidth)},    Nc,   ncvrs,    diff"
    }
} */

///////////////////////////////////////////////////////////////////////////
// TODO sometimes we know the redacted styles, so can calculate ncards directlry

// meanwhile, down the rabbit hole again
// estimate ncards(county, contest), return contestId -> ncards
// are we adding in the unredactedCvrTabs ? cause we have an exact count there
fun estNcardsByContest(notmissing: Map<Int, ContestTabulationIF>, missing: Map<Int, ContestTabulation>, manifestCount: Int, missingCount: Int): Map<Int, Int> {
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
        val maxPct = (manifestCounts.totalEntries * tab.voteForN) / tab.nvotes().toDouble() - 1.0
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

    val election = CorlaCountyElection(countyInput, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}