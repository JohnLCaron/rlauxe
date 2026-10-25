package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.writeCountyContestData
import org.cryptobiotic.rlauxe.corlaInput.writeCountyData
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyElection
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariant
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.makeContestWAs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInputWithCvrs
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.util.TransformingIterator
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CorlaStateElection")

// A Corla state election with CVRS. Builds on the CountyElection, which have already been written.
// We want to be able to update the county elections individually, then run this again to recreate the state election
class CorlaStateElection2(
    val topdir: String,
    val stateInput: StatewideInputWithCvrs,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val statePublisher = Publisher(topdir)
    val electionName = stateInput.javaClass.simpleName

    val statePools = mutableListOf<CardPool>()
    val stateStyles = mutableListOf<CardStyle>()
    val countyPools = mutableListOf<CountyPools>()

    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        /*

        val cvrPools = mutableListOf<CountyPools>()
        val contestBuilder = CorlaStateContestInfoBuilder(stateInput)
        val infos = contestBuilder.infos
        val totalPoolTabs = mutableMapOf<Int, ContestTabulation>() // contest id -> total over counties
        val stateNCardsByContest = mutableMapOf<Int, Int>()
        val statePhantoms = mutableMapOf<Int, Int>()
        val stateCvrTabs = mutableMapOf<Int, ContestTabulation>() // total over counties */

        val statePops = mutableMapOf<Int, Int>()
        val stateContests = mutableMapOf<Int, MutableList<Pair<String, Contest>>>() // contestId -> countyName, countyContest

        var totalCvrCardCount = 0

        var countyPoolId = 1
        var statePoolId = 1
        var stateStyleId = 1
        var stateCardIndex = 1

        for (countyName in stateInput.counties()) {
            val countyDir = "$topdir/county/$countyName"
            val result = AuditRecord.readPartialResult(countyDir)
            if (result.isErr) {
                logger.error { "$countyName got error ${result}" }
                continue
            }

            val countyRecord = result.unwrap()

            val statePoolIdMap = mutableMapOf<Int, Int>()
            val countyCardPools = countyRecord.readCardPools()
            if (countyCardPools != null) {
                countyCardPools.forEach { pool ->
                    statePoolIdMap[pool.poolId] = statePoolId
                    statePools.add(pool.copy(poolName = "$countyName-${pool.poolName}", poolId = statePoolId))
                    statePoolId++
                }
            }

            val stateStyleIdMap = mutableMapOf<Int, Int>()
            val countyStyles = countyRecord.readCardStyles() // can this really be null or something other than CardStyle ??
            if (countyStyles != null) {
                countyStyles.forEach { style ->
                    stateStyleIdMap[style.id()] = stateStyleId
                    stateStyles.add(
                        CardStyle(
                            name = "$countyName-${style.name()}", id = stateStyleId,
                            style.possibleContests(), style.hasExactContests()
                        ).setNcards(style.ncards())
                    )
                    stateStyleId++
                }
            }

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

            // transform and write out unsorted manifest
            val countyPublisher = Publisher(countyDir)
            val orgIter = readCardsCsvIterator(countyPublisher.cardManifestFile(), styles = null)
            val transformer = TransformingIterator(orgIter) { org ->
                if (stateStyleIdMap[org.styleId] == null)
                    print("")
                org.copy(
                    location = "$countyName-${org.location}}",
                    index = stateCardIndex++,
                    styleId = stateStyleIdMap[org.styleId]!!,
                    poolId = statePoolIdMap[org.poolId],
                )
            }
            val countyCardCount = writeUnsortedMvrs(countyName, statePublisher, transformer)
            totalCvrCardCount += countyCardCount

            val countyContestTabs = mutableMapOf<Int, ContestTabulation>()

            // have to redo the assertions; TODO IRV
            countyRecord.contests.map { it.contest as Contest }.forEach { contest ->
                val contestList = stateContests.getOrPut(contest.id) { mutableListOf() }
                contestList.add(Pair(countyName, contest))

                countyContestTabs.sumContestTabulationsFromCandVotes(contest.info, contest.votes, contest.Ncast)
            }

            countyPools.add(CountyPools(
                countyName = countyName,
                countyPoolId = countyPoolId++,
                contestTabs = countyContestTabs,
                cardCount = countyCardCount,
                styles = countyStyles!!,
            ))
        }

        val contests = stateContests.map { (contestId, contestList) ->
            var Nc = 0
            var Ncast = 0
            val counties = mutableListOf<String>()

            val info = contestList.first().second.info
            val voteTabulation = ContestTabulation(info)
            contestList.forEach { (countyName, contest) ->
                Nc += contest.Nc
                Ncast += contest.Ncast
                counties.add(countyName)
                contest.votes.forEach { (cand, vote) -> voteTabulation.addVote(cand, vote) }
            }
            info.metadata["CORLAcounties"] = counties.toList().toString()
            /* if (variant.isOA()) {
                val poolTotalCards = allPools.filter { it.hasContest(info.id) }.sumOf { it.ncards() }
                info.metadata["PoolPct"] = if (Nc == 0) "0" else (100.0 * poolTotalCards / Nc).toInt().toString()
            }
            if (variant.phantoms) {
                val phantomsForContest = statePhantoms[info.id] ?: 0
                info.metadata["PhantomPct"] = if (Nc == 0) "" else (100.0 * phantomsForContest / Nc).toInt().toString()
            } */
            Contest(info, voteTabulation.votes, Nc, Ncast)
        }

        // TODO need Irv tabs
        contestsUA = makeContestWAs(contests, statePops, emptyMap(), statePools, variant, hasStyle)

        // stick all the phantoms at the end? or put them by county ??
        // if (variant.phantoms) println("phantomsByContest = ${statePhantoms}")
        require(totalCvrCardCount == stateCardIndex - 1)
        this.ncards = totalCvrCardCount // or totalPoolCardCount? */
    }

    override fun electionInfo() =
        ElectionInfo(electionName, variant.auditType, ncards(), contestsUA.size, true, mvrSource = mvrSource)

    override fun contestsUA() = contestsUA
    override fun cardStyles() = stateStyles
    override fun cardPools() = statePools

    override fun countyCardPools(): List<CountyPools> = countyPools
    //override fun countyCvrPools(): List<CountyPools> = cvrPools

    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = CardIteratorfromCountyMvrs(statePublisher, styles = stateStyles)

    // TODO do we need to munge the mvrs for the card manifest? Add the card styles ??
    override fun cards() = removeVotesFromMvrs(unsortedMvrsExternal(), variant)
    override fun ncards() = ncards
}

fun makeCountyElections(
    topdir: String,
    stateInput: StatewideInputWithCvrs,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
) {
    val contestBuilder = CorlaStateContestInfoBuilder(stateInput)
    stateInput.counties().forEach { countyName ->
        val countyInput = stateInput.corlaCountyInput(countyName)
        if (countyInput == null)
            print("")
        val countyElection = CorlaCountyElection(countyInput!!, stateInput, contestBuilder, mvrSource, hasStyle, variantEnum)
        val countyDir = "$topdir/county/$countyName"

        createElectionRecord(countyElection, topdir = countyDir)
    }
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaStateElection2(
    topdir: String,
    stateInput: StatewideInputWithCvrs,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true, // TODO wtf ??
    variant: ElectionVariantEnum,
    alreadyMadeCounties: Boolean = false,
 ) {
    val stopwatch = Stopwatch()

    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createCorlaStateElection ${stateInput.javaClass.name} in $topdir"}

    if (!alreadyMadeCounties) {
        clearDirectory(Path(topdir))
        makeCountyElections(topdir, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)
    }

    val election = CorlaStateElection2(topdir, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir, fastSampling = true)

    // TODO maybe just chosen counties ?
    writeCountyData(topdir, stateInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, stateInput)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }

    logger.info{"createCorlaCountyElection took $stopwatch"}
}