package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateNpops
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.math.max

private val logger = KotlinLogging.logger("CreateBoulderElection25")

// Use OneAudit; redacted ballots are in pools. Cant do IRV because we dont have VoteConsolidators
// this version assume that the redacted groups know how many cards are contained in each
class CreateBoulderElection25(
    val auditType: AuditType,
    val export: BoulderCvrExportCsv,
    val sovo: BoulderStatementOfVotes,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
): ElectionBuilder {
    val exportCvrs: List<Cvr> = export.cvrs.map { it.convertToCvr() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }

    //val cvrTabs = countCvrVotes()
    // val redTabs = countRedactedVotes() // wrong
    //val cardPoolBuilders: List<OneAuditPoolFromBallotStyle> = convertRedactedToCardPool(export.redacted)
    //val boulderContestBuilders: Map<Int, BoulderContestBuilder25> = makeBoulderContestBuilders().associate { it.info.id to it}
    val cardPools: List<OneAuditPoolFromBallotStyle>
    val ncards: Int

    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val simulatedCvrs: List<Cvr>  // redacted cvrs
    val allCvrs: List<Cvr>  // redacted cvrs

    init {
        /*
        //// the redacted groups dont have undervotes, so we do some fancy dancing to generate reasonable undervote counts
        boulderContestBuilders.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}

        // estimate undervotes based on each precinct having a single ballot style
        val undervotesByContest = mutableMapOf<BoulderContestBuilder, Int>() // contestId ->
        boulderContestBuilders.values.forEach {
            undervotesByContest[it] = it.poolTotalCards() - it.expectedPoolNCards()
        } */

        val cvrTabs = countCvrVotes()
        val poolTabs = countRedactedVotes() // wrong
        cardPools = convertRedactedToCardPool(export.redacted)
        val contestBuilders: Map<Int, BoulderContestBuilder25> = makeBoulderContestBuilders(cvrTabs, cardPools, poolTabs)
            .associate { it.info.id to it}

        // we need to know the diluted Nb before we can create the UAs
        contests = makeContests(contestBuilders)
        simulatedCvrs = makeRedactedCvrs(cardPools)

        val phantoms = makePhantomCvrs(contests)
        allCvrs = exportCvrs + simulatedCvrs + phantoms

        val npops = tabulateNpops(allCvrs, infoList)
        this.ncards = allCvrs.size

        contestsUA = if (auditType.isClca()) ContestWithAssertions.make(contests, npops, isClca=true, )
            else makeOneAuditContests(contests, npops, cardPools)

        val totalRedactedBallots = cardPools.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPools.size} cardPools"}

        // TODO put in verify
        // checkNpops(allCvrs, createCards(), infoList)
    }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    }
                    candIdx++
                }
                candidateMap1

            } else { // there are ncand x ncand columns, so need something different here
                val candidates = mutableListOf<String>()
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    candidates.add(columns[col].choice)
                }
                val pairs = mutableListOf<Pair<String, Int>>()
                repeat(exportContest.nchoices) { idx ->
                    pairs.add(Pair(candidates[idx], idx))
                }
                pairs.toMap()
            }

            val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    private fun convertRedactedToCardPool(redacteds: List<RedactedGroup>): List<OneAuditPoolFromBallotStyle> {
        return redacteds.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, _) -> key != 12 }
                } else redacted.contestVotes

            //// the redacted groups dont have undervotes, so we have to generate reasonable undervote counts
            // for this pass we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = useContestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }

            val name = cleanCsvString(redacted.ballotType)
            val id = redactedIdx
            OneAuditPoolFromBallotStyle(name, id, hasExactContests=true, contestTabs, infos, ncards = redacted.ncards)
        }
    }

    // make simulated CVRs for all the pools
    fun makeRedactedCvrs(cardPools: List<OneAuditPoolFromBallotStyle>) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeCvrsForOnePool(cardPool))
        }
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests
    private fun makeCvrsForOnePool(cardPool: OneAuditPoolFromBallotStyle) : List<Cvr> { // contestId -> candidateId -> nvotes
        val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val cvrs =
            makeCvrsForOnePool(poolVunders, cardPool.poolName, poolId = cardPool.poolId, cardPool.hasExactContests)

        // check it
        val cvrTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        poolVunders.forEach { (contestId, vunder) ->
            val poolTab = cardPool.voteTotals[contestId]!!
            val cvrTab = cvrTabs[contestId]!!
            if (!checkEquivilentVotes(vunder.cands(), cvrTab.votes)) {
                logger.warn{"cvrs differ from cardPool"}
                println("  info=${infos[contestId]}")
                println("  cardPool.ncards=${cardPool.ncards()} cvrs.size=${cvrs.size}")
                println("  cardPoolTab=$poolTab")
                println("  cvrTab=$cvrTab")
                println("  vunder= ${vunder}")
                // TODO track down why this happens; maybe just inexact simulation? causes verification to fail?
                println("  checkEquivilentVotes=${checkEquivilentVotes(vunder.cands(), cvrTab.votes)}")
                println()
                throw RuntimeException("makeCvrsForOnePool fails")
            }
        }

        return cvrs
    }

    private fun checkVunderEquivilentTab(vunder: Vunder, contestTab: ContestTabulation): Boolean {
        // if hasExactContests, then missing has to be zero
        // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        // val undervotes = npop * voteForN - voteSum
        val npop = (vunder.undervotes + vunder.nvotes) / vunder.voteForN

        var allOk = true
        allOk = allOk && checkEquivilentVotes(vunder.cands(), contestTab.votes)
        allOk = allOk && (vunder.nvotes == contestTab.nvotes())
        allOk = allOk && (vunder.undervotes == contestTab.undervotes) // no
        allOk = allOk && (npop == contestTab.ncards())
        return allOk
    }

    fun makeBoulderContestBuilders(cvrTabs: Map<Int, ContestTabulation>,
                                   cardPools: List<OneAuditPoolFromBallotStyle>,
                                   poolTabs: Map<Int, ContestTabulation>,
                                   ): List<BoulderContestBuilder25> {
        val oa2Contests = mutableListOf<BoulderContestBuilder25>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null && cvrTabs[info.id] != null) {
                val cb = BoulderContestBuilder25(info, sovoContest, cvrTabs[info.id]!!, poolTabs[info.id], cardPools)
                oa2Contests.add(cb)
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oa2Contests
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(infos[contestVote.contestId]!!) }
                tab.addVotes(contestVote.candVotes.toIntArray(), phantom=false)
            }
        }
        return votes
    }

    // sum over all pools of the ContestTabulations
    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.redacted.forEach { redacted: RedactedGroup ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestVote.forEach { (cand, vote) -> tab.addVote(cand, vote) }
                tab.ncardsTabulated += redacted.ncards
            }
        }
        return votes
    }

    fun makeContests(contestBuilders: Map<Int, BoulderContestBuilder25>): List<ContestIF> {
        return infoList.filter { !it.isIrv }.map { info ->
            val contestBuilder = contestBuilders[info.id]!!
            val candVotes = contestBuilder.candVoteTotals.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = contestBuilder.ncards()
            val useNc = max( ncards, contestBuilder.Nc())
            info.metadata["PoolPct"] = (100.0 * contestBuilder.poolTotalCards / useNc).toInt().toString()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun electionInfo() =
        ElectionInfo("Boulder25$auditType", auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA
    override fun cardStyles(): List<StyleIF> {
        val lastId = cardPools.map{ it.id() }.max()
        return cardPools +
            export.ballotTypes.mapIndexed { idx, it ->
                CardStyle(
                    it.name,
                    lastId + idx + 1,
                    it.contests.toList().toIntArray(),
                    true
                )
            }
    }
    override fun cardPools() = cardPools
    override fun createUnsortedMvrsInternal() = mvrsToAuditableCardsList(allCvrs, cardPools())
    override fun createUnsortedMvrsExternal() = null

    override fun cards() = createCards()
    override fun ncards() = ncards

    fun createCards(): CloseableIterator<CardWithBatchName> {
        // same cvrs for CLCA and OneAudit
        return CvrsToCardsWithBatchNameIterator(
            auditType,
            Closer(allCvrs.iterator()), // use the mvrs as the cvrs
            null,
            batches = if (auditType.isClca()) null else cardPools
        )
    }
}

////////////////////////////////

// assume redacted groups knows how many cards there are in each group
class BoulderContestBuilder25(val info: ContestInfo,
                              val sovoContest: BoulderContestVotes,
                              val cvrTab: ContestTabulation,
                              poolTabulation: ContestTabulation?,
                              val cardPools: List<OneAuditPoolFromBallotStyle>) {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards
    val contestId: Int = info.id

    val poolTotalCards: Int
    val candVoteTotals: Map<Int, Int>

    init {
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }

        candVoteTotals = if (poolTabulation == null) cvrTab.votes else {
            val sum = mutableMapOf<Int, Int>()
            sum.mergeReduce(listOf(cvrTab.votes, poolTabulation.votes))
            sum
        }
    }

    fun Nc() = sovoContest.totalBallots

    override fun toString() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
    }

    fun ncards() = poolTotalCards + cvrTab.ncardsTabulated

}
