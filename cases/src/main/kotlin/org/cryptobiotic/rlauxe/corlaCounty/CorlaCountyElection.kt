package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.MergedContestInfo
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
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

class CorlaCountyElection(
    val countyInput: CorlaCountyInput,
    val stateInput: ColoradoInput,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val county: String = countyInput.countyName

    val infoList = makeContestInfo().sortedBy { it.id }
    val infos = infoList.associateBy { it.id }
    val infosByName = infoList.associateBy { it.name } //  are the cvr names compatible ?

    val contestBuilders: Map<Int, CCContestBuilder> // make visible for debugging
    val contests: List<ContestIF>
    val contestsUA: List<ContestWithAssertions>
    val redactedCvrs: List<AuditableCard>  // redacted cvrs
    val allCards: List<AuditableCard>
    val redactedPools: List<CardPool>
    val mvrs: List<AuditableCard>
    val ncards: Int
    val cardStyles: List<StyleIF>

    init {
        if (stateInput.strataMap[county] == null) {
            stateInput.strataMap.keys.sorted().forEach { println(it) }
            throw RuntimeException("stateInput.strata doesnt have county $county")
        }
        val countyInfo = stateInput.strataMap[county]!!

        val cvrsFromManifest = CvrsFromManifest(variant, countyInput, stateInput, infos, stateElection =  false)

        contestBuilders = makeContestBuilders(
            cvrsFromManifest.convertedCvrTabs,
            cvrsFromManifest.redactedTabs
        ).associate { it.contestId to it }
        contests = makeContests(contestBuilders)

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
        val allCardsTabs = tabulateCards(allCards.iterator(), infos)

        contestsUA = makeContestWAs(contests, npops, allCardsTabs, redactedPools,)
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
    //// contest building

    private fun makeContestInfo(): List<ContestInfo> {
        val mergedContestMap = stateInput.mergedContestMap
        val infos = mutableListOf<ContestInfo>()

        // use canonical contests for the contest and candidate names
        mergedContestMap.values.forEach { mcontest ->
            if (mcontest.counties.contains(countyInput.countyName)) {
                val contestTabAllCounties = stateInput.contestTabsAllCounties()[mcontest.contestName] // needed ?
                if (contestTabAllCounties == null) {
                    logger.warn{"*** Cant find contestTab for '${mcontest.contestName}': remove from audit" }
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

    fun makeContestBuilders(cvrTabs: Map<Int, ContestTabulation>,
                            redactedTabs: Map<Int, ContestTabulation>,
    ): List<CCContestBuilder> {
        val mergedContestMap = stateInput.mergedContestMap

        val ccContests = mutableListOf<CCContestBuilder>()
        infoList.forEach { info ->
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
                        variant)
                    ccContests.add(cb)
                }
            }
            else logger.warn{"*** cant find contest '${info.name}' in stateInput.mergedContestMap"}
        }

        return ccContests
    }

    fun makeContests(contestBuilders: Map<Int, CCContestBuilder>): List<ContestIF> {
        return infoList.filter { contestBuilders[it.id] != null }.map { info ->
            val contestBuilder = contestBuilders[info.id]!!
            contestBuilder.build(info)
        }
    }

    fun makeContestWAs(
        contests: List<ContestIF>,
        npopMap: Map<Int, Int>,
        allCvrTabs: Map<Int, ContestTabulation>,
        oneAuditPools: List<CardPool>,
    ): List<ContestWithAssertions> {
        val contestsUAs = mutableListOf<ContestWithAssertions>()

        val regular = ContestWithAssertions.make(contests.filter { !it.isIrv() }, npopMap, true, hasStyle)
        if (variant.isOA()) setPoolAssorterAverages(regular, oneAuditPools)
        contestsUAs.addAll(regular)

        contests.filter { it.isIrv() }.forEach {
            // assumes contestTab.irvVotes are present
            val irvContest = if (!variant.isOA())
                makeRaireContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!)
            else
                makeRaireOneAuditContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!, oneAuditPools)
            contestsUAs.add(irvContest)
        }

        return contestsUAs
    }
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
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaCountyElection(
    countyInput: CorlaCountyInput,
    stateInput: ColoradoInput,
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