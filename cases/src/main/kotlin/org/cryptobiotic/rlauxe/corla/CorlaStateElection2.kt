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
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.CardHeader
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsv
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.util.TransformingIterator
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

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
    val stateStyleMap: Map<Int, StyleIF>
    val countyAcPools = mutableListOf<CountyPools>()
    val vunderPoolMap: Map<Int, VunderPool>

    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        // write the card manifest here
        // TODO put inside a use clause ??
        val publisher = Publisher(topdir)
        validateOutputDirOfFile(publisher.cardManifestFile())
        val cardManifestWriter: OutputStreamWriter = FileOutputStream(publisher.cardManifestFile()).writer()
        cardManifestWriter.write(CardHeader)

        val stateContests = mutableMapOf<Int, MutableList<Pair<String, Contest>>>() // contestId -> countyName, countyContest

        var totalCvrCardCount = 0

        var countyPoolId = 1
        var statePoolId = 1
        var stateStyleId = 1
        var stateCardIndex = 1
        val npopMap = mutableMapOf<Int, Int>()

        for (countyName in stateInput.counties()) {
            println(countyName)
            val countyDir = "$topdir/county/$countyName"
            val result = AuditRecord.readPartialResult(countyDir)
            if (result.isErr) {
                logger.error { "$countyName got error ${result}" }
                continue
            }
            val countyAuditRecord = result.unwrap()

            val statePoolIdMap = mutableMapOf<Int, Int>()
            val countyCardPools = countyAuditRecord.readCardPools()
            if (countyCardPools != null) {
                countyCardPools.forEach { pool ->
                    statePoolIdMap[pool.poolId] = statePoolId
                    statePools.add(pool.copy(poolName = "$countyName-${pool.poolName}", poolId = statePoolId))
                    statePoolId++
                }
            }

            val stateStyleIdMap = mutableMapOf<Int, Int>()
            val countyStyles = countyAuditRecord.readCardStyles()!!
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
            val countyStyleMap = countyStyles.associateBy { it.id() }

            // read county cvrs (already canonicalized) and write out unsorted card manifest
            // TODO this is the card manifest; for the mvrs, we need to simulate the cvrs when they dont exist...
            val countyPublisher = Publisher(countyDir)
            val orgIter = readCardsCsvIterator(countyPublisher.cardManifestFile(), styles = null)
            val countyCardsIter = TransformingIterator(orgIter) { org ->
                // convenient to count population here; pools can make pop > ncards
                if (countyStyleMap[org.styleId] == null)
                    logger.error{"cant find ${org.styleId} in countyStyleMap"}
                val style = countyStyleMap[org.styleId]!!
                style.possibleContests().forEach {
                    val accum = npopMap.getOrDefault(it, 0)
                    npopMap[it] = accum + 1
                }

                if (stateStyleIdMap[org.styleId] == null)
                    logger.error{"cant find ${org.styleId} in stateStyleIdMap"}

                org.copy(
                    location = "$countyName-${org.location}",
                    index = stateCardIndex++,
                    styleId = stateStyleIdMap[org.styleId]!!,
                    poolId = statePoolIdMap[org.poolId], // TODO make sure if theres a poolId in dounty cardManifestFile
                )
            }

            // append county cards to the cardManifest
            var countyCardCount = 0
            countyCardsIter.use { cardIter ->
                while (cardIter.hasNext()) {
                    cardManifestWriter.write(writeCardCsv(cardIter.next()))
                    countyCardCount++
                }
            }
            totalCvrCardCount += countyCardCount

            // these are the ac tabs
            val countyContestTabs = mutableMapOf<Int, ContestTabulation>()
            countyAuditRecord.contests.map { it.contest as Contest }.forEach { contest ->
                val contestList = stateContests.getOrPut(contest.id) { mutableListOf() }
                contestList.add(Pair(countyName, contest))
                countyContestTabs.sumContestTabulationsFromCandVotes(contest.info, contest.votes, contest.Ncast)
            }

            // used in viewer
            countyAcPools.add(CountyPools(
                countyName = countyName,
                countyPoolId = countyPoolId++,
                contestTabs = countyContestTabs,
                cardCount = countyCardCount,
                styles = countyStyles,
            ))
        } // end loop over counties
        cardManifestWriter.close()

        val contests = stateContests.map { (contestId, countyList) ->
            var Ncast = 0
            val counties = mutableListOf<String>()

            // heres where we set the statewide contests to the sum of the county contests
            // may not be the reported vote; eg Baca is missing.
            // however, we used the reported vote to create a missing pool; I think those will be the same.
            // TODO track down where they differ ....

            val info = countyList.first().second.info // all infos are identical
            val voteTabulation = ContestTabulation(info)
            countyList.forEach { (countyName, contest) ->
                Ncast += contest.Ncast
                counties.add(countyName)
                contest.votes.forEach { (cand, vote) -> voteTabulation.addVote(cand, vote) }
            }
            info.metadata["CvrCounties"] = counties.toList().toString() // could be different than CorlaCounties from countyTabs ??
            /* if (variant.isOA()) {
                val poolTotalCards = allPools.filter { it.hasContest(info.id) }.sumOf { it.ncards() }
                info.metadata["PoolPct"] = if (Nc == 0) "0" else (100.0 * poolTotalCards / Nc).toInt().toString()
            }
            if (variant.phantoms) {
                val phantomsForContest = statePhantoms[info.id] ?: 0
                info.metadata["PhantomPct"] = if (Nc == 0) "" else (100.0 * phantomsForContest / Nc).toInt().toString()
            } */
            // Nc should be from auditcenter, mcontest.nc = round.contestBallotCardCount
            var Nc = stateInput.contestNc[info.name]!!
            if (Ncast > Nc) {
                // why is Ncast > Nc ??
                logger.warn{"${info.name} (${info.id}): Ncast $Ncast > $Nc Nc; set Nc = Ncast for now"}
                // should actually be Ncast = Nc, but only if you exclude Gunnison
                Nc = Ncast
            }
            Contest(info, voteTabulation.votes, Nc, Ncast) // phantoms = Nc - Ncast
        }

        // TODO need Irv tabs
        contestsUA = makeContestWAs(contests, npopMap, emptyMap(), statePools, variant, hasStyle)

        // stick all the phantoms at the end? or put them by county ??
        // if (variant.phantoms) println("phantomsByContest = ${statePhantoms}")
        require(totalCvrCardCount == stateCardIndex - 1)
        this.ncards = totalCvrCardCount // or totalPoolCardCount? */

        stateStyleMap = stateStyles.associateBy { it.id }
        // this can only be use once, else must be reinitialized....
        vunderPoolMap = statePools.map { cardPool ->
            val vunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
            VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        }.associateBy{ it.poolId }
    }

    override fun electionInfo() =
        ElectionInfo(electionName, variant.auditType, ncards(), contestsUA.size, true, mvrSource = mvrSource)

    override fun contestsUA() = contestsUA
    override fun cardStyles() = stateStyles
    override fun cardPools() = statePools

    override fun countyAuditcenterPools(): List<CountyPools> = countyAcPools // used by viewer
    //override fun countyCvrPools(): List<CountyPools> = cvrPools

    //      and add the mvr simulations by reading the cardManifest ??
    //      see VunderBatches CreateCardsForCountyPools used by CountyElectionSimCvrs
    //      see VunderPool used by CanonicalCvrsFromManifest
    // TODO if these are the mvrs, we need to simulate the cvrs when they dont exist...
    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = null // CardIteratorfromCountyMvrs(statePublisher, styles = stateStyles)

    // convert cvr to mvr on the fly
    override fun cardToMvrTransformer(): ((AuditableCard) -> AuditableCard) {
        return { card ->
            if (card.poolId == null) card else {
                val style = stateStyleMap[card.styleId]!!
                card.setStyle(style)
                val vunderPool = vunderPoolMap[card.poolId]!! // the poolId should match a VunderPool
                vunderPool.simulatePooledCard(card)
            }
        }
    }

    override fun cards() = null // removeVotesFromMvrs(unsortedMvrsExternal(), variant)
    override fun ncards() = ncards
}

// create an iterator over all the cards unsortedMvrsDirectory
class CardIteratorfromCountyMvrs(
    publisher: Publisher,
    val styles: List<StyleIF>
) : CloseableIterator<AuditableCard> {

    val dir = publisher.unsortedMvrsDirectory()
    val path = Path(dir)
    val countyPaths: List<Path> = path.listDirectoryEntries().filter { !it.isDirectory() && it.fileName.toString().endsWith(".csv")}

    val counties = countyPaths.iterator()
    var innerIter = readCardsCsvIterator(counties.next().toString(), styles = styles)  // TODO do we need styles ??

    override fun next(): AuditableCard {
        return innerIter.next()
    }

    override fun hasNext(): Boolean {
        if (innerIter.hasNext()) return true
        if (counties.hasNext()) {
            innerIter = readCardsCsvIterator(counties.next().toString(), styles = styles)
            return hasNext()
        }
        return false
    }

    override fun close() {
        // NOOP
    }
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
        if (countyInput == null) {
            stateInput.corlaCountyInput(countyName)
            logger.error { "cant find countyInput for $countyName"}
        }
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

    validateOutputDir(Path(topdir))
    if (!alreadyMadeCounties) {
        // clearDirectory(Path(topdir))
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

/*
auditrecord/
    round1/ (runAudit)
        samplePrns1.json        prns chosen for round1
        sampleMvrs1.csv         mvrs chosen for round1
    cardManifest.csv            card manifest for state                             2.7 G
    sortedCards.csv             card manifest sorted by PRN (createAudit)           2.8 G
    ...
county/                                                                              .855 G
    countyName/
        auditrecord/
            cardManifest.csv    card manifest for county; no prn or poolId, redacted have style no votes
           ...
cvrs/                                                                               1.1G (100 M zipped)
    countyName-Redacted.csv     original cvr file from county
    countyName-Report.txt
    ...
private/                                                                            3.7 G
    sortedMvrs.csv              all mvrs, sorted by PRN (createAudit)               2.8 G
sortChunks/                                                                         2.8 G
    sorted-cards-part-k.csv
countyContestData.csv
countyData.csv
fastSampling.bin                fastest for what-if sampling (createAudit)           .141 G
logs.logs
sortedCards.proto               faster to read (createAudit)                        2.6 G

 */