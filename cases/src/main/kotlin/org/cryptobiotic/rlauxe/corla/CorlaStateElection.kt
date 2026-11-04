package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.corlaInput.writeCountyContestData
import org.cryptobiotic.rlauxe.corlaInput.writeCountyData
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyElection
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyElectionNoCvrs
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariant
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.makeContestWAs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.bin.writeFastSamplingCards
import org.cryptobiotic.rlauxe.persist.csv.AddStyles
import org.cryptobiotic.rlauxe.persist.csv.CardHeader
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsv
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.util.TransformingIterator
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CorlaStateElection")

// A Corla state election with CVRS. Builds on the CountyElection, which have already been written.
// We want to be able to update the county elections individually, then run this again to recreate the state election
class CorlaStateElection(
    val topdir: String,
    val stateInput: StatewideInput,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
): ElectionBuilder {
    val variant = ElectionVariant(variantEnum)
    val electionName = stateInput.javaClass.simpleName

    val statePools = mutableListOf<CardPool>()
    val stateStyles = mutableListOf<StyleIF>()
    val stateStyleMap: Map<Pair<String, Int>, StyleIF>
    val countyAcPools = mutableListOf<CountyPools>()
    // val countyCvrPools = mutableListOf<CountyPools>()
    val vunderPoolMap: Map<Int, VunderPool>
    val countyPops = mutableMapOf<String, MutableMap<Int,Int>>() // county -> contest -> population

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
        var stateCardIndex = 1

        for (countyName in stateInput.counties()) {
            println(countyName)
            if (countyName == "Baca")
                print("")
            val countyDir = "$topdir/county/$countyName"
            val result = AuditRecord.readPartialResult(countyDir)
            if (result.isErr) {
                logger.error { "$countyName got error ${result}" }
                continue
            }
            val countyAuditrecord = result.unwrap()

            //val statePoolIdMap = mutableMapOf<Int, Int>()
            val countyCardPools = countyAuditrecord.readCardPools()
            if (countyCardPools != null) statePools.addAll(countyCardPools)
            /* if (countyCardPools != null) {
                countyCardPools.forEach { pool ->
                    statePoolIdMap[pool.poolId] = statePoolId
                    statePools.add(pool.copy(poolName = "$countyName-${pool.poolName}", poolId = statePoolId))
                    statePoolId++
                }
            } */

            // val stateStyleIdMap = mutableMapOf<Int, Int>()
            val countyStyles = countyAuditrecord.readCardStyles()
            stateStyles.addAll(countyStyles)
            /* countyStyles.forEach { style ->
                stateStyleIdMap[style.id()] = stateStyleId
                stateStyles.add(
                    CardStyle(countyName,
                        name = "$countyName-${style.name()}", id = stateStyleId,
                        style.possibleContests(), style.hasExactContests()
                    ).setNcards(style.ncards())
                )
                stateStyleId++
            } */
            val countyStyleMap = countyStyles.associateBy { it.id() }

            // read county cvrs (already canonicalized) and write out unsorted card manifest
            // not adding the styles, but existing style and pools ids are correct
            // need to add styles to tabulate ??

            val countyCvrTabs = mutableMapOf<Int, ContestTabulation>()
            val countyPop = mutableMapOf<Int, Int>() // contestId -> ncards
            countyPops[countyName] = countyPop
            val countyPublisher = Publisher(countyDir)
            val countyCardsIter = readCardsCsvIterator(countyPublisher.cardManifestFile()) { org ->

                // convenient to count population here; pools can make pop > ncards
                if (countyStyleMap[org.styleId] == null)
                    logger.error{"cant find ${org.styleId} in countyStyleMap"}
                val style = countyStyleMap[org.styleId]!!
                style.possibleContests().forEach { contestId ->
                    val accum = countyPop.getOrDefault(contestId, 0)
                    countyPop[contestId] = accum + 1
                }

                // the only change is to reindex it.
                org.copy(index = stateCardIndex++)
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
            countyAuditrecord.contests.map { it.contest as Contest }.forEach { contest ->
                val contestList = stateContests.getOrPut(contest.id) { mutableListOf() }
                contestList.add(Pair(countyName, contest))
                countyContestTabs.sumContestTabulationsFromCandVotes(contest.info, contest.votes, contest.Ncast)
            }

            // used in viewer
            countyAcPools.add( CountyPools(
                countyName = countyName,
                countyPoolId = countyPoolId++,
                contestTabs = countyContestTabs,
                cardCount = countyCardCount,
                styles = countyStyles,
            ))

            // could also do the cvr tabulation and output countyCvrPools
            // but you can only tabulate the unredacted cvrs ....
            /* countyCvrPools.add( CountyPools(
                countyName = countyName,
                countyPoolId = countyPoolId++,
                contestTabs = countyContestTabs,
                cardCount = countyCardCount,
                styles = countyStyles,
            )) */

        } // end loop over counties
        cardManifestWriter.close()

        val contests = stateContests.map { (contestId, countyList) ->
            var Ncast = 0
            val counties = mutableListOf<String>()

            if (contestId == 641)
                print("")

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

        // sum over counties
        // TODO IRV need Irv tabs
        val npopMap = tabulateNpops(countyPops)

        contestsUA = makeContestWAs(contests, npopMap, emptyMap(), statePools, variant, hasStyle)

        // stick all the phantoms at the end? or put them by county ??
        // if (variant.phantoms) println("phantomsByContest = ${statePhantoms}")
        require(totalCvrCardCount == stateCardIndex - 1)
        this.ncards = totalCvrCardCount // or totalPoolCardCount? */

        stateStyleMap = stateStyles.associate{ Pair(it.county(), it.id()) to it }
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
    override fun countyCvrPools() = null // countyCvrPools

    //      and add the mvr simulations by reading the cardManifest ??
    //      see VunderBatches CreateCardsForCountyPools used by CountyElectionSimCvrs
    //      see VunderPool used by CanonicalCvrsFromManifest
    // TODO if these are the mvrs, we need to simulate the cvrs when they dont exist...
    override fun unsortedMvrsInternal() = null
    override fun unsortedMvrsExternal() = null // CardIteratorfromCountyMvrs(statePublisher, styles = stateStyles)

    // convert card to mvr on the fly
    override fun cardToMvrTransformer(): ((AuditableCard) -> AuditableCard) {
        return { card ->
            if (card.poolId == null) card else {
                val style = stateStyleMap[Pair(card.county, card.styleId)]!!
                card.setStyle(style)
                val vunderPool = vunderPoolMap[card.poolId]!! // the poolId should match a VunderPool
                vunderPool.simulatePooledCard(card)
            }
        }
    }

    override fun cards() = null // removeVotesFromMvrs(unsortedMvrsExternal(), variant)
    override fun ncards() = ncards
}

fun makeCountyElections(
    topdir: String,
    stateInput: StatewideInput,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean, // TODO
    variantEnum: ElectionVariantEnum,
) {
    val contestBuilder = CorlaStateContestInfoBuilder(stateInput)
    stateInput.counties().forEach { countyName ->
        val countyDir = "$topdir/county/$countyName"
        val countyInput = stateInput.corlaCountyInput(countyName)
        requireNotNull(countyInput)
        val countyElection = if (countyInput.hasCvrs()) {
                CorlaCountyElection(countyInput, stateInput, contestBuilder, mvrSource, hasStyle, variantEnum)
        } else {
            logger.info { "cant find countyInput for $countyName"}
            // can we still build it without the cvrs ??
            CorlaCountyElectionNoCvrs(countyInput, stateInput, contestBuilder, mvrSource, hasStyle)
        }
        createElectionRecord(countyElection, topdir = countyDir)

        //  write fastCards with the unsorted manifest, for use in estimating county sample sizes
        val countyPublisher = Publisher(countyDir)
        val styles = countyElection.cardStyles()
        val csvIter = readCardsCsvIterator(countyPublisher.cardManifestFile(), AddStyles(styles))
                // fun writeFastSamplingCards(cards: CloseableIterator<AuditableCard>, styles: List<StyleIF>, filenameOut: String, limit: Int? = null): Int {
        writeFastSamplingCards(csvIter, styles, countyPublisher.fastSamplingFile())
    }
}

////////////////////////////////////////////////////////////////////
// variant.Sim: CLCA with simulated cvrs for the redacted groups
// variant.Styles: OneAudit with redacted cards in a multiple pools by style
// variant.OnePool: OneAudit with redacted cards in a single pool
// variant.Phantoms: OneAudit with redacted cards set to isPhantom

fun createCorlaStateElection(
    topdir: String,
    stateInput: StatewideInput,
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

    val election = CorlaStateElection(topdir, stateInput, mvrSource = mvrSource, hasStyle = hasStyle, variant)

    createElectionRecord(election, topdir = topdir)

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir, externalSortDir = topdir, fastSampling = true)

    // TODO maybe just chosen counties ?
    writeCountyData(topdir, stateInput.countyStrataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, stateInput, election.countyPops)

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

/*
County contest
|           field |                       value |                                                                         description |
| --------------- | --------------------------- | ----------------------------------------------------------------------------------- |
|          target |                             |                                                         is a Corla targeted contest |
|       NCounties |                     El Paso |                                  number of counties, or the county name if only one |
|              id |                         546 |                                                                  contest identifier |
|            name | State Senator - District 10 |                                                                        contest name |
|          margin |         0.17389678384442783 |                                   voteMargin / population size (smallest assertion) |
|     corlaStrata |                      382583 |                         Corla strata size (from contestRoundFile ballot_card_count) |
|      population |                       88174 |                                                     Nc (hasStyle) or Npop (noStyle) |
|              nc |                       88174 |                                      trusted upper bound on contest number of cards |
|    corlaEstMvrs |                          -1 |            Corla estimated nmvrs (from contestRoundFile estimated_samples_to_audit) |
|   corlaHaveMvrs |                        1195 | single county: count mvrs in county (from comparison file), else min rate algorithm |
|       corlaRisk |       7.276349262910895E-11 |                                                        estimated Corla uniform risk |
| corlaVoteMargin |                       15345 |                            Corla margin in votes (from contestRoundFile min_margin) |
|      voteMargin |                       15345 |                                           (winner-loser) votes (smallest assertion) |
|        haveMvrs |                         222 |                                   number of mvrs that were sampled for this contest |
|         estMvrs |                          37 |                                                     estimated number of mvrs needed |
|      estNewMvrs |                         165 |                                                        estimated new samples needed |
|         estRisk |        3.429879037949587E-9 |                                                                      estimated risk |
Contest 'State Senator - District 10' (546) PLURALITY voteForN=1 votes={0=47416, 1=32071, 2=4613} undervotes=4068, winners=[0] Nc=88174 Nphantoms=6 Nu=4068 sumVotes=84100 Npop=88242 hasStyle=true
   minAssertion= 0/1 votes=47416/32071 diff=15345 dilutedMargin=0.173897 noerror=0.5476

   0 'Larry G. Liston': votes=47416 (56.38%)  (winner)
   1 'Randi McCallian': votes=32071 (38.13%)
   2 'Heather Johnson': votes=4613 (5.49%)
    Total=84100
Metadata
  CORLAhaveMvrs=1195
  CORLAstrataNcards=382583
  CORLAballotCardCount=382583
  CORLAcontestBallotCardCount=88174
  CORLAauditReason=opportunistic_benefits
  CORLAmarginInVotes=15345
  CanonicalCounties=[El Paso]
  CORLAestMvrs=167
  CORLAcountyMvrs=285
  CORLAstatewideNmvrs=0
  CvrCounties=[El Paso]
 */