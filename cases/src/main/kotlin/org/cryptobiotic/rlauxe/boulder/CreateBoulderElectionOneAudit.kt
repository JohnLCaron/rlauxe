package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeIrvContestVotes
import org.cryptobiotic.rlauxe.raire.makeRaireContests
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

// using sovo just to define the contests. TODO: tests there are some votes
class BoulderElectionOneAudit(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    quiet: Boolean = true,
    val includeCvrs: Boolean = true,
): BoulderElection(export,sovo, quiet)
{
    val oaContests = makeOAContests().associate { it.info.id to it}
    val cardPools: List<CardPool> = convertRedactedToCardPools()
    val redactedCvrs = makeRedactedCvrs()
    val allCvrs = exportCvrs + redactedCvrs // TODO could be CvrExport ??

    // TODO IRV?
    fun makeOAContests(): List<OneAuditContest> {
        val countCvrVotes = countCvrVotes()
        val countRedactedVotes = countRedactedVotes()

        val oaContests = mutableListOf<OneAuditContest>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                val oaContest = OneAuditContest(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
                oaContest.allocateUndervotes(export.redacted)
                oaContests.add( oaContest)
            }
            else println("*** cant find '${info.name}' in BoulderStatementOfVotes")
        }
        return oaContests
    }

    // cant do this until we have oaContest.undervotes
    fun convertRedactedToCardPools(): List<CardPool> {
        val result = mutableListOf<CardPool>()

        export.redacted.forEachIndexed { redactedCount, redacted ->
            // val poolName: String,
            // val poolId: Int,
            // val irvIds: Set<Int>,
            // val contestInfos: Map<Int, ContestInfo>)
            // val contestTabulations = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
            // val irvVoteConsolidations = mutableMapOf<Int, IrvContestVotes>()  // contestId -> IrvContestVotes
            val cardPool = CardPool(redacted.ballotType, redactedCount, emptySet(), infoMap)

            val tabs = cardPool.contestTabulations  // contestId -> candidateId -> nvotes
            redacted.contestVotes.forEach { (contestId, candCounts) ->
                val info = infoMap[contestId]!!
                val tab = ContestTabulation(info.voteForN)
                tabs[contestId] = tab
                candCounts.forEach { (cand, vote) -> tab.addVote(cand, vote)}

                //  redVotes = redacted.votes.map { it.value }.sum()
                val redVotes = candCounts.map { it.value }.sum()
                //  redNcards = (redVotes + redUndervotes) / info.voteForN
                val oaContest = oaContests[contestId]!!
                tab.ncards = (redVotes + oaContest.undervotes[redacted.ballotType]!!) / info.voteForN
            }
            result.add(cardPool)
        }
        return result
    }

    override fun makeRedactedCvrs(show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeRedactedCvrs(cardPool, show))
        }

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val rcvrTabs = tabulateCvrs(rcvrs.iterator(), voteForN).toSortedMap()
        rcvrTabs.forEach { contestId, contestTab ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            val redUndervotes = oaContest.redUndervotes()
            if (show) {
                println("contestId=${contestId}")
                println("  redacted= ${oaContest.red.votes}")
                println("  contestTab=${contestTab.votes.toSortedMap()}")
                println("  oaContest.undervotes= ${redUndervotes} == contestTab.undervotes = ${contestTab.undervotes}")
                println()
            }
            require(checkEquivilentVotes(oaContest.red.votes, contestTab.votes))
            if (voteForN[contestId] == 1) require(redUndervotes == contestTab.undervotes) // TODO
        }

        return rcvrs
    }

    // the redacted Cvrs simulate the real CVRS that are in the pools, for testing and estimation
    // for a real OneAudit, only the pool averages are used. CLCA can only be used for testing, not for a real audit.
    // we could use CardPool instead of redacted, would be more resusable
    fun makeRedactedCvrs(cardPool: CardPool, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        cardPool.contestTabulations.forEach { (contestId, contestTab) ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            val underVotes = oaContest.undervotes[cardPool.poolName]!!
            contestVotes[contestId] = VotesAndUndervotes(contestTab.votes, underVotes, oaContest.info.voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, poolId = cardPool.poolId) // TODO test

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
                println("  contestId=${contestId}")
                println("  tabVotes=${tv}")
                println("  vunders= ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv))
            }
        }

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val cvrTab = tabulateCvrs(cvrs.iterator(), voteForN).toSortedMap()
        cvrTab.forEach { contestId, contestTab ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            if (show) {
                println("contestId=${contestId} group=${cardPool.poolName}")
                println("  redacted= ${contestTab.votes[contestId]}")
                println("  oaContest.undervotes= ${oaContest.undervotes[cardPool.poolName]} == ${contestTab.undervotes}")
                println("  contestTab=$contestTab")
                println()
            }
            require(checkEquivilentVotes(cardPool.contestTabulations[contestId]!!.votes, contestTab.votes))
        }

        return cvrs
    }

    fun makeContestsUA(): Pair<List<Contest>, List<RaireContestUnderAudit>> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val allTab = tabulateCvrs(allCvrs.iterator(), voteForN).toSortedMap()

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val contestTab = allTab[info.id]!!
            val useNc = oaContests[info.id]?.nballots() ?: contestTab.ncards
            val useNcast = contestTab.ncards

            val votesIn = contestTab.votes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            Contest(info, votesIn, useNc, useNcast)
        }

        if (!quiet) {
            println("Regular contests (No IRV)) = ${regContests.size}")
            regContests.forEach { contest ->
                println(contest.show2())
            }
        }
        return Pair(regContests, emptyList()) // no IRV contests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs
// Optionally only has pools (ie "batch level comparison audit") includeCvrs = false
fun createBoulderElectionOneAudit(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    includeCvrs: Boolean = true,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null)
{
    //  TODO clearDirectory(Path.of(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    println("readDominionCvrExport $cvrExportFile")
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOneAudit(export, sovo, includeCvrs = includeCvrs)

    val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(election.redactedCvrs.iterator())
    println("added ${election.redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")

    val (contests, irvContests) = election.makeContestsUA()

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles=true, riskLimit=riskLimit, sampleLimit=20000, minRecountMargin=minRecountMargin, nsimEst=10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val cards = createSortedCards(election.allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${election.allCvrs.size}")

    /////////////////
    // TODO attach (abstraction of) OneAuditContest ?
    val contestsUA = contests.map {
        OAContestUnderAudit(it, auditConfig.hasStyles)
    }
    addOAClcaAssorters(contestsUA, CvrIteratorAdapter(cards.iterator()), election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), show = false)
    checkVotesVsSovo(contests, sovo)

    writeContestsJsonFile(contestsUA + irvContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    println("took = $stopwatch\n")
}


// intermediate form before we make Contest
class OneAuditContest(val info: ContestInfo, val sovoContest: BoulderContestVotes,
                      val cvr: ContestTabulation, val red: ContestTabulation) {

    val undervotes = mutableMapOf<String, Int>() // group id -> undervote

    override fun toString() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
    }

    fun details() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
        // appendLine(" allTabulation=$all3")
        appendLine(" cvrTabulation=$cvr")
        appendLine(" redTabulation=$red")

        val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN
        val phantoms = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes
        appendLine("  sovoCards= $sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN")
        appendLine("  phantoms= $phantoms  = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes")

        val redUndervotes = sovoContest.totalUnderVotes - cvr.undervotes
        val redVotes = red.nvotes()
        val redNcards = (redVotes + redUndervotes) / info.voteForN
        val redUnderPct = 100.0 * redUndervotes / (redVotes + redUndervotes)
        appendLine("  redUndervotes= $redUndervotes  = sovoContest.totalUnderVotes - cvr.undervotes")
        appendLine("  redVotes= $redVotes = redacted.votes.map { it.value }.sum()")
        appendLine("  redNcards= $redNcards = (redVotes + redUndervotes) / info.voteForN")
        appendLine("  redUnderPct= 100.0 * redUndervotes / redNcards  = ${redUnderPct.toInt()}%")
    }

    fun problems() = buildString {
        appendLine(info)
        val nvotes = cvr.nvotes() + red.nvotes()
        val no = if (nvotes == sovoContest.totalVotes) "" else "***"
        appendLine("  nvotes= $nvotes, sovoContest.totalVotes = ${sovoContest.totalVotes} $no")

        val nballots = (nvotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
        val no2 = if (nballots == sovoContest.totalBallots) "" else "***"
        appendLine("  nballots= $nballots, sovoContest.totalBallots = ${sovoContest.totalBallots} $no2")

        val ncards = cvr.ncards + red.ncards
        val no3 = if (ncards == votesCast()) "" else "***"
        appendLine("  ncards= $ncards, votesCast() = ${votesCast()} $no3")
    }
    // on contest 20, totalVotes and totalBallots is wrong vs the cvrs. (only one where voteForN=3)
    // 'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
    //  nvotes= 17110, sovoContest.totalVotes = 16417
    //  nballots= 8485, sovoContest.totalBallots = 8254

    fun checkCvrs(contestTab: ContestTabulation) {
        sovoContest.candidateVotes.forEach { (sovoCandidate, sovoVote) ->
            val candidateId = info.candidateNames[sovoCandidate]
            val contestVote = contestTab.votes[candidateId] ?: 0
            if (contestVote != sovoVote) {
                println("*** ${info.name} '$sovoCandidate' $contestVote != $sovoVote")
            }
            require(contestVote == sovoVote)
        }
    }

    fun checkNcards(contestTab: ContestTabulation) {
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} - contestTab.ncards=${contestTab.ncards} = ${sovoContest.totalBallots - contestTab.ncards}")
        println("  ${info.id}: nballots=${nballots()} - contestTab.ncards=${contestTab.ncards} = ${nballots() - contestTab.ncards}")
        println()
    }

    fun showSummary(contestTab: ContestTabulation) {
        val ncvrs = cvr.ncards
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} ncvrs=${cvr.ncards}  ${(100.0 * ncvrs)/sovoContest.totalBallots} % ")
    }

    fun nballots(): Int {
        val nvotes = cvr.nvotes() + red.nvotes()
        val nballots = (nvotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
        return max(nballots, sovoContest.totalBallots)
    }

    fun votesCast(): Int {
        val nvotes = cvr.nvotes() + red.nvotes()
        val sovoCards = (nvotes + sovoContest.totalUnderVotes) / info.voteForN
        val votesCast = sovoCards + sovoContest.totalOverVotes
        return votesCast
    }

    // there are no overvotes in the Cvrs; we treat them as undervotes and add to redacted pools.
    fun redUndervotes() = sovoContest.totalUnderVotes + sovoContest.totalOverVotes - cvr.undervotes

    fun allocateUndervotes(groups: List<RedactedGroup>) {
        val totalCards = red.ncards
        var used = 0
        val redUndervotes = redUndervotes()

        groups.forEach { group ->
            val votes = group.contestVotes[info.id]
            if (votes != null) {
                // distribute undervotes as proportion of totalVotes
                val groupUndervotes = roundToClosest(redUndervotes * (votes.values.sum()/totalCards.toDouble()))
                used += groupUndervotes
                undervotes[group.ballotType] = groupUndervotes
            }
        }
        // adjust the last group so sum undervotes = redUndervotes
        if (redUndervotes - used != 0) {
            val adjustGroup = groups.first { undervotes[it.ballotType] != null && undervotes[it.ballotType]!! > (used - redUndervotes) }.ballotType
            val lastUnder = undervotes[adjustGroup]!!
            undervotes[adjustGroup] = lastUnder + (redUndervotes - used)
        }
        // check
        require (undervotes.values.sum() == redUndervotes)
    }
}