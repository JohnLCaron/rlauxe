package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.ContestVotes
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.math.max

private val logger = KotlinLogging.logger("BoulderElectionOA")

// Use OneAudit; redacted ballots are in pools.
// No redacted CVRs. Cant do IRV.
// specific to 2025 election. TODO: generalize
open class CreateBoulderElection(
    val export: DominionCvrExportCsv,
    val sovo: BoulderStatementOfVotes,
    val isClca: Boolean,
    val hasStyle: Boolean = true,
    val quiet: Boolean = true,
): CreateElectionIF {
    val exportCvrs: List<Cvr> = export.cvrs.map { it.convertToCvr() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infoMap = infoList.associateBy { it.id }

    val countCvrVotes = countCvrVotes()
    val countRedactedVotes = countRedactedVotes() // wrong
    val oaContests: Map<Int, OneAuditContestBoulder> = makeOAContests().associate { it.info.id to it}
    val contests: List<ContestIF>
    val contestsUA : List<ContestUnderAudit>

    val cardPools: List<CardPoolWithBallotStyle> = convertRedactedToCardPool() // convertRedactedToCardPoolPaired(export.redacted, infoMap) // convertRedactedToCardPool2()

    init {
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // first even up with contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeExpectedOvervotes(oaContest0, cardPools)
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // contest 63 has fewest undervotes for card Bs
        val oaContest63 = oaContests[63]!!
        distributeExpectedOvervotes(oaContest63, cardPools)
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // we need to know the diluted Nb before we can create the UAs
        contests = makeContests()

        val manifestTabs = tabulateAuditableCards(createCardManifest(), infoMap)
        val contestNbs = manifestTabs.mapValues { it.value.ncards }

        contestsUA = makeOneAuditContests(hasStyle, contests, contestNbs, cardPools).sortedBy { it.id }

        val totalRedactedBallots = cardPools.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPools.size} cardPools"}
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

    private fun convertRedactedToCardPool(): List<CardPoolWithBallotStyle> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, _) -> key != 12 }
                } else redacted.contestVotes

            // TODO what tis ncards here ??
            val contestTabs = useContestVotes.mapValues{ ContestTabulation(infoMap[it.key]!!, it.value, ncards=0) }
            CardPoolWithBallotStyle(cleanCsvString(redacted.ballotType), redactedIdx, contestTabs, infoMap)
        }
    }

    // make up fake CVRs for the pooled (redacted) votes
    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeRedactedCvrs(cardPool, show))
        }

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
        }

        return rcvrs
    }

    // the redacted Cvrs simulate the real CVRS that are in the pools, for testing and estimation
    private fun makeRedactedCvrs(cardPool: CardPoolWithBallotStyle, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes

        val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
        cardPool.voteTotals.forEach { (contestId, contestTab) ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            val sumVotes = contestTab.nvotes()
            val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
            contestVotes[contestId] = Vunder(contestTab.votes, underVotes, oaContest.info.voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, cardPool.poolName, poolId = cardPool.poolId) // TODO test
        if (cardPool.ncards() != cvrs.size)
            logger.error{"cardPool.ncards ${cardPool.ncards()} cvrsize = ${cvrs.size}"}

        // checkit
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infoMap)
        contestVotes.forEach { (contestId, vunders) ->
            val tv = contestTabs[contestId]!!
            if (!checkEquivilentVotes(vunders.candVotesSorted, tv.votes)) {
                println("  contestId=${contestId}")
                println("  tabVotes=${tv}")
                println("  vunders= ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv.votes))
            }
        }

        val infos = oaContests.mapValues { it.value.info }
        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).toSortedMap()
        cvrTab.forEach { contestId, contestTab ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            if (show) {
                println("contestId=${contestId} group=${cardPool.poolName}")
                println("  redacted= ${contestTab.votes[contestId]}")
                println("  oaContest.undervotes= ${oaContest.redVotes} == ${contestTab.undervotes}")
                println("  contestTab=$contestTab")
                println()
            }
            require(checkEquivilentVotes(cardPool.voteTotals[contestId]!!.votes, contestTab.votes))
        }

        return cvrs
    }

    fun makeOAContests(): List<OneAuditContestBoulder> {
        val oa2Contests = mutableListOf<OneAuditContestBoulder>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                if (countCvrVotes[info.id] != null && countRedactedVotes[info.id] != null) {
                    oa2Contests.add(
                        OneAuditContestBoulder(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
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
                tab.ncards += contestVote.map { it.value }.sum() / info!!.voteForN // wrong, dont use
            }
        }
        return votes
    }

    fun makeContests(): List<ContestIF> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        return infoList.filter { !it.isIrv }.map { info ->
            val oaContest = oaContests[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.ncards()
            val useNc = max( ncards, oaContest.Nc())
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun contestsUA() = contestsUA
    override fun cardPools() = cardPools
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return if (isClca) {
            val simulatedCvrs = makeRedactedCvrs()
            val cvrs =  exportCvrs + simulatedCvrs
            CvrsWithStylesToCardManifest(
                AuditType.CLCA, hasStyle,
                Closer(cvrs.iterator()),
                makePhantomCvrs(contests),
                null
            )
        } else {
            val poolCards =  createCvrsFromPools()
            val cvrs =  exportCvrs + poolCards
            CvrsWithStylesToCardManifest(
                AuditType.ONEAUDIT, hasStyle,
                Closer(cvrs.iterator()),
                makePhantomCvrs(contests),
                styles = cardPools
            )
        }
    }

    fun createCvrsFromPools() : List<Cvr> {
        val cvrs = mutableListOf<Cvr>()

        cardPools.forEach { pool ->
            val cleanName = cleanCsvString(pool.poolName)
            repeat(pool.ncards()) { poolIndex ->
                cvrs.add(
                    Cvr(
                        id = "pool${cleanName} card ${poolIndex + 1}",
                        phantom = false,
                        votes = pool.voteTotals.mapValues { intArrayOf() }, // empty candidates
                        poolId = pool.poolId
                    )
                )
            }
        }
        return cvrs
    }
}

////////////////////////////////////////////////////////////////////
// Clca: create simulated cvrs for the redacted groups, for a full CLCA audit with hasStyles=true.
// OA: Create a OneAudit where pools are from the redacted cvrs.
fun createBoulderElection(
    cvrExportFile: String,
    sovoFile: String,
    topdir: String,
    auditDir: String = "$topdir/audit",
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    auditConfigIn: AuditConfig? = null,
    auditType : AuditType,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()
    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")

    val config = if (auditConfigIn != null) auditConfigIn
        else if (auditType.isClca())
            AuditConfig(
                AuditType.CLCA,
                hasStyle = true,
                riskLimit = riskLimit,
                contestSampleCutoff = 20000,
                minRecountMargin = minRecountMargin,
                nsimEst = 10,
            )
        else if (auditType.isOA())
            AuditConfig(
            AuditType.ONEAUDIT, hasStyle=true, riskLimit=riskLimit, contestSampleCutoff=20000, minRecountMargin=minRecountMargin, nsimEst=10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    else throw RuntimeException("unsupported audit type $auditType")

    val election = CreateBoulderElection(export, sovo, isClca = auditType.isClca(), hasStyle=config.hasStyle)

    CreateAudit("boulder", config, election, auditDir = auditDir, clear = clear)
    println("createBoulderElectionOAnew took $stopwatch")
}

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
