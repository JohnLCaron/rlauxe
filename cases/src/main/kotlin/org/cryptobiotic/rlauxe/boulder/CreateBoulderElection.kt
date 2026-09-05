package org.cryptobiotic.rlauxe.boulder

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.auditcenter.CountyElectionSimCvrs
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.cvr.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.cvr.parseIrvContestName
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCardsForOnePoolV
import org.cryptobiotic.rlauxe.irv.IrvContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
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

enum class BoulderVariantEnum { Phantoms, OnePool, Styles, Sim }
class BoulderVariant(variantEnum: BoulderVariantEnum) {
    val phantoms = (variantEnum == BoulderVariantEnum.Phantoms)
    val onePool = (variantEnum == BoulderVariantEnum.OnePool)
    val styles = (variantEnum == BoulderVariantEnum.Styles)
    val sim = (variantEnum == BoulderVariantEnum.Sim)   // created simulated cvrs from redacted pools
}

class CreateBoulderElection(
    val electionName: String,
    val auditType: AuditType,
    val corlaCvrs: CorlaCvrsIF,
    val sovo: BoulderStatementOfVotes,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean, // TODO
    variantEnum: BoulderVariantEnum,
): ElectionBuilder {
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }

    val contestBuilders: Map<Int, BoulderContestBuilderIF> // make visible for debugging
    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val exportCvrs: List<AuditableCard>
    val redactedCvrs: List<AuditableCard>  // redacted cvrs
    val allCards: List<AuditableCard>
    val redactedPools: List<CardPool>
    val mvrs: List<AuditableCard>
    val ncards: Int
    val variant = BoulderVariant(variantEnum)

    init {
        val cvrTabs = countCvrVotes()
        val redactedTabs = countRedactedVotes()  // wrong
        val cardPoolBuilders = if (variant.onePool) convertRedactedToOneCardPool(corlaCvrs.redactedGroups())
            else convertRedactedToCardPool(corlaCvrs.redactedGroups())

        contestBuilders = makeBoulderContestBuilders(cvrTabs, redactedTabs, cardPoolBuilders)
                            .associate { it.contestId to it}

        redactedPools = cardPoolBuilders.map { it.build() }

        // we need to know the diluted Nb before we can create the UAs
        // make fake IRV contest for the purpose of setting the phantoms.
        contests = makeContests(contestBuilders)

        // these are the mvrs, must have votes and styleIds,
        exportCvrs  = corlaCvrs.cvrs().map { it.convertToCard() }
        redactedCvrs = if (variant.phantoms) emptyList() else makeSimulatedCards(redactedPools)

        // need to know the phantoms to calculate allCvrs and Npops
        val phantoms = makePhantomCards(contests, 1)
        logger.debug {"made ${phantoms.size} phantom cards"}

        allCards = exportCvrs + redactedCvrs + phantoms // in memory
        this.ncards = allCards.size
        val npops = tabulateNpops(allCards, infoList)

        // TODO cvrTabs dont have the irv part, so will fail in the raire library
        contestsUA = makeContestWAs(contests, npops, cvrTabs, redactedPools, )

        //contestsUA = if (auditType.isClca()) ContestWithAssertions.make(contests, npops, isClca=true, hasStyle = hasStyle)
        //    else makeOneAuditContests(contests, npops, redactedPools, hasStyle = hasStyle)

        val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}

        // TODO put in verify
        // checkNpops(allCvrs, createCards(), infoList)

        // needed ??
        // mvrs = mvrsToAuditableCardsList(allCards, cardPools())
        mvrs = addIndexToMvrs(allCards)
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
            //// the redacted groups dont have undervotes, so we should try to generate reasonable undervote counts
            // but... now we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }

            val name = "redacted " + cleanCsvString(redacted.ballotType)
            // in this case, nlines == ncards
            val hasExactContests = !redacted.ballotType.contains("&") // has multiple card styles
            CardPoolBuilder.fromMinVotesNeeded(name, id++, hasExactContests=hasExactContests, infos, contestTabs).setNcards(redacted.ncards())
        }
    }

    private fun convertRedactedToOneCardPool(redacteds: List<RedactedGroup>): List<CardPoolBuilder> {
        var ncards = 0
        var sumTabs = mutableMapOf<Int, ContestTabulation>()
        redacteds.forEach { redacted: RedactedGroup ->
            val groupTab = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }
            sumTabs.sumContestTabulations(groupTab)
            ncards += redacted.ncards()
        }
        return listOf(CardPoolBuilder.fromMinVotesNeeded("RedactedPool", 1, hasExactContests=false, infos, sumTabs)
            .setNcards(ncards))
    }

    // make simulated CVRs for all the pools
    fun makeSimulatedCards(cardPools: List<CardPool>) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<AuditableCard>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeCardsForOnePool(cardPool))
        }
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests
    private fun makeCardsForOnePool(cardPool: CardPool) : List<AuditableCard> { // contestId -> candidateId -> nvotes
        val poolVunders = cardPool.possibleContests().associate { Pair(it, cardPool.votesAndUndervotes(it)) }
        val cards = makeCardsForOnePoolV(poolVunders, pool=cardPool)

        // check it
        val cvrTabs: Map<Int, ContestTabulation> = tabulateCards(cards.iterator(), infos)
        poolVunders.forEach { (contestId, vunder) ->
            val poolTab = cardPool.contestTabs[contestId]!!
            val cvrTab = cvrTabs[contestId]!!
            if (!checkEquivilentVotes(vunder.cands(), cvrTab.votes)) {
                logger.warn{"cvrs differ from cardPool"}
                println("  info=${infos[contestId]}")
                println("  cardPool.ncards=${cardPool.ncards()} cards.size=${cards.size}")
                println("  cardPoolTab=$poolTab")
                println("  cvrTab=$cvrTab")
                println("  vunder= ${vunder}")
                // TODO track down why this happens; maybe just inexact simulation? causes verification to fail?
                println("  checkEquivilentVotes=${checkEquivilentVotes(vunder.cands(), cvrTab.votes)}")
                println()
                throw RuntimeException("makeCvrsForOnePool fails")
            }
        }

        return cards
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
                                   redactedTabs: Map<Int, ContestTabulation>,
                                   cardPools: List<CardPoolBuilder>,
   ): List<BoulderContestBuilderIF> {
        val oaContests = mutableListOf<BoulderContestBuilderIF>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            // its possible that all cvrs for a contest are redacted
            if (sovoContest != null && (cvrTabs[info.id] != null || redactedTabs[info.id] != null)) {
                val cb = BoulderContestBuilder(auditType, info, sovoContest, cvrTabs[info.id], redactedTabs[info.id], variant)
                oaContests.add(cb)
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oaContests
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
        if (auditType.isOA()) setPoolAssorterAverages(regular, oneAuditPools)
        contestsUAs.addAll(regular)

        contests.filter { it.isIrv() }.forEach {
            // assumes contestTab.irvVotes are present
            val irvContest = if (!auditType.isOA())
                makeRaireContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!)
            else
                makeRaireOneAuditContest(it.info(), allCvrTabs[it.id]!!, it.Nc(), Nbin=npopMap[it.id]!!, oneAuditPools)
            contestsUAs.add(irvContest)
        }

        return contestsUAs
    }

    ////////////////////////////////////////////////////////////////

    override fun electionInfo() =
        ElectionInfo(electionName, auditType, ncards(), contestsUA.size, true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA

    override fun cardStyles() = null
    override fun cardPools() = redactedPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCardsFromMvrs(mvrs)
    override fun ncards() = ncards

    // TODO do you really need to do this ??
    // adding an index and the pool style
    fun mvrsToAuditableCardsList(
        mvrs: List<AuditableCard>,
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

            AuditableCard(
                org.id,
                null,
                cardIndex++,
                0,
                phantom = org.phantom,
                styleId = styleId,
                org.contestIds, org.contestStarts, org.candidates,
                poolId = org.poolId,
            )
        }
    }

    fun addIndexToMvrs(mvrs: List<AuditableCard>): List<AuditableCard> {
        var cardIndex = 1 // 1 based index

        // add the index
        val result = mutableListOf<AuditableCard>()
        mvrs.forEach { org ->
            result.add(org.copy(index = cardIndex ))
            cardIndex++
        }
        return result
    }

    fun createCardsFromMvrs(mvrs: List<AuditableCard>): CloseableIterator<AuditableCard> {
        // remove cvrs for cards in the pools
        val mvrIter = Closer(mvrs.iterator())
        val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
            if (org.poolId != null && auditType.isOA()) AuditableCard.removeVotes(org) else org
        }
        return transformer
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

interface BoulderContestBuilderIF {
    val contestId: Int
    val contestName: String
    fun build(info: ContestInfo): ContestIF
}

class BoulderContestBuilder(val auditType: AuditType,
                              val info: ContestInfo,
                              val sovoContest: SovoContestVotes,
                              cvrTab: ContestTabulation?,
                              redactedTab: ContestTabulation?,
                              val variant: BoulderVariant
): BoulderContestBuilderIF {

    override val contestId = info.id
    override val contestName = info.name

    val cvrsTotalCards: Int
    val poolTotalCards: Int
    val candVoteTotals: Map<Int, Int>
    val useNc: Int
    val ncvrs: Int

    init {
        candVoteTotals = when {
            (cvrTab == null) -> redactedTab!!.votes
            (redactedTab) == null -> cvrTab.votes
            else -> {
                val sum = mutableMapOf<Int, Int>()
                sum.mergeReduce(listOf(cvrTab.votes, redactedTab.votes))
                sum
            }
        }

        cvrsTotalCards = cvrTab?.ncardsTabulated ?: 0
        poolTotalCards = redactedTab?.ncards() ?: 0
        ncvrs = cvrsTotalCards + poolTotalCards

        val diff = sovoContest.calcNcast(info.voteForN) - ncvrs
        if (ncvrs > sovoContest.calcNcast(info.voteForN)) {
            logger.warn{"contest ${info.id} ncvrs $ncvrs > ${sovoContest.calcNcast(info.voteForN)} sovoContest.calcNc; adjust Nc= ${-diff} "}
        } else if (ncvrs != sovoContest.calcNcast(info.voteForN)) {
            logger.debug{"contest ${info.id} ncvrs $ncvrs < ${sovoContest.calcNcast(info.voteForN)} sovoContest.calcNc; phantoms = $diff"}
        }
        useNc = max( ncvrs, sovoContest.totalBallots - sovoContest.totalOverVotes)
    }

    override fun build(info: ContestInfo): ContestIF {
        val candVotes = candVoteTotals.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
        return if (info.isIrv) // TODO
                IrvContest(info, winners=listOf(0), useNc, Ncast=ncvrs, undervotes=0) // TODO this is fake...
            else if (variant.phantoms) {
                val nphantoms = useNc - cvrsTotalCards // the redacted cards arent counted, so become phantoms
                ContestWithPhantoms(info, candVotes, useNc, ncvrs, nphantoms)
            } else
                Contest(info, candVotes, useNc, ncvrs)
    }

    override fun toString() = buildString {
        append("${nfn(info.id,3)}, ${trunc(info.name, nameWidth)}, ")
        append(" ${nfn(sovoContest.calcNcast(info.voteForN), 8)}, ${nfn(ncvrs, 7)}, ${nfn(sovoContest.calcNcast(info.voteForN)-ncvrs, 7)}")
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
    input: BoulderInput,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true, // TODO wtf ??
    variant: BoulderVariantEnum,
 ) {

    clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createBoulderElection ${input.electionName} in $topdir"}

    createBoulderElectionWithSovo(input.electionName, input.corlaCvrs(), input.sovo(), topdir, creation, roundConfig,
        mvrSource, hasStyle, clear = false, variant)
}

fun createBoulderElectionWithSovo(
    electionName: String,
    corlaCvrs: CorlaCvrsIF,
    sovo: BoulderStatementOfVotes,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    hasStyle: Boolean = true,
    clear: Boolean = true, // TODO
    variant: BoulderVariantEnum,
    ): Result<AuditRoundIF, ErrorMessages> {

    val stopwatch = Stopwatch()

    if (clear) {
        clearDirectory(Path(topdir))
        Logging.addFileAppender("cases", "$topdir/logs.log")
        CountyElectionSimCvrs.logger.info {"-------------- createBoulderElection $electionName in $topdir"}
    }

    //val election = if (electionName.contains("2024clca"))
    //    CreateBoulderElectionClcaOld(electionName, creation.auditType, corlaCvrs, sovo, emptyList(), mvrSource = mvrSource,
    //        hasStyle = hasStyle)
    //else

    val election = CreateBoulderElection(electionName, creation.auditType, corlaCvrs, sovo, mvrSource = mvrSource,
        hasStyle = hasStyle, variant)

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
