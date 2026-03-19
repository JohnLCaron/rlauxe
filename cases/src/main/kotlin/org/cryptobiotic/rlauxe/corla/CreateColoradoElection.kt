package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.boulder.distributeExpectedOvervotes
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestBuilderIF
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.estimate.makeCvrsForPool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoOneAudit")

private val debugUndervotes = false

// make pools from the precinct results, then generate CVRs from pools
open class CreateColoradoElection (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val auditType: AuditType,
    val auditdir: String,
    val hasSingleCardStyle: Boolean,
    val pollingMode: PollingMode?,
): CreateElectionIF {
    val roundContests: List<CorlaContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val corlaContestBuilders = makeOneAuditBuilders(electionDetailXml, roundContests)
    val infoMap = corlaContestBuilders.associate { it.info.id to it.info }

    val cardPools: List<OneAuditPoolFromBallotStyle> = convertPrecinctsToCardPools(precinctFile, infoMap)
    val ncards: Int

    val batches: List<BatchIF>
    val contests: List<ContestIF>
    val contestsUA: List<ContestWithAssertions>
    val publisher = Publisher(auditdir)

    init {
        // set contest total cards as sum over pools
        corlaContestBuilders.forEach { it.adjustPoolInfo(cardPools) }

        // estimate undervotes based on each precinct having a single ballot style
        val undervotesByContest = mutableMapOf<CorlaContestBuilder, Int>() // contestId ->
        corlaContestBuilders.forEach {
            undervotesByContest[it] = it.expectedPoolNCards() - it.poolTotalCards()
        }

        // adjust so contest 0 has 0 undervotes. needed since we dont know number of cards in precincts or missing
        distributeExpectedOvervotes(corlaContestBuilders[0], cardPools)
        corlaContestBuilders.forEach { it.adjustPoolInfo(cardPools) }

        if (debugUndervotes) {
            undervotesByContest.forEach { (cb, before) ->
                val needAfter = cb.expectedPoolNCards() - cb.poolTotalCards()
                println("  ${cb.contestId} $before $needAfter ")
            }
        }

        batches = cardPools
        contests = makeContests()

        // have to save the mvrs while we know them
        ncards = createAndSaveMvrs()

        val npopMap: Map<Int, Int> = if ((auditType.isPolling() && pollingMode!!.withoutBatches())) {
            contests.associate { it.id to ncards }
        } else {
            val infos = contests.map { it.info() }.associateBy { it.id }
            val mvrs = readCardsCsvIterator(publisher.unsortedMvrsFile())
            val (manifestTabs, count) = tabulateCardsAndCount(mvrs, infos)
            require(ncards == count)
            manifestTabs.mapValues { it.value.ncardsTabulated }
        }

        contestsUA = if (auditType.isOA()) makeOneAuditContests(contests, npopMap, cardPools) // in case we decide to support OA
                     else ContestWithAssertions.make(contests, npopMap, isClca = auditType.isClca(),)
    }

    private fun makeOneAuditBuilders(
        electionDetailXml: ElectionDetailXml,
        roundContests: List<CorlaContestRoundCsv>
    ): List<CorlaContestBuilder> {
        val roundContestMap = roundContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }

        val contests = mutableListOf<CorlaContestBuilder>()
        electionDetailXml.contests.forEachIndexed { detailIdx, detailContest ->
            val contestName = contestNameCleanup(detailContest.text)
            var roundContest = roundContestMap[contestName]
            if (roundContest == null) {
                roundContest = roundContestMap[mutatisMutandi(contestName)]
                if (roundContest == null) {
                    val mname = mutatisMutandi(contestName)
                    println("*** Cant find ContestRoundCsv $mname")
                }
            } else {
                val candidates = detailContest.choices
                val candidateNames =
                    candidates.mapIndexed { idx, choice -> Pair(candidateNameCleanup(choice.text), idx) }.toMap()

                val info = ContestInfo(
                    contestName,
                    detailIdx,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY,
                    detailContest.voteFor
                )
                info.metadata["CORLAsample"] = roundContest.optimisticSamplesToAudit

                // they dont have precinct data for contest >= 260, so we'll just skip them
                if (info.id < 260) {
                    val contest = CorlaContestBuilder(
                        info,
                        detailContest,
                        roundContest,
                    )
                    contests.add(contest)
                }
            }
        }

        return contests
    }

    // we create the simulated mvrs from the pools; used for both CLCA and Polling; OA is not feasible
    private fun convertPrecinctsToCardPools(
        precinctFile: String,
        infoMap: Map<Int, ContestInfo>
    ): List<OneAuditPoolFromBallotStyle> {
        val reader = ZipReader(precinctFile)
        val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
        val precincts = readColoradoPrecinctLevelResults(input)
        println("precincts = ${precincts.size}")

        return precincts.mapIndexed { idx, precinct ->
            val contestTabs = mutableMapOf<Int, ContestTabulation>()
            precinct.contestChoices.forEach { (name, choices) ->
                val contestName = mutatisMutandi(contestNameCleanup(name))
                val info = infoMap.values.find { it.name == contestName }
                if (info != null) {
                    val contestTab = ContestTabulation(info)
                    contestTabs[info.id] = contestTab
                    choices.forEach { choice ->
                        val choiceName = candidateNameCleanup(choice.choice)
                        val candId = info.candidateNames[choiceName]
                        if (candId == null) {
                            // logger.warn{"*** precinct ${precinct} candidate ${choiceName} writein missing in info ${info.id} $contestName infoNames= ${info.candidateNames}"}
                        } else {
                            contestTab.addVote(candId, choice.totalVotes) // cant use addVotes
                        }
                    }
                }
            }
            OneAuditPoolFromBallotStyle(
                "${precinct.county}-${precinct.precinct}", idx+1,
                hasSingleCardStyle = hasSingleCardStyle, contestTabs, infoMap
            )
        }
    }

    fun makeContests(): List<ContestIF> {
        val infoList = corlaContestBuilders.map { it.info }.sortedBy { it.id }
        val contestMap = corlaContestBuilders.associateBy { it.info.id }
        println("ncontests with info = ${infoList.size}")

        return infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max(ncards, oaContest.Nc)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun electionInfo() =
        ElectionInfo("Corla24$auditType$pollingMode", auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null)

    override fun batches() = if (auditType.isPolling() && pollingMode!!.withBatches()) batches else null
    override fun cardPools() = if (auditType.isPolling() && pollingMode!!.withPools()) cardPools.map { it.toOneAuditPool() } else null
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    override fun cards(): CloseableIterator<AuditableCard> {
        val unsortedMvrs = readCardsCsvIterator(publisher.unsortedMvrsFile())
        return TransformingIterator(unsortedMvrs) { mvr ->
            when {
                mvr.isPhantom() -> mvr
                auditType.isClca() -> mvr.copy(poolId = null)
                (auditType.isPolling() && pollingMode!!.withoutBatches()) -> mvr.copy(votes = null, batchName="OneBatch", poolId=0)
                (auditType.isPolling()) -> mvr.copy(votes = null)
                else -> throw IllegalStateException("Unknown what to do with mvr: $mvr")
            }
        }
    }

    override fun createUnsortedMvrsInternal() = null
    override fun createUnsortedMvrsExternal() = readCardsCsvIterator(publisher.unsortedMvrsFile())

    // CvrsToCardsAddStyles is random, so in order to match the mvrs and cvrs, we must generate the mvrs first,
    // the create manifest from them. This is nonstandard, so we will do it here.
    // Could put this into createAuditRecord(reverse = true) ??
    // return number of cards
    fun createAndSaveMvrs(): Int {
        val unsortedMvrIterator = MvrsToCardsAddStyles(
            Closer(CvrIteratorfromPools()),
            makePhantomCvrs(contests), // yes there are phantoms, heres where we need the contests
            cardPools,
        )

        clearDirectory(Path(auditdir))
        validateOutputDirOfFile(publisher.unsortedMvrsFile())

        writeAuditableCardCsvFile(unsortedMvrIterator, publisher.unsortedMvrsFile())
        logger.info{"CreateColoradoElection unsortedMvrsFile to ${publisher.unsortedMvrsFile()}"}

        return unsortedMvrIterator.cardIndex
    }

    /*
    fun wtf(externalSortDir: String, seed: Long) {
        // oh damn jumping the gun on the seed aka sorting. but cant get mvrs from the auditable cards .....
        writeSortedCardsExternal(externalSortDir, publisher, unsortedMvrIterator, seed = seed, zip = false)
        logger.info { "createAuditRecord wrotePrivateMvrs to ${publisher.privateMvrsFile()}" }

        // mvrs are sorted, dont need to sort cards.
        val sortedMvrs = readCardsCsvIterator(publisher.privateMvrsFile())
        // class TransformingIterator<R, T> (val org: CloseableIterator<R>, val transform: (R) -> T) : CloseableIterator<T> {
        val sortedCvrs = TransformingIterator(sortedMvrs) { mvr ->
            if (mvr.isPhantom()) mvr else mvr.copy(votes = null) // just remove the votes for non-phantoms
        }
        writeAuditableCardCsvFile(sortedCvrs, publisher.cardManifestFile())
    } */

    // dont load into memory all at once, just one pool at a time
    // this is random, cant do more than once. must do mvrs first
    inner class CvrIteratorfromPools() : Iterator<Cvr> {
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

            cvrs = makeCvrsForPool(
                poolVunders,
                cardPool.poolName,
                poolId = cardPool.poolId,
                cardPool.hasSingleCardStyle
            ).iterator()
        }

        override fun next() = cvrs.next()
        override fun hasNext() = cvrs.hasNext()
    }

}

class CorlaContestBuilder(val info: ContestInfo, detailContest: ElectionDetailContest, contestRound: CorlaContestRoundCsv): OneAuditContestBuilderIF {
    override val contestId = info.id
    val Nc: Int
    val candidateVotes: Map<Int, Int>
    var poolTotalCards: Int = 0

    init {
        val candidates = detailContest.choices
        candidateVotes = candidates.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()

        val totalVotes = candidateVotes.map { it.value }.sum() / info.voteForN
        var useNc = contestRound.contestBallotCardCount
        if (useNc < totalVotes) {
            println("*** Contest ${info.name} has $totalVotes total votes, but contestBallotCardCount is ${contestRound.contestBallotCardCount} - using totalVotes")
            useNc = totalVotes // contestRound.ballotCardCount
        }
        Nc = useNc
    }

    // total cards in all pools for this contest
    override fun poolTotalCards(): Int  = poolTotalCards

    override fun adjustPoolInfo(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
    }

    // this maximizes undervotes, assumes missing = 0
    fun oapoolUndervote(cardPools: List<OneAuditPoolFromBallotStyle>): Int {
        return cardPools.sumOf { it.undervoteForContest(contestId) }
    }

    // expected total poolcards for this contest, making assumptions about missing undervotes
    override fun expectedPoolNCards() = Nc
}


////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoElection(
        topdir: String,
        auditdir: String,
        electionDetailXmlFile: String,
        contestRoundFile: String,
        precinctFile: String,
        pollingMode: PollingMode? = null,
        creation: AuditCreationConfig,
        round: AuditRoundConfig,
        startFirstRound: Boolean = true
    ) {
    val stopwatch = Stopwatch()

    val election = if (creation.auditType.isClca())
        CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, creation.auditType, auditdir,
                    hasSingleCardStyle=false, pollingMode=null) else
        CreateColoradoPolling(electionDetailXmlFile, contestRoundFile, precinctFile, auditdir, hasSingleCardStyle=false, pollingMode!!)

    createElectionRecord(election, auditDir = auditdir, clear = false)
    val config = Config(election.electionInfo(), creation, round)
    val auditConfig = config.toAuditConfig()

    /* val config = when {
        (auditConfigIn != null) -> auditConfigIn
        auditType.isClca() -> AuditConfig(
            AuditType.CLCA,
            contestSampleCutoff = 10000,
            auditSampleCutoff = 20000,
            riskLimit = .03,
            nsimEst=10,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
        )
        auditType.isPolling() -> AuditConfig(
            AuditType.POLLING, riskLimit = .03, nsimEst = 20, quantile = 0.5,
            contestSampleCutoff = 20000,
            auditSampleCutoff = 100000,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
            pollingConfig = PollingConfig(mode = pollingMode!!)
        )
        else -> throw RuntimeException("Unsupported audit type ${auditType.name}")
    } */

    createAuditRecord(auditConfig, election, auditDir = auditdir, externalSortDir = topdir)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorla took $stopwatch" }
    }
}


