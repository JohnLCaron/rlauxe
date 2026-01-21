package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.ContestVotes
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
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
class CreateBoulderElection(
    val export: DominionCvrExportCsv,
    val sovo: BoulderStatementOfVotes,
    val isClca: Boolean,
    val poolsHaveOneCardStyle: Boolean = true,
    val distributeOvervotes: List<Int> = listOf(0, 63),
): CreateElectionIF {
    val exportCvrs: List<Cvr> = export.cvrs.map { it.convertToCvr() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infoMap = infoList.associateBy { it.id }

    val countCvrVotes = countCvrVotes()
    val countRedactedVotes = countRedactedVotes() // wrong
    val oaContests: Map<Int, OneAuditContestBoulder> = makeOAContests().associate { it.info.id to it}
    val cardPoolBuilders: List<OneAuditPoolWithBallotStyle> = convertRedactedToCardPool()

    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val simulatedCvrs: List<Cvr>

    init {
        //// the redacted groups dont have undervotes, so we do some fancy dancing to generate reasonable undervote counts
        // todo this seems to depend on ncards, which we set to 0
        oaContests.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}
        // 2024
        // first even up with contest 0, since it has the fewest undervotes
        // then contest 63 has fewest undervotes for card Bs
        distributeOvervotes.forEach { contestId ->
            val oaContest = oaContests[contestId]!!
            distributeExpectedOvervotes(oaContest, cardPoolBuilders)
            oaContests.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}
        }
        // cardPools = cardPoolBuilders.map { it.toOneAuditPool() } // why ?

        // we need to know the diluted Nb before we can create the UAs
        contests = makeContests()
        simulatedCvrs = makeRedactedCvrs()

        val manifestTabs = tabulateAuditableCards(createCardManifest(), infoMap)
        val npopMap = manifestTabs.mapValues { it.value.ncards }

        contestsUA = if (isClca) ContestWithAssertions.make(contests, npopMap, isClca=true, )
            else makeOneAuditContests(contests, npopMap, cardPoolBuilders)

        val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}
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

    private fun convertRedactedToCardPool(): List<OneAuditPoolWithBallotStyle> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, _) -> key != 12 }
                } else redacted.contestVotes

            //// the redacted groups dont have undervotes, so we have to generate reasonable undervote counts
            // for this pass we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = useContestVotes.mapValues{ ContestTabulation(infoMap[it.key]!!, it.value, ncards=0) }

            // data class Population(
            //    val name: String,
            //    val id: Int,
            //    val possibleContests: IntArray, // the list of possible contests.
            //    val exactContests: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
            //) : PopulationIF {
            //    var ncards = 0
            val name = cleanCsvString(redacted.ballotType)
            val id = redactedIdx
            OneAuditPoolWithBallotStyle(name, id, hasSingleCardStyle=poolsHaveOneCardStyle, contestTabs, infoMap)
        }
    }

    // make simulated CVRs for all the pools
    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPoolBuilders.forEach { cardPool ->
            rcvrs.addAll(makeCvrsForOnePool(cardPool, infoMap))
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
    private fun makeCvrsForOnePool(cardPool: OneAuditPoolIF, infos: Map<Int, ContestInfo>) : List<Cvr> { // contestId -> candidateId -> nvotes

        val poolVunders = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
        cardPool.regVotes().forEach { (contestId, regVote) ->
            val sumVotes = regVote.ncards()
            val voteForN = infos[contestId]?.voteForN ?: 1
            val underVotes = cardPool.ncards() * voteForN - sumVotes
            poolVunders[contestId] = Vunder.fromNpop(contestId, underVotes, cardPool.ncards(), regVote.votes, voteForN)
        }

        val cvrs = makeVunderCvrs(poolVunders, cardPool.poolName, poolId = cardPool.poolId)
        // the number of cvrs can vary when there are multiple contests: artifact of simulating the cvrs
        //if (cardPool.ncards() != cvrs.size)
        //    logger.info{"cardPool.ncards ${cardPool.ncards()} cvrs.size = ${cvrs.size}"}

        // check it
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infoMap)
        poolVunders.forEach { (contestId, vunders) ->
            val tv = contestTabs[contestId]!!
            if (!checkEquivilentVotes(vunders.candVotesSorted, tv.votes)) {
                println("cvrs differ from cardPool")
                /* println("  info=${infoMap[contestId]}")
                println("  cardPool=${cardPool.voteTotals[contestId]}")
                println("  poolVotes=${poolVunders[contestId]}")
                println("    vunders= ${vunders.candVotesSorted}")
                val tvVotesSorted: Map<Int, Int> = tv.votes.toList().sortedBy{ it.second }.reversed().toMap() // reverse sort by largest vote
                println("         tv= ${tvVotesSorted}")
                println("  cvrsTab=${tv}\n")
                // TODO track down why this happens; maybe just inexact simulation? cause verification to fail?
                require(checkEquivilentVotes(vunders.candVotesSorted, tv.votes)) */
            }
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
                tab.ncards += contestVote.map { it.value }.sum() / info!!.voteForN // TODO wrong, dont use
            }
        }
        return votes
    }

    fun makeContests(): List<ContestIF> {
        println("ncontests with info = ${infoList.size}")

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
    override fun populations() = if (isClca) emptyList() else cardPoolBuilders
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return if (isClca) { // TODO and hasUndervotes
            val cvrs =  exportCvrs + simulatedCvrs
            CvrsWithPopulationsToCardManifest(
                AuditType.CLCA,
                Closer(cvrs.iterator()),
                makePhantomCvrs(contests),
                null
            )
        } else {
            val poolCards =  createCvrsFromPools()
            val cvrs =  exportCvrs + poolCards
            CvrsWithPopulationsToCardManifest(
                AuditType.ONEAUDIT,
                Closer(cvrs.iterator()),
                makePhantomCvrs(contests),
                populations = cardPoolBuilders
            )
        }
    }

    fun createCvrsFromPools() : List<Cvr> {
        val cvrs = mutableListOf<Cvr>()

        cardPoolBuilders.forEach { pool ->
            val cleanName = cleanCsvString(pool.poolName)
            repeat(pool.ncards()) { poolIndex ->
                cvrs.add(
                    Cvr(
                        id = "pool${cleanName} card ${poolIndex + 1}",
                        phantom = false,
                        votes = pool.regVotes().mapValues { intArrayOf() }, // empty votes
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
    poolsHaveOneCardStyle: Boolean,
    clear: Boolean = true,
    )
{
    val stopwatch = Stopwatch()
    val variation = if (sovoFile.contains("2025") || sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")

    val config = if (auditConfigIn != null) auditConfigIn
        else if (auditType.isClca())
            AuditConfig(
                AuditType.CLCA,
                riskLimit = riskLimit,
                minRecountMargin = minRecountMargin,
                nsimEst = 10,
            )
        else if (auditType.isOA())
            AuditConfig( // TODO hasStyle=false ?
                AuditType.ONEAUDIT, riskLimit=riskLimit, minRecountMargin=minRecountMargin, nsimEst=10,
                oaConfig = OneAuditConfig(OneAuditStrategyType.generalAdaptive, useFirst = false)
            )
    else throw RuntimeException("unsupported audit type $auditType")

    val election = CreateBoulderElection(export, sovo, isClca = auditType.isClca(), poolsHaveOneCardStyle)

    CreateAudit("boulder", config, election, auditDir = auditDir, clear = clear)
    println("createBoulderElectionOAnew took $stopwatch")
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
