package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.MergedContestInfo
import org.cryptobiotic.rlauxe.auditcenter.StrataInfo
import org.cryptobiotic.rlauxe.core.*
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

private val logger = KotlinLogging.logger("CorlaStateElection")


// TODO cant we merge this into CreateBoulderElection? why is it special ??
// Use OneAudit; redacted ballots are in pools. Cant do IRV because we dont have VoteConsolidators
// this version assume that the redacted groups know how many cards are contained in each
class CorlaStateElection(
    val countyInput: CorlaCountyInput,
    val stateInput: ColoradoInput,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val county: String = countyInput.countyName

    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }
    val infosByName = infoList.associateBy { it.name } //  are the cvr names compatible ?

    val contestBuilders: Map<Int, CCContestBuilder> // make visible for debugging
    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val redactedCvrs: List<AuditableCard>  // redacted cvrs
    val allCards: List<AuditableCard>
    val redactedPools: List<CardPool>
    val mvrs: List<AuditableCard>
    val ncards: Int
    val countyInfo: StrataInfo

    init {
        if (stateInput.strataMap[county] == null) {
            stateInput.strataMap.keys.sorted().forEach { println(it)}
            throw RuntimeException("stateInput.strata doesnt have county $county")
        }
        countyInfo = stateInput.strataMap[county]!!

        val cvrsFromManifest = CvrsFromManifest(variant, countyInput, stateInput, infos)

        contestBuilders = makeContestBuilders(cvrsFromManifest.convertedCvrTabs, cvrsFromManifest.redactedTabs).associate { it.contestId to it}
        contests = makeContests(contestBuilders)

        redactedPools = cvrsFromManifest.redactedPools

        // these are mvrs
        // we should do this in cvrsFromManifest so we can use the manifest ids
        redactedCvrs = cvrsFromManifest.makeSimulatedCards()

        // need to know the phantoms to calculate allCards and Npops
        val phantoms = makePhantomCards(contests, 1)
        logger.info {"made ${phantoms.size} phantom cards"}

        allCards = cvrsFromManifest.convertedCvrs + redactedCvrs + phantoms // in memory
        this.ncards = allCards.size
        val npops = tabulateNpops(allCards, infoList)

        // TODO cvrTabs dont have the irv part, so will fail in the raire library
        val allCardsTabs = tabulateCards(allCards.iterator(), infos)

        contestsUA = makeContestWAs(contests, npops, allCardsTabs, redactedPools, )
        mvrs = addIndexToMvrs(allCards)
        logger.info {"made ${mvrs.size} mvrs"}
    }

    override fun electionInfo() =
        ElectionInfo(countyInput.electionName, variant.auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA

    override fun cardStyles() = null
    override fun cardPools() = redactedPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCardsFromMvrs(mvrs)
    override fun ncards() = ncards

    fun addIndexToMvrs(mvrs: List<AuditableCard>): List<AuditableCard> {
        var cardIndex = 1 // 1 based index
        val result = mutableListOf<AuditableCard>()
        mvrs.forEach { org ->
            result.add(org.copy(index = cardIndex ))
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

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaStateElection(
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
    logger.info {"-------------- createCorlaStateElection ${countyInput.electionName} in $topdir"}

    val election = CorlaStateElection(countyInput, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}