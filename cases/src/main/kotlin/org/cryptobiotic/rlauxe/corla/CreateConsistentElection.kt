package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

open class CreateConsistentElection (
    val countyElection: CountyContestBuilder,
    val auditType: AuditType,
    val auditdir: String,
    val hasStyle: Boolean,
    val pollingMode: PollingMode?,
    val name: String? = null,
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyPools: List<CountyPoolFromStyle>

    init {
        countyPools = CountyPoolsFromStyles(countyElection.corlaContestBuilders).countyPools

        // have to save the mvrs and generate the cardManifest from them.
        ncards = createAndSaveUnsortedMvrs(countyElection.contests, countyPools, publisher)

        // TODO Npop >= Nc
        val npopMap: Map<Int, Int> = if ((auditType.isPolling() && pollingMode!!.withoutBatches())) {
            countyElection.contests.associate { it.id to ncards } // then the population is the entire set of cards. (wont go well)
        } else {
            // read them back in as an Iterator, so we dont have to read all into memory
            val infos = countyElection.contests.map { it.info() }.associateBy { it.id }
            val auditableCardIter: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles=countyPools)
            // are we handling the batches correctly using mvrs?
            val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
            require(ncards == count)
            manifestTabs.mapValues { it.value.ncardsTabulated }
        }

        contestsUA = ContestWithAssertions.make(countyElection.contests, npopMap, auditType.isClca(), hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name ?: "Corla24$auditType$pollingMode", auditType, ncards(),
            contestsUA.size, pollingMode = pollingMode,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun cardStyles(): List<StyleIF>? = countyPools
    override fun cardPools() = countyPools
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    // TODO verify election creation, verify audit creation
    override fun cards(): CloseableIterator<AuditableCardM> {
        val unsortedMvrs: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = null)

        return TransformingIterator(unsortedMvrs) { cardm ->
            when {
                cardm.phantom -> cardm
                auditType.isClca() -> cardm.copy(poolId = null)
                (auditType.isPolling() && pollingMode!!.withoutBatches()) -> cardm.copy(
                    contestIds = IntArray(0), // might be safer to provide a function to remove all three
                    styleName = "OneBatch",
                    poolId = 0
                )

                (auditType.isPolling()) -> cardm.copy(contestIds = IntArray(0))
                else -> throw IllegalStateException("Unknown what to do with mvr: $cardm")
            }
        }
    }

    // StartAuditFirstRound will create the sorted MVRs
    override fun createUnsortedMvrsExternal() = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = null)
    override fun createUnsortedMvrsInternal() = null
}

// CvrsToCardsAddStyles is random, so in order to match the mvrs and cvrs, we must generate the mvrs first,
// then create manifest from them. This is nonstandard, so we will do it here.
// Could put this into createAuditRecord(reverse = true) ??
// return number of cards

// TODO wed like to add a style name, but becuse we are using cvrs instead of mvrs, we have no place to put it without setting a pool Id.
//   which maybe we could but seems lame
fun createAndSaveUnsortedMvrs(
    contests: List<ContestIF>,
    cardPools: List<CardPoolIF>,
    publisher: Publisher
): Int {
    val unsortedMvrIterator = MvrsToCardStylesIterator(
        Closer(CvrIteratorfromPools(cardPools.iterator())),
        cardPools,
        makePhantomCvrs(contests), // yes there are phantoms, heres where we need the contests' Nphantoms
    )
    validateOutputDirOfFile(publisher.unsortedMvrsFile())

    writeCardCsvFile(unsortedMvrIterator, publisher.unsortedMvrsFile())
    logger.info { "CreateColoradoElection unsortedMvrsFile to ${publisher.unsortedMvrsFile()}" }

    return unsortedMvrIterator.cardIndex // card count
}

// dont load into memory all at once, just one pool at a time
// this is random, cant do more than once. must do mvrs first
class CvrIteratorfromPools(val cardPoolIter: Iterator<CardPoolIF>) : Iterator<Cvr> {
    var innerIter: CardsFromPool

    init {
        innerIter = CardsFromPool(cardPoolIter.next())
    }

    override fun next(): Cvr {
        return innerIter.next()
    }

    override fun hasNext(): Boolean {
        if (innerIter.hasNext()) return true
        if (cardPoolIter.hasNext()) {
            innerIter = CardsFromPool(cardPoolIter.next())
            return hasNext()
        }
        return false
    }
}

// these are chosen randomly, so in order for mvrs and cvrs to match, the cvrs have to be made from the mvrs.
class CardsFromPool(val cardPool: CardPoolIF) : Iterator<Cvr> {
    val cvrs: Iterator<Cvr>

    init {
        val poolVunders = cardPool.possibleContests().map { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()

        cvrs = makeCvrsForOnePool(
            poolVunders,
            cardPool.poolName,
            poolId = cardPool.poolId,
            cardPool.hasExactContests()
        ).iterator()
    }

    override fun next() = cvrs.next()
    override fun hasNext() = cvrs.hasNext()
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createConsistentElection(
    topdir: String,
    auditdir: String,
    pollingMode: PollingMode? = null,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String? = null,
) {
    val stopwatch = Stopwatch()

    val countyElection = CountyContestBuilder()

    val election = if (creation.auditType.isClca())
            CreateConsistentElection(countyElection, creation.auditType, auditdir, pollingMode=null, name=name,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent)
        else
            CreateColoradoPolling(countyElection, auditdir, pollingMode!!) // TODO hasExact = false ??

    createElectionRecord(election, auditDir = auditdir, clear = false)
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir)

    writeCountyData(topdir, Colorado2024Input.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, Colorado2024Input.countyContestMap)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorla took $stopwatch" }
    }
}

