package org.cryptobiotic.rlauxe.boulder

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.auditcenter.CountyElectionSimCvrs
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.cvr.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.cvr.parseIrvContestName
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromFile
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromResource
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.irv.IrvContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("CreateBoulderElection")

// TODO cant we merge this into CreateBoulderElection? why is it special ??
// Use OneAudit; redacted ballots are in pools. Cant do IRV because we dont have VoteConsolidators
// this version assume that the redacted groups know how many cards are contained in each
class CreateBoulderElection(
    val electionName: String,
    val auditType: AuditType,
    val corlaCvrs: CorlaCvrsIF,
    val sovo: BoulderStatementOfVotes,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean,
): ElectionBuilder {
    val exportCvrs: List<Cvr> = corlaCvrs.cvrs().map { it.convertToCvr() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }

    //val cvrTabs = countCvrVotes()
    // val redTabs = countRedactedVotes() // wrong
    //val cardPoolBuilders: List<AdjustableCardPool> = convertRedactedToCardPool(export.redacted)
    //val boulderContestBuilders: Map<Int, BoulderContestBuilder25> = makeBoulderContestBuilders().associate { it.info.id to it}
    val ncards: Int

    val contestBuilders: Map<Int, BoulderContestBuilderIF> // make visible for debugging
    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val redactedCvrs: List<Cvr>  // redacted cvrs
    val allCvrs: List<Cvr>  // unredacted cvrs
    val redactedPools: List<CardPool>
    val mvrs: List<AuditableCard>

    init {
        val cvrTabs = countCvrVotes()
        val redactedTabs = countRedactedVotes()  // wrong
        val cardPoolBuilders = convertRedactedToCardPool(corlaCvrs.redactedGroups())

        contestBuilders = if (auditType.isClca()) makeBoulderContestClcaBuilders(cvrTabs, redactedTabs, cardPoolBuilders).associate { it.contestId to it}
            else makeBoulderContestOaBuilders(cvrTabs, redactedTabs, cardPoolBuilders).associate { it.contestId to it}

        redactedPools = cardPoolBuilders.map { it.build() }

        // we need to know the diluted Nb before we can create the UAs
        // make fake IRV contest for the purpose of setting the phantoms.
        contests = makeContests(contestBuilders)
        redactedCvrs = if (auditType.isClca()) emptyList() else makeRedactedCvrs(redactedPools)

        // need to know the phantoms to calculate allCvrs and Npops
        val phantoms = makePhantomCvrs(contests)
        logger.info {"made ${phantoms.size} phantom ballots"}

        allCvrs = exportCvrs + redactedCvrs + phantoms // in memory
        this.ncards = allCvrs.size
        val npops = tabulateNpops(allCvrs, infoList)

        // TODO cvrTabs dont have the irv part, so will fail in raire library
        contestsUA = makeContestOAs(contests, npops, cvrTabs, redactedPools, )

        //contestsUA = if (auditType.isClca()) ContestWithAssertions.make(contests, npops, isClca=true, hasStyle = hasStyle)
        //    else makeOneAuditContests(contests, npops, redactedPools, hasStyle = hasStyle)

        val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}

        // TODO put in verify
        // checkNpops(allCvrs, createCards(), infoList)

        mvrs = mvrsToAuditableCardsList(allCvrs, cardPools())
    }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = corlaCvrs.schema.columns

        val result = mutableListOf<ContestInfo>()
        sovo.contests.forEach { sovoContest ->
            // NOTE: "starts with"
            val exportContest = corlaCvrs.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }
            if (exportContest != null) {
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
                val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else
                    parseContestNameAndVoteFor(exportContest.contestName)
                result.add(ContestInfo(name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners))
            } else {
                logger.warn{"Cant find contest ${sovoContest.contestTitle}"}
            }
        }
        return result
    }

    private fun convertRedactedToCardPool(redacteds: List<RedactedGroup>): List<CardPoolBuilder> {
        var id = 1
        return redacteds.map { redacted: RedactedGroup ->
            //// the redacted groups dont have undervotes, so we should generate reasonable undervote counts
            // but... now we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }

            val name = "redacted " + cleanCsvString(redacted.ballotType)
            // in this case, nlines == ncards
            val hasExactContests = !redacted.ballotType.contains("&") // has multiple card styles
            CardPoolBuilder.fromMinVotesNeeded(name, id++, hasExactContests=hasExactContests, infos, contestTabs).setNcards(redacted.ncards())
        }
    }

    // make simulated CVRs for all the pools
    fun makeRedactedCvrs(cardPools: List<CardPool>) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeCvrsForOnePool(cardPool))
        }
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests
    private fun makeCvrsForOnePool(cardPool: CardPool) : List<Cvr> { // contestId -> candidateId -> nvotes
        val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val cvrs = makeCvrsForOnePool(poolVunders, cardPool.poolName, poolId = cardPool.poolId, cardPool.hasExactContests)

        // check it
        val cvrTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        poolVunders.forEach { (contestId, vunder) ->
            val poolTab = cardPool.contestTabs[contestId]!!
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

    fun makeBoulderContestOaBuilders(cvrTabs: Map<Int, ContestTabulation>,
                                     redactedTabs: Map<Int, ContestTabulation>,
                                     cardPools: List<CardPoolBuilder>,
   ): List<BoulderContestOaBuilder> {
        val oaContests = mutableListOf<BoulderContestOaBuilder>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null && (cvrTabs[info.id] != null || redactedTabs[info.id] != null)) {
                val cb = BoulderContestOaBuilder(info, sovoContest, cvrTabs[info.id], redactedTabs[info.id], cardPools)
                oaContests.add(cb)
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oaContests
    }

    fun makeBoulderContestClcaBuilders(cvrTabs: Map<Int, ContestTabulation>,
                                     redactedTabs: Map<Int, ContestTabulation>,
                                     cardPools: List<CardPoolBuilder>,
    ): List<BoulderContestClcaBuilder> {
        val clcaContests = mutableListOf<BoulderContestClcaBuilder>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null && (cvrTabs[info.id] != null)) {
                val cb = BoulderContestClcaBuilder(info, sovoContest,cvrTabs[info.id]!!, cardPools)
                clcaContests.add(cb)
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return clcaContests
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        corlaCvrs.cvrs().forEach { cvr ->
            cvr.contestVotes.forEach { contestVote ->
                val info = infos[contestVote.contestId]
                if (info == null)
                    println("cant find ${contestVote.contestId}")
                else {
                    val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(info) }
                    tab.addVotes(contestVote.candVotes.toIntArray(), phantom = false)
                }
            }
        }
        return votes
    }

    // sum over all pools of the ContestTabulations
    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        corlaCvrs.redactedGroups().forEach { redacted ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestVote.forEach { (cand, vote) -> tab.addVote(cand, vote) }
                // in this case, nlines == ncards
                tab.ncardsTabulated += redacted.ncards()
            }
        }
        return votes
    }

    fun makeContests(contestBuilders: Map<Int, BoulderContestBuilderIF>): List<ContestIF> {
        return infoList.filter { /*!it.isIrv && */ (contestBuilders[it.id] != null) }.map { info ->
            val contestBuilder = contestBuilders[info.id]!!
            contestBuilder.build(info)
        }
    }

    fun makeContestOAs(
        contests: List<ContestIF>,
        npopMap: Map<Int, Int>,
        allCvrTabs: Map<Int, ContestTabulation>,
        oneAuditPools: List<CardPool>,
    ): List<ContestWithAssertions> {
        val contestsUAs = mutableListOf<ContestWithAssertions>()

        val regular = ContestWithAssertions.make(contests.filter { !it.isIrv() }, npopMap, true, hasStyle)
        if (auditType.isOA()) setPoolAssorterAverages(regular, oneAuditPools)
        contestsUAs.addAll(regular)

        contests.filter { it.isIrv() }.forEach {
            // assumes contestTab.irvVotes are present
            val irvContest = if (!auditType.isOA()) makeRaireContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!)
            else makeRaireOneAuditContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!, oneAuditPools)
            contestsUAs.add(irvContest)
        }

        return contestsUAs
    }

    override fun electionInfo() =
        ElectionInfo(electionName, auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA

    override fun cardStyles() = null /*: List<StyleIF> {
        val lastId = redactedPools.map{ it.id() }.max()
        return redactedPools + corlaCvrs.cardStyles().mapIndexed { idx, it ->
                CardStyle(
                    it.name,
                    lastId + idx + 1,
                    it.contestIds.toList().toIntArray(),
                    true
                )
            }
    } */

    override fun cardPools() = redactedPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCards(mvrs)
    override fun ncards() = ncards

    fun mvrsToAuditableCardsList(
        mvrs: List<Cvr>,
        styles: List<StyleIF>?,
    ): List<AuditableCard> {
        val styleMap = styles?.associateBy{ it.id() } ?: emptyMap()
        var cardIndex = 0 // 0 based index

        return mvrs.map { org ->
            val style = styleMap[org.poolId]  // hijack poolId

            val styleId = when {
                (style != null) -> style.id()
                org.phantom() -> CardStyle.phantomStyle.id()
                else -> CardStyle.fromCvrStyle.id()
            }

            val (contestIds, contestStarts, candidates) = makeFromVotes(org.votes)
            AuditableCard(
                org.id,
                null,
                cardIndex++,
                0,
                phantom = org.phantom,
                styleId = styleId,
                contestIds, contestStarts, candidates,
                poolId = org.poolId,
            )
        }
    }

    fun createCards(mvrs: List<AuditableCard>): CloseableIterator<AuditableCard> {
        // remove cvrs for cards in the pools
        val mvrIter = Closer(mvrs.iterator())
        val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
            if (org.poolId != null) AuditableCard.removeVotes(org) else org
        }
        return transformer
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

interface BoulderContestBuilderIF {
    val contestId: Int
    val contestName: String
    // fun info(): ContestInfo
    fun build(info: ContestInfo): ContestIF
}

// cards in redacted group become phantoms
class BoulderContestClcaBuilder(val info: ContestInfo,
                                val sovoContest: SovoContestVotes,
                                cvrTab: ContestTabulation,
                                redactedPools: List<CardPoolBuilder>,
    ): BoulderContestBuilderIF {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards
    override val contestId = info.id
    override val contestName = info.name

    val ncvrs: Int
    val poolTotalCards: Int
    val candVoteTotals: Map<Int, Int>

    init {
        poolTotalCards = redactedPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        candVoteTotals = cvrTab.votes
        ncvrs = cvrTab.ncardsTabulated
    }

    override fun build(info: ContestInfo): Contest {
        val candVotes = candVoteTotals.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
        if (ncvrs > sovoContest.totalBallots) {
            logger.warn{"contest ${info.id} ncvrs $ncvrs > ${sovoContest.totalBallots} sovoContest.totalBallots"}
        }
        val useNc = max( ncvrs, sovoContest.totalBallots)  // WRONG
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()

        return Contest(info, candVotes, Nc = useNc, Ncast = ncvrs)
    }

    override fun toString() = buildString {
        append("${nfn(info.id,3)}, ${trunc(info.name, nameWidth)}, ")
        append(" ${nfn(sovoContest.totalBallots, 8)}, ${nfn(sovoCards, 7)}, ${nfn(sovoContest.totalBallots - sovoCards, 7)}, ${nfn(ncvrs, 7)}, ${nfn(sovoCards-ncvrs, 7)}")
    }

    companion object {
        val nameWidth = 50
        val header = " id, ${trunc("name", nameWidth)}, sovoTotal, sovoCards,     diff, ncvrs,     diff"
    }
}



class BoulderContestOaBuilder(val info: ContestInfo,
                              val sovoContest: SovoContestVotes,
                              cvrTab: ContestTabulation?,
                              redactedTab: ContestTabulation?,
                              cardPools: List<CardPoolBuilder>): BoulderContestBuilderIF {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCardsOld = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val sovoCast = sovoContest.calcNc(info.voteForN)
    // val phantoms = sovoContest.totalBallots - sovoCards
    override val contestId = info.id
    override val contestName = info.name

    val ncvrs: Int
    val poolTotalCards: Int
    val candVoteTotals: Map<Int, Int>

    init {
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        candVoteTotals = when {
            (cvrTab == null) -> redactedTab!!.votes
            (redactedTab) == null -> cvrTab.votes
            else -> {
                val sum = mutableMapOf<Int, Int>()
                sum.mergeReduce(listOf(cvrTab.votes, redactedTab.votes))
                sum
            }
        }
        ncvrs = poolTotalCards + (cvrTab?.ncardsTabulated ?: 0)
        val diff = sovoContest.calcNc(info.voteForN)-ncvrs
        if (ncvrs > sovoContest.calcNc(info.voteForN)) {
            logger.warn{"contest ${info.id} ncvrs $ncvrs > ${sovoContest.calcNc(info.voteForN)} sovoContest.calcNc; adjust Nc= ${-diff} "}
        } //else if (ncvrs != sovoContest.calcNc(info.voteForN)) {
          //  logger.info{"contest ${info.id} ncvrs $ncvrs < ${sovoContest.calcNc(info.voteForN)} sovoContest.calcNc; phantoms = $diff"}
        //}
    }

    override fun build(info: ContestInfo): ContestIF {
        val candVotes = candVoteTotals.filter { info.candidateIds.contains(it.key) } // remove Write-Ins

        val useNc = max( ncvrs, sovoContest.totalBallots - sovoContest.totalOverVotes)
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
        //     val info: ContestInfo,
        //    val winners: List<Int>, // actually only one winner is allowed
        //    val Nc: Int,
        //    val Ncast: Int,
        //    val undervotes: Int,
        return if (info.isIrv) IrvContest(info, listOf(0), useNc, ncvrs, 0) // TODO this is fake...
            else Contest(info, candVotes, useNc, ncvrs)
    }

    override fun toString() = buildString {
        append("${nfn(info.id,3)}, ${trunc(info.name, nameWidth)}, ")
        append(" ${nfn(sovoContest.calcNc(info.voteForN), 8)}, ${nfn(ncvrs, 7)}, ${nfn(sovoContest.calcNc(info.voteForN)-ncvrs, 7)}")
    }

    companion object {
        val nameWidth = 50
        val header = " id, ${trunc("name", nameWidth)},    sovoNc,   ncvrs,    diff"
    }
}

////////////////////////////////////////////////////////////////////
// Clca: create simulated cvrs for the redacted groups, for a full CLCA audit with hasStyles=true.
// OA: Create a OneAudit where pools are from the redacted cvrs.

fun createBoulderElection(
    electionName: String,
    input: BoulderInput,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    distributeOvervotes: List<Int>, // maybe no default
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true,
) {

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    CountyElectionSimCvrs.logger.info {"-------------- createBoulderElection $electionName in $topdir"}

    createBoulderElectionWithSovo(electionName, input.corlaCvrs(), input.sovo(), topdir, creation, roundConfig,
        distributeOvervotes, mvrSource, hasStyle, clear = false)
}

fun createBoulderElection(
    electionName: String,
    cvrExportFile: String,
    sovoFile: String,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    distributeOvervotes: List<Int>, // maybe no default
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true,
) {

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    CountyElectionSimCvrs.logger.info {"-------------- createBoulderElection $electionName in $topdir"}

    val sovo = if (sovoFile.startsWith("/resources/")) readBoulderSOVfromResourcePath(sovoFile, electionName)
    else readBoulderStatementOfVotes(sovoFile, electionName)

    val corlaCvrs = if (cvrExportFile.startsWith("/resources/"))
        readCorlaCvrsFromResource(cvrExportFile, redaction = RedactionBoulder())
    else readCorlaCvrsFromFile(cvrExportFile, redaction = RedactionBoulder())

    // TODO get rid of
    createBoulderElectionWithSovo(electionName, corlaCvrs, sovo, topdir, creation, roundConfig, distributeOvervotes, mvrSource, hasStyle, clear = false)
}

fun createBoulderElectionWithSovo(
    electionName: String,
    corlaCvrs: CorlaCvrsIF,
    sovo: BoulderStatementOfVotes,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    distributeOvervotes: List<Int>,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true,
    clear: Boolean = true,
): Result<AuditRoundIF, ErrorMessages> {

    val stopwatch = Stopwatch()

    if (clear) {
        clearDirectory(Path(topdir))
        Logging.addFileAppender("cases", "$topdir/logs.log")
        CountyElectionSimCvrs.logger.info {"-------------- createBoulderElection $electionName in $topdir"}
    }

    val election = if (electionName.contains("2024clca"))
        CreateBoulderElectionClcaOld(electionName, creation.auditType, corlaCvrs, sovo, distributeOvervotes, mvrSource = mvrSource,
            hasStyle = hasStyle)
    else
        CreateBoulderElection(electionName, creation.auditType, corlaCvrs, sovo, mvrSource = mvrSource, hasStyle = hasStyle)

    createElectionRecord(election, topdir = topdir)
    println("CreateBoulderElection took $stopwatch")

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info{"startFirstRound took $stopwatch"}

    return result
}

///////////////////////////////////////////////////

fun makeClcaContestUAs(
    contests: List<ContestIF>,
    npopMap: Map<Int, Int>,
    hasStyle: Boolean,
    allCvrTabs: Map<Int, ContestTabulation>
): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()
    val regular = ContestWithAssertions.make(contests.filter { !it.isIrv() }, npopMap, true, hasStyle)
    contestsUAs.addAll(regular)

    contests.filter { it.isIrv() }.forEach {
        // assumes contestTab.irvVotes are present
        val irvContest = makeRaireContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!)
        contestsUAs.add(irvContest)
    }

    return contestsUAs
}

// currently
// the contests share the card pools, so its convenient to process them all at once
fun makeOneAuditContests(
    contests: List<ContestIF>, // the contests you want to audit
    npopMap: Map<Int,Int>,  // contestId -> Npop
    oneAuditPools: List<CardPool>,
    hasStyle: Boolean,
    allCvrTabs: Map<Int, ContestTabulation>
): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()
    val regular = ContestWithAssertions.make(contests.filter { !it.isIrv() }, npopMap, true, hasStyle)
    setPoolAssorterAverages(regular, oneAuditPools)
    contestsUAs.addAll(regular)

    contests.filter { it.isIrv() }.forEach {
        // assumes contestTab.irvVotes are present
        val irvContestOA = makeRaireOneAuditContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!, oneAuditPools)
        contestsUAs.add(irvContestOA)
    }

    return contestsUAs
}
