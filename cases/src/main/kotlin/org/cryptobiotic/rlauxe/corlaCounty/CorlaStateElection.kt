package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.auditcenter.BuildCorlaContests
import org.cryptobiotic.rlauxe.auditcenter.CardIteratorfromCountyMvrs
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.auditcenter.writeCountyData
import org.cryptobiotic.rlauxe.auditcenter.writeUnsortedMvrs
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CorlaStateElection")

class CorlaStateElection(
    val topdir: String,
    val stateInput: ColoradoInput,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val publisher = Publisher(topdir)
    val electionName = stateInput.javaClass.name

    val allCardStyles = mutableListOf<StyleIF>()
    val allCardPools = mutableListOf<CardPool>()
    val countyPools = mutableListOf<CountyPools>()
    val cvrPools = mutableListOf<CountyPools>()
    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        val contestBuilder = BuildCorlaContests(stateInput)
        val infos = contestBuilder.infos
        val infosByName = infos.mapKeys{ it.value.name }

        // val countyTabMap = stateInput.countyTabsAllContests()
        val totalPoolTabs = mutableMapOf<Int, ContestTabulation>() // total over counties

        var totalCvrCardCount = 0
        val totalStateTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
        var countyPoolId = 1

        stateInput.counties().forEach { countyName ->
            val countyInput = CorlaCounty2020Input(countyName)

            if (stateInput.strataMap[countyName] == null) {
                stateInput.strataMap.keys.sorted().forEach { println(it) }
                throw RuntimeException("stateInput.strata doesnt have county $countyName")
            }
            val countyInfo = stateInput.strataMap[countyName]!!
            val countyPopulation = countyInput.countyPopulation

            val cvrsFromManifest = CvrsFromManifest(variant, countyInput, stateInput, infos, stateElection =  true)

            val totalCountyTabs = mutableMapOf<Int, ContestTabulation>() // total over counties
            totalCountyTabs.sumContestTabulations(cvrsFromManifest.convertedCvrTabs)
            totalCountyTabs.sumContestTabulations(cvrsFromManifest.redactedTabs)
            totalStateTabs.sumContestTabulations(totalCountyTabs)

            /*
            val countyContestBuilders = makeContestBuilders(
                cvrsFromManifest.convertedCvrTabs,
                cvrsFromManifest.redactedTabs
            ).associate { it.contestId to it }

            val contests = makeContests(countyContestBuilders) */

            val redactedPools = cvrsFromManifest.redactedPools

            // these are mvrs
            val redactedCvrs = cvrsFromManifest.makeSimulatedCards()

            // need to know the phantoms to calculate allCards and Npops
            // val phantoms = makePhantomCards(contests, 1)
            // logger.info { "made ${phantoms.size} phantom cards" }

            val allCards = cvrsFromManifest.convertedCvrs + redactedCvrs // + phantoms // in memory
            // write them out while we have them in memory
            writeUnsortedMvrs(countyName, publisher, Closer(allCards.iterator()))
            totalCvrCardCount += allCards.size

            // TODO to use fastSampleing, all cvrs must have stylesIds (no fromCvr or phantoms)
            //  styles cant be optional; all styles must be in styleMap when reading
            val countyCardStyles = cvrsFromManifest.countyCardStyles()
            allCardStyles.addAll(countyCardStyles)

            // TODO do we have cvrPools ?
            // cvrPools.add(CountyPools(countyName, countyPoolId, cvrTabs, countyPopulation, countyCardStyles))
            countyPools.add(
                // make the county pool from auditcenter tabs plus cvr cardStyles and ncards
                // TODO are we factoring out the style information, or leaving "fromCvr"?
                CountyPools(countyName, countyPoolId++, totalCountyTabs, countyPopulation, countyCardStyles)
            )

            allCardPools.addAll(redactedPools)
            // totalPoolTabs.sumContestTabulations(contestTabs)
        }

        val contests = totalStateTabs.map { (contestId, contestTab) ->
            Contest(infos[contestId]!!, contestTab.votes, contestTab.ncardsTabulated, contestTab.ncardsTabulated)
        }

        // probably a bad idea, in that it would create phantoms for what is (probably) the redacted ballots
        // eg Boulder went from 66393 to 251 missing votes (2646 to 25 missing cards) when redacted ballots were added
        // val ncast: Map<Int, Int>  = totalPoolTabs.mapValues { it.value.ncards() }
        // val contests = contestBuilder.contests(ncast)
        /* just leave it as Ncast = Nc, then the diff goes into the undervote
        val contests = contestBuilder.contests(emptyMap<Int, Int>())
        contests.forEach {
            val contestCvrTab = totalCvrTabs[it.id]
            if (contestCvrTab != null) {
                it.info().metadata["CvrNcards"] = contestCvrTab.ncards().toString()
                it.info().metadata["CvrNvotes"] = contestCvrTab.nvotes().toString()
                it.info().metadata["CvrNundervotes"] = contestCvrTab.undervotes().toString()
            }
        } */
        // where do we get these? difference between the cvr card counts and the contest.Nc = round.contestBallotCardCount
        // can put them is a seperate pool as long as you include them in the unsorted iterator
        // val phantoms = makePhantomCards(contests, 0) // TODO

        this.ncards = totalCvrCardCount // or totalPoolCardCount?

        // probably should read cards back in ??
        val npops = emptyMap<Int, Int>() // tabulateNpops(allCards, infoList)

        contestsUA = contests.map {
            // use strataSize or Nc as population size
            // val NpopIn = if (isUniform) it.info().metadata["CORLAstrataNcards"]!!.toInt() else null // TODO
            ContestWithAssertions(it, true, hasStyle, NpopIn = npops[it.id]).addStandardAssertions()
        }

    }

    override fun electionInfo() =
        ElectionInfo(electionName, variant.auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)

    override fun contestsUA() = contestsUA
    override fun cardStyles() = allCardStyles
    override fun cardPools() = allCardPools
    override fun countyCardPools(): List<CountyPools> = countyPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(publisher, styles = allCardStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = createCardsFromMvrs(unsortedMvrsExternal())
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

    fun createCardsFromMvrs(mvrs: CloseableIterator<AuditableCard>): CloseableIterator<AuditableCard> {
        // remove cvrs for cards in the pools
        val mvrIter = Closer(mvrs)
        val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
            if (org.poolId != null && variant.isOA()) AuditableCard.removeVotes(org) else org
        }
        return transformer
    }

////////////////////////////////////////////////////////////////////////////////////////////////
//// contest building

    /*
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
    } */
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaStateElection(
    topdir: String,
    stateInput: ColoradoInput,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true, // TODO wtf ??
    variant: ElectionVariantEnum,
 ) {
    val stopwatch = Stopwatch()

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createCorlaStateElection ${stateInput.javaClass.name} in $topdir"}

    val election = CorlaStateElection(topdir, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir, fastSampling = false)

    // TODO maybe just chosen counties ?
    writeCountyData(topdir, stateInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, stateInput)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}