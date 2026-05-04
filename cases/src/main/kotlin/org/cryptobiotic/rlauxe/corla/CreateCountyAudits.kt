package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterator
import org.cryptobiotic.rlauxe.audit.MvrsToCardsWithBatchNameIterator
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromBallotStyle
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.collections.associateBy
import kotlin.collections.flatten
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("CreateCountyAudits")

private val debugUndervotes = true

class CreateCountyAudits(
        val topdir: String,
        val wantCounties: List<String>,
    ) {
    val corlaInput = Colorado2024Input

    val countyContestBuilders: Map<String, List<CountyContestBuilder>> =
        makeCountyContestBuilders(corlaInput, wantCounties)

    // hopefully all the infos are the same
    val infoMap = countyContestBuilders.values.map { it.map { it.info } }.flatten().associateBy { it.id }
    val allCardPools = emptyList<OneAuditPoolFromBallotStyle>() // convertPrecinctsToCardPools(corlaInput.precinctFile, infoMap, true)

    val electionBuilders = mutableListOf<CorlaElectionBuilder>()

    init {
        countyContestBuilders.forEach { (countyName, builders) ->
            val countyCardPools = allCardPools.filter { it.poolName.lowercase().startsWith(countyName.lowercase()) }

            // set contest total cards as sum over pools
            builders.forEach { it.setTotalCardsFromPools(countyCardPools) }

            var countZero = 0
            builders.forEach {
                if (it.totalCards == 0) {
                    countZero++
                    println(" ${it.info.id} ${it.info.name}")
                }
            }
            if (countZero > 0) println("$countZero/${builders.size} contests with no precinct data for county $countyName")

            // estimate undervotes based on each precinct having a single ballot style


            // adjust so contest 0 has 0 undervotes. needed since we dont know number of cards in precincts or missing
            // distributeExpectedOvervotes(builders[0], countyCardPools)
            // builders.forEach { it.adjustPoolInfo(countyCardPools) }

            /* if (debugUndervotes) {
                val undervotesByContest = mutableMapOf<CountyContestBuilder, Int>() // contestId ->
                builders.forEach {
                    undervotesByContest[it] = it.expectedPoolNCards() - it.poolTotalCards()
                }
                undervotesByContest.forEach { (cb, before) ->
                    val needAfter = cb.expectedPoolNCards() - cb.poolTotalCards()
                    println("  ${cb.contestId} $before $needAfter ")
                }
            } */

            val batches = countyCardPools
            val contests = makeContests(builders)

            // have to save the mvrs and generate the cardManifest from them
            val auditdir = "$topdir/$countyName/audit"
            clearDirectory(Path(auditdir))
            val publisher = Publisher(auditdir)

            val ncards = createAndSaveUnsortedMvrs(contests, countyCardPools, publisher)

            // read them back in as an Iterator, so we dont have to read all into memory
            val infos = contests.map { it.info() }.associateBy { it.id }
            val mvrs: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.unsortedMvrsFile())
            val auditableCardIter: CloseableIterator<AuditableCard> =
                MergeBatchesIntoCardManifestIterator(mvrs, batches)

            // are we handling the batches correctly using mvrs?
            val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
            require(ncards == count)
            val npopMap: Map<Int, Int> = manifestTabs.mapValues { it.value.ncardsTabulated }

            val contestsUAs = ContestWithAssertions.make(contests, npopMap, isClca = true)

            electionBuilders.add(CorlaElectionBuilder(countyName, auditdir, contestsUAs, ncards, null, countyCardPools))
        }
    }

}

class CorlaElectionBuilder(
    val countyName: String,
    val auditdir: String,
    val contestsUA: List<ContestWithAssertions>,
    val ncards: Int,
    val cardStyles: List<StyleIF>?,
    val cardPools: List<OneAuditPoolFromBallotStyle>,
) : ElectionBuilder {

    override fun electionInfo(): ElectionInfo {
        val useName = countyName.replace(" ", "_")
        return ElectionInfo(
            useName, AuditType.CLCA, ncards,
            contestsUA.size, pollingMode = null
        )
    }


    override fun contestsUA() = contestsUA

    override fun cards(): CloseableIterator<CardWithBatchName> {
        val publisher = Publisher(auditdir)
        val unsortedMvrs = readCardsCsvIterator(publisher.unsortedMvrsFile())
        return TransformingIterator(unsortedMvrs) { mvr ->
            when {
                mvr.phantom -> mvr
                else -> mvr.copy(poolId = null, styleName = CardStyle.fromCvr)
            }
        }
    }

    override fun ncards() = ncards
    override fun cardStyles() = cardStyles
    override fun cardPools() = cardPools
    override fun createUnsortedMvrsInternal() = null
    override fun createUnsortedMvrsExternal() = null
    override fun toString(): String {
        return "CorlaElectionBuilder(countyName='$countyName', auditdir='$auditdir')"
    }

}


fun makeContests(builders: List<CountyContestBuilder>): List<ContestIF> {
    val infoList = builders.map { it.info }.sortedBy { it.id }
    val builderMap = builders.associateBy { it.info.id }
    println("ncontests with info = ${infoList.size}")

    return infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
        val builder = builderMap[info.id]!!
        val candVotes = builder.candVotes.filter { info.candidateNames.contains(it.candName) } // remove Write-Ins
        val votes = candVotes.map { Pair(it.candId, it.vote) }.toMap()
        val totalVotes = votes.map {it.value}.sum()
        val ncards = max(builder.totalCards, totalVotes)
        // info.metadata["PoolPct"] = (100.0 * builder.poolTotalCards() / ncards).toInt()
        // TODO is there an Nc foreach county ??
        Contest(info, votes, ncards, ncards)
    }
}

fun makeCountyContestBuilders(
    corlaInput:  Colorado2024Input,
    wantCounties: List<String>,
): Map<String, List<CountyContestBuilder>> {
    val roundContestMap = corlaInput.roundContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }

    // change ElectionResult to County name -> CountyContests
    val countyMap = mutableMapOf<String, CountyContests>()
    wantCounties.forEach { countyMap[it] = CountyContests(it) }
    corlaInput.electionDetailXml.contests.forEachIndexed { idx, corlaXmlContest ->
        corlaXmlContest.choices.forEach { choice ->
            choice.voteTypes.forEach { voteType ->
                voteType.byCounty.forEach{
                    val county = countyMap[it.name]
                    if (county != null) {
                        county.add(corlaXmlContest, choice.text, choice.id(), it.votes)
                    }
                }
            }
        }
    }

    // make the contests for each County
    return countyMap.mapValues { (countyName, countyContests) ->
        val contestBuilders = mutableListOf<CountyContestBuilder>()
        countyContests.contestVotes.forEach { (contestName, candVotes) ->
            val mname = mutatisMutandi(contestNameCleanup(contestName))

            var roundContest = roundContestMap[mname]
            val ccvs = countyContests.contestVotes[mname] // List<CountyCandVote>
            val corlaXmlContest = countyContests.contests[mname]
            if (roundContest == null || ccvs == null || corlaXmlContest == null) {
                println("*** Cant find contest with name '$mname' key=${contestName} - will ignore")
            } else {
                val candidateNames = ccvs.map { Pair(candidateNameCleanup(it.candName), it.candId) }.toMap()

                val info = ContestInfo(
                    mname,
                    corlaXmlContest.key,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY,
                    corlaXmlContest.voteFor
                )
                info.metadata["CORLAsample"] = roundContest.optimisticSamplesToAudit.toString()

                val contest = CountyContestBuilder(
                    info,
                    candVotes,
                    roundContest,
                )
                contestBuilders.add(contest)

            }
        }
        contestBuilders
    }
}

class CountyContests(val county: String) {
    val contestVotes = mutableMapOf<String, MutableList<CountyCandVote>>() // contest name -> List
    val contests = mutableMapOf<String, CorlaXmlContest>()  // contest name -> CorlaXmlContest

    fun add(corlaXmlContest: CorlaXmlContest, candName: String, candId: Int, vote: Int) {
        val mname = mutatisMutandi(contestNameCleanup(corlaXmlContest.text))
        contests.getOrPut(mname) { corlaXmlContest }
        val contestVote = contestVotes.getOrPut(mname) { mutableListOf() }
        contestVote.add(CountyCandVote(candName, candId, vote))
    }
}
data class CountyCandVote(val candName: String, val candId: Int, val vote: Int)

// for one county, one contest
class CountyContestBuilder(val info: ContestInfo, val candVotes: List<CountyCandVote>, contestRound: CorlaContestRoundCsv) {
    val contestId = info.id
    // val Nc: Int
    // val candidateVotes: Map<Int, Int>
    var totalCards: Int = 0

    /* init {
        // val candidates = corlaXmlContest.choices
        // candidateVotes = candidates.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()

        val totalVotes = candVotes.map { it.vote }.sum() / info.voteForN
        var useNc = contestRound.contestBallotCardCount
        if (useNc < totalVotes) {
            println("*** Contest '${info.name}' has $totalVotes total votes, but contestBallotCardCount is ${contestRound.contestBallotCardCount} - using totalVotes")
            useNc = totalVotes // contestRound.ballotCardCount
        }
        Nc = useNc
    } */

    fun setTotalCardsFromPools(cardPools: List<CardPoolIF>){
        totalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
    }
}

// TODO wed like to add a style name, but becuse we are using cvrs instead of mvrs, we have to place to put it without setting a pool Id.
//   which maybe we could but seems lame
fun createAndSaveUnsortedMvrs(contests: List<ContestIF>, cardPools: List<OneAuditPoolFromBallotStyle>, publisher: Publisher): Int {
    val unsortedMvrIterator = MvrsToCardsWithBatchNameIterator(
        Closer(CvrIteratorfromPools(cardPools)),
        cardPools,
        makePhantomCvrs(contests), // yes there are phantoms, heres where we need the contests' Nphantoms
    )
    validateOutputDirOfFile(publisher.unsortedMvrsFile())

    writeCardCsvFile(unsortedMvrIterator, publisher.unsortedMvrsFile())
    logger.info{"CreateColoradoElection unsortedMvrsFile to ${publisher.unsortedMvrsFile()}"}

    return unsortedMvrIterator.cardIndex // card count
}

// dont load into memory all at once, just one pool at a time
// this is random, cant do more than once. must do mvrs first
class CvrIteratorfromPools(cardPools: List<OneAuditPoolFromBallotStyle>) : Iterator<Cvr> {
    val cardPoolIter = cardPools.iterator()
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
class CardsFromPool(val cardPool: OneAuditPoolFromBallotStyle) : Iterator<Cvr> {
    val cvrs: Iterator<Cvr>

    init {
        val poolVunders = cardPool.possibleContests().map { Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()

        cvrs = makeCvrsForOnePool(
            poolVunders,
            cardPool.poolName,
            poolId = cardPool.poolId,
            cardPool.hasExactContests
        ).iterator()
    }

    override fun next() = cvrs.next()
    override fun hasNext() = cvrs.hasNext()
}

    ////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createCountyAudits(
    topdir: String,
    wantCounties: List<String>,
    creationConfig: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean,
) {
    val stopwatch = Stopwatch()

    createColoradoElection(
        externalSortDir = topdir,
        auditdir = "$topdir/audit", // or put it in audit ??
        pollingMode = null,
        creationConfig,
        roundConfig,
        startFirstRound = startFirstRound,
        name = "CorlaContest24",
    )

    val corlaCounty = CreateCountyAudits(topdir, wantCounties)

    corlaCounty.electionBuilders.forEach { election ->
        createElectionRecord(election, auditDir = election.auditdir, clear = false)
        val config = Config(election.electionInfo(), creationConfig, roundConfig)

        createAuditRecord(config, election, auditDir = election.auditdir, externalSortDir = "$topdir/${election.countyName}")

        if (startFirstRound) {
            val result = startFirstRound(election.auditdir)
            if (result.isErr) logger.error { result.toString() }
        }
    }

    logger.info { "createCountyAudits took $stopwatch" }
}

