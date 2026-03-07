package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.boulder.distributeExpectedOvervotes
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestBuilderIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.oneaudit.makeVunderCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoOneAudit")

private val debugUndervotes = false

// making OneAudit pools from the precinct results, then generate CVRs from pools
// TODO experiment with OneAudit with small counties that do hand counts
open class CreateColoradoElection (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val auditType: AuditType,
): CreateElectionIF {
    val roundContests: List<CorlaContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val corlaContestBuilders = makeOneAuditBuilders(electionDetailXml, roundContests)
    val infoMap = corlaContestBuilders.associate { it.info.id to it.info }

    val cardPools: List<OneAuditPoolFromBallotStyle> = convertPrecinctsToCardPools(precinctFile, infoMap)
    val ncards: Int

    val populations: List<PopulationIF>
    val contests: List<ContestIF>
    val contestsUA: List<ContestWithAssertions>

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

        populations = cardPools
        contests = makeContests()

        val infos = contests.map { it.info() }.associateBy { it.id }
        val (manifestTabs, count) = tabulateCardsAndCount(createCards(), infos)
        val npopMap = manifestTabs.mapValues { it.value.ncardsTabulated }
        this.ncards = count

        // in case we decide to support OA
        contestsUA = if (auditType.isOA()) makeOneAuditContests(contests, npopMap, cardPools)
                     else ContestWithAssertions.make(contests, npopMap, isClca=auditType.isClca(), )
    }

    private fun makeOneAuditBuilders(electionDetailXml: ElectionDetailXml, roundContests: List<CorlaContestRoundCsv>): List<CorlaContestBuilder> {
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

    private fun convertPrecinctsToCardPools(precinctFile: String, infoMap: Map<Int, ContestInfo>): List<OneAuditPoolFromBallotStyle> {
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
            // TODO hasSingleCardStyle=false ?
            OneAuditPoolFromBallotStyle("${precinct.county}-${precinct.precinct}", idx,
                hasSingleCardStyle=false, contestTabs, infoMap)
        }
    }

    fun makeContests(): List<ContestIF> {
        val infoList= corlaContestBuilders.map { it.info }.sortedBy { it.id }
        val contestMap= corlaContestBuilders.associateBy { it.info.id }
        println("ncontests with info = ${infoList.size}")

        return infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max( ncards, oaContest.Nc)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun electionInfo() = ElectionInfo(auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null)
    override fun populations() = if (auditType.isClca()) emptyList() else populations
    override fun makeCardPools() = if (auditType.isClca()) emptyList() else cardPools.map { it.toOneAuditPool() }
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = ncards
    override fun createUnsortedMvrs() = emptyList<Cvr>() // TODO only needed for private mvrs for OneAudit

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsToCardsAddStyles(auditType,
            Closer(CvrIteratorfromPools()),
            makePhantomCvrs(contests), // yes there are phantoms
            if (auditType.isClca()) null else cardPools,
        )
    }

    // dont load into memory all at once, just one pool at a time
    inner class CvrIteratorfromPools(): Iterator<Cvr> {
        val oaContestMap = corlaContestBuilders.associateBy { it.info.id }
        val cardPoolIter = cardPools.iterator()
        var innerIter: CardsFromPool

        init {
            innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap)
        }

        override fun next(): Cvr {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (cardPoolIter.hasNext()) {
                innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap)
                return hasNext()
            }
            return false
        }
    }

    // these are chosen randomly, so in order for mvrs and cvrs to match, the cvrs have to be made from the mvrs.
    inner class CardsFromPool(val cardPool: OneAuditPoolFromBallotStyle, val oaContestMap: Map<Int, CorlaContestBuilder>) : Iterator<Cvr> {
        val cvrs: Iterator<Cvr>

        init {
            val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
            /* val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
            cardPool.voteTotals.forEach { (contestId, contestTab) ->
                val oaContest: OneAuditBuilderCorla = oaContestMap[contestId]!!
                val sumVotes = contestTab.nvotes()
                val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
                contestVotes[contestId] = Vunder.fromNpop(contestId,  underVotes, cardPool.ncards(), contestTab.votes, oaContest.info.voteForN)
            }
            poolVunders.forEach{ (id, vunder) ->
                val otherVunder =  contestVotes[id]!!
                if (vunder != otherVunder)
                    print("why?")
            } */

            cvrs = makeVunderCvrs(
                poolVunders,
                cardPool.poolName,
                poolId = if (auditType.isClca()) null else cardPool.poolId
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

    override fun adjustPoolInfo(cardPools: List<OneAuditPoolIF>){
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
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    auditType : AuditType,
    clear: Boolean = true,
    ): Result<AuditRoundIF, ErrorMessages>
{
    val stopwatch = Stopwatch()
    val auditdir = "$topdir/audit"

    val election = if (auditType.isClca()) CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, auditType)
                    else CreateColoradoPolling(electionDetailXmlFile, contestRoundFile, precinctFile)
    createElectionRecord("corla", election, auditDir = auditdir, clear = clear)

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        auditType.isClca() -> AuditConfig(AuditType.CLCA, contestSampleCutoff = 20000, riskLimit = .03, nsimEst=10)
        auditType.isPolling() -> AuditConfig(
            AuditType.POLLING, riskLimit = .03, nsimEst = 100, quantile = 0.5,
        )
        else -> throw RuntimeException("Unsupported audit type ${auditType.name}")
    }

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"createCorla took $stopwatch" }

    return result
}


