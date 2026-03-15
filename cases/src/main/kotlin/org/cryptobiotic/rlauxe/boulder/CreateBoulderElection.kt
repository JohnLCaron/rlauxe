package org.cryptobiotic.rlauxe.boulder

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.ContestVotes
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForPool
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.checkNpops
import org.cryptobiotic.rlauxe.utils.tabulateNpops
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.math.max

private val logger = KotlinLogging.logger("BoulderElectionOA")
private val debugUndervotes = false

// Use OneAudit; redacted ballots are in pools. Cant do IRV.
// specific to 2024 election. TODO: generalize
class CreateBoulderElection(
    val auditType: AuditType,
    val export: DominionCvrExportCsv,
    val sovo: BoulderStatementOfVotes,
    val distributeOvervotes: List<Int> = listOf(0, 63),
): CreateElectionIF {
    val exportCvrs: List<Cvr> = export.cvrs.map { it.convertToCvr() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infoMap = infoList.associateBy { it.id }

    val countCvrVotes = countCvrVotes()
    val countRedactedVotes = countRedactedVotes() // wrong
    val boulderContestBuilders: Map<Int, BoulderContestBuilder> = makeBoulderContestBuilders().associate { it.info.id to it}
    val cardPoolBuilders: List<OneAuditPoolFromBallotStyle> = convertRedactedToCardPool()
    val ncards: Int

    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val simulatedCvrs: List<Cvr>  // redacted cvrs
    val allCvrs: List<Cvr>  // redacted cvrs

    init {
        //// the redacted groups dont have undervotes, so we do some fancy dancing to generate reasonable undervote counts
        boulderContestBuilders.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}

        // estimate undervotes based on each precinct having a single ballot style
        val undervotesByContest = mutableMapOf<BoulderContestBuilder, Int>() // contestId ->
        boulderContestBuilders.values.forEach {
            undervotesByContest[it] = it.poolTotalCards() - it.expectedPoolNCards()
        }

        // 2024
        // first even up with contest 0, since it has the fewest undervotes
        // then contest 63 has fewest undervotes for card Bs
        distributeOvervotes.forEach { contestId ->
            val oaContest = boulderContestBuilders[contestId]!!
            distributeExpectedOvervotes(oaContest, cardPoolBuilders)
            boulderContestBuilders.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}
        }

        if (debugUndervotes) {
            undervotesByContest.forEach { (cb, before) ->
                val needAfter = cb.poolTotalCards() - cb.expectedPoolNCards()
                println("  ${cb.contestId} $before $needAfter ")
            }
        }

        // we need to know the diluted Nb before we can create the UAs
        contests = makeContests()
        simulatedCvrs = makeRedactedCvrs()

        val phantoms = makePhantomCvrs(contests)
        allCvrs = exportCvrs + simulatedCvrs + phantoms

        val npops = tabulateNpops(allCvrs, infoList)
        this.ncards = allCvrs.size

        contestsUA = if (auditType.isClca()) ContestWithAssertions.make(contests, npops, isClca=true, )
            else makeOneAuditContests(contests, npops, cardPoolBuilders)

        val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}

        checkNpops(allCvrs, createCards(), infoList)
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
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestName(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    private fun convertRedactedToCardPool(): List<OneAuditPoolFromBallotStyle> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, _) -> key != 12 }
                } else redacted.contestVotes

            //// the redacted groups dont have undervotes, so we have to generate reasonable undervote counts
            // for this pass we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = useContestVotes.mapValues{ ContestTabulation(infoMap[it.key]!!, it.value, ncards=0) }

            val name = cleanCsvString(redacted.ballotType)
            val id = redactedIdx
            OneAuditPoolFromBallotStyle(name, id, hasSingleCardStyle=true, contestTabs, infoMap)
        }
    }

    // make simulated CVRs for all the pools
    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPoolBuilders.forEach { cardPool ->
            rcvrs.addAll(makeCvrsForOnePool(cardPool))
        }

        /*
        val infos = oaContests.mapValues { it.value.info }
        val rcvrTabs = tabulateCvrs(rcvrs.iterator(), infos).toSortedMap()
        rcvrTabs.forEach { contestId, contestTab ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            val redUndervotes = oaContest.redUndervotes
            if (show) {
                println("contestId=${contestId}")
                println("  redacted= ${oaContest.red.votes}")
                println("  contestTab=${contestTab.votes.toSortedMap()}")
                println("  oaContest.undervotes= ${redUndervotes} == contestTab.undervotes = ${contestTab.undervotes}")
                println()
            }
            require(checkEquivilentVotes(oaContest.red.votes, contestTab.votes))
            // if (voteForN[contestId] == 1) require(redUndervotes == contestTab.undervotes) // TODO
        } */

        return rcvrs
    }

    // make simulated CVRs for one pool, all contests
    private fun makeCvrsForOnePool(cardPool: OneAuditPoolFromBallotStyle) : List<Cvr> { // contestId -> candidateId -> nvotes
        val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val cvrs =
            makeCvrsForPool(poolVunders, cardPool.poolName, poolId = cardPool.poolId, cardPool.hasSingleCardStyle)
        // TODO is it true that the number of cvrs can vary when there are multiple contests ?
        //if (cardPool.ncards() != cvrs.size)
        //    logger.warn{"cardPool.ncards ${cardPool.ncards()} != cvrs.size = ${cvrs.size}"}

        // check it
        val cvrTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infoMap)
        poolVunders.forEach { (contestId, vunder) ->
            val poolTab = cardPool.voteTotals[contestId]!!
            val cvrTab = cvrTabs[contestId]!!
            if (!checkEquivilentVotes(vunder.cands(), cvrTab.votes)) {
                logger.warn{"cvrs differ from cardPool"}
                println("  info=${infoMap[contestId]}")
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
        // if hasSingleCardStyle, then missing has to be zero
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

    fun makeBoulderContestBuilders(): List<BoulderContestBuilder> {
        val oa2Contests = mutableListOf<BoulderContestBuilder>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                if (countCvrVotes[info.id] != null && countRedactedVotes[info.id] != null) {
                    oa2Contests.add(
                        BoulderContestBuilder(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
                    )
                }
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oa2Contests
    }

    fun countVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val cvrVotes =  countCvrVotes()
        val redVotes =  countRedactedVotes()
        val allVotes = mutableMapOf<Int, ContestTabulation>()
        allVotes.sumContestTabulations(cvrVotes)
        allVotes.sumContestTabulations(redVotes)
        return allVotes
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(infoMap[contestVote.contestId]!!) }
                tab.addVotes(contestVote.candVotes.toIntArray(), phantom=false)
            }
        }
        return votes
    }

    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.redacted.forEach { redacted: RedactedGroup ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation(infoMap[contestId]!!) }
                contestVote.forEach { (cand, vote) -> tab.addVote(cand, vote) }
                val info = infoMap[contestId]

                // TODO approx
                tab.ncardsTabulated += contestVote.map { it.value }.sum() / info!!.voteForN // TODO wrong, dont use
            }
        }
        return votes
    }

    fun makeContests(): List<ContestIF> {
        println("ncontests with info = ${infoList.size}")

        return infoList.filter { !it.isIrv }.map { info ->
            val oaContest = boulderContestBuilders[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.ncards()
            val useNc = max( ncards, oaContest.Nc())
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun electionInfo() = ElectionInfo(auditType, ncards(), contestsUA.size, true, poolsHaveOneCardStyle=true)
    override fun contestsUA() = contestsUA
    override fun batches() = if (auditType.isClca()) emptyList() else cardPoolBuilders
    override fun cardPools() = if (auditType.isClca()) emptyList() else cardPoolBuilders.map { it.toOneAuditPool() }
    override fun createUnsortedMvrsInternal() = allCvrs
    override fun createUnsortedMvrsExternal() = null

    override fun cards() = createCards()
    override fun ncards() = ncards

    fun createCards(): CloseableIterator<AuditableCard> {
        // same cvrs for CLCA and OneAudit
        return CvrsToCardManifest(
            auditType,
            Closer(allCvrs.iterator()), // use the mvrs as the cvrs
            null,
            batches = cardPoolBuilders
        )
    }
}

////////////////////////////////////////////////////////////////////
// Clca: create simulated cvrs for the redacted groups, for a full CLCA audit with hasStyles=true.
// OA: Create a OneAudit where pools are from the redacted cvrs.
fun createBoulderElection(
    cvrExportFile: String,
    sovoFile: String,
    auditdir: String,
    auditType : AuditType,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    minMargin: Double = 0.0,
    maxSamplePct: Double = 0.0,
    auditConfigIn: AuditConfig? = null,
    mvrFuzz: Double? = null,
    contestSampleCutoff: Int?,
    auditSampleCutoff: Int?,
    removeMaxContests: Int? = null,
): Result<AuditRoundIF, ErrorMessages> {

    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2025") || sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")

    val election = CreateBoulderElection(auditType, export, sovo)
    createElectionRecord("boulder2024", election, auditDir = auditdir)
    println("CreateBoulderElection took $stopwatch")

    val config = if (auditConfigIn != null) auditConfigIn
        else if (auditType.isClca())
            AuditConfig(
                AuditType.CLCA,
                riskLimit = riskLimit,
                minRecountMargin = minRecountMargin,
                minMargin = minMargin,
                maxSamplePct=maxSamplePct,
                nsimEst = 20,
                removeMaxContests=removeMaxContests,
                contestSampleCutoff = contestSampleCutoff,
                auditSampleCutoff = auditSampleCutoff,
                persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
                clcaConfig = ClcaConfig(fuzzMvrs=mvrFuzz)
            )
        else if (auditType.isOA())
            AuditConfig(
                AuditType.ONEAUDIT,
                riskLimit=riskLimit,
                minRecountMargin=minRecountMargin,
                minMargin=minMargin,
                maxSamplePct=maxSamplePct,
                nsimEst=20,
                removeMaxContests=removeMaxContests,
                contestSampleCutoff = contestSampleCutoff,
                auditSampleCutoff = auditSampleCutoff,
                persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,  // write mvrs to private
                clcaConfig = ClcaConfig(fuzzMvrs=mvrFuzz)
            )
    else throw RuntimeException("unsupported audit type $auditType")

    createAuditRecord(config, election, auditDir = auditdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info{"startFirstBoulderRound took $stopwatch"}

    return result
}

///////////////////////////////////////////////////////////////////////////////////////////

fun checkVotesVsSovo(contests: List<Contest>, sovo: BoulderStatementOfVotes, mustAgree: Boolean = true) {
    // we are making the contest votes from the cvrs. how does it compare with official tally ??
    contests.forEach { contest ->
        val sovoContest: BoulderContestVotes? = sovo.contests.find { it.contestTitle == contest.name }
        if (sovoContest == null) {
            print("*** ${contest.name} not found in BoulderStatementOfVotes")
        } else {
            //println("sovoContest = ${sovoContest!!.candidateVotes}")
            //println("    contest = ${contest.votes}")
            sovoContest.candidateVotes.forEach { (sovoCandidate, sovoVote) ->
                val candidateId = contest.info.candidateNames[sovoCandidate]
                if (candidateId == null) {
                    print("*** $sovoCandidate not in ${contest.info.candidateNames}")
                }
                val contestVote = contest.votes[candidateId]!!
                if (contestVote != sovoVote) {
                    println("*** ${contest.name} '$sovoCandidate' $contestVote != $sovoVote")
                }
                // createBoulder23 doesnt agree on contest "City of Louisville City Council Ward 2 (4-year term)"
                // see ColbertDiscrepency.csv, FaheyDiscrepency.csv
                if (mustAgree) require(contestVote == sovoVote)
            }
        }
    }
}

fun parseContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Vote For=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Vote For=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(")").toInt()
    return Pair(namet, ncand)
}

// City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)
fun parseIrvContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Number of positions=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Number of positions=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(",").toInt()
    return Pair(namet, ncand)
}


private val regex = Regex("[,]") // Matches '!', ',' or any digit
fun cleanCsvString(originalString: String) = originalString.replace(regex, "")

