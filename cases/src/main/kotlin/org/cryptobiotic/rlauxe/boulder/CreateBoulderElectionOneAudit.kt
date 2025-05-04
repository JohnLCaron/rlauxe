package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.RedactedVotes
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExport
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import java.nio.file.Path

// using sovo just to define the contests. TODO: tests there are some votes
class BoulderElectionOneAuditFromCvrs(val export: DominionCvrExport, val sovo: BoulderStatementOfVotes,
                                      val redactedOnly: Boolean = false, val quiet: Boolean = false) {
    val cvrs: List<Cvr> = export.cvrs.map { it.convert() }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    // if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    // }
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

    fun makeContests(): List<OneAuditContest> {
        val infos = makeContestInfo().sortedBy { it.id }
        if (!quiet) println("ncontests with info = ${infos.size}")

        val cvrCounts = cvrCounts()
        val countRedactedVotes = countRedactedVotes()
        val poolsByContest = makePoolsFromRedacted()

        // not using IRV contests
        val oaContests = infos.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val contestCvrCount = cvrCounts[info.id]!!
            val sovContest = sovo.contests.find {
                it.contestTitle == info.name
            }
            if (sovContest == null) {
                println("*** cant find '${info.name}' in BoulderStatementOfVotes")
            } else {
                val redactedCount = countRedactedVotes[info.id]!!
                val diff = sovContest.totalBallots - contestCvrCount.ncards - redactedCount.ncards
                println(" makeContest ${info.id} redactedCount = ${redactedCount.ncards} cvrCount = ${contestCvrCount.ncards} sovContest.totalBallots = ${sovContest.totalBallots} undervotes=$diff" )
                // TODO undervotes, phantoms to deal with diff?
            }

            // BallotPool is specific to a Contest

            // remove Write-Ins
            // val votesIn = contestCount.votes.filter { info.candidateIds.contains(it.key) }


            val pools = poolsByContest[info.id]!!
            val poolCount = pools.sumOf { it.ncards }
            val redactedCount = countRedactedVotes[info.id]!!
            require(poolCount == redactedCount.ncards )

            //         fun make(info: ContestInfo,
            //                          cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
            //                          cvrNc: Int,                // the diff from cvrVotes tells you the undervotes
            //                          pools: Map<Int, BallotPool>, // pool id -> pool
            //                          Np: Int): OneAuditContest
            OneAuditContest.make(info, contestCvrCount.votes, contestCvrCount.ncards, pools ?: emptyList(), 0)
        }

        if (!quiet) {
            println("\nRegular contests (No IRV)) = ${oaContests.size}")
            oaContests.forEach { contest ->
                println(contest)
            }
        }

        return oaContests
    }

    // make contest votes from the export.cvrs
    fun cvrCounts() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        if (!redactedOnly) {
            export.cvrs.forEach { cvr ->
                cvr.contestVotes.forEach { contestVote ->
                    val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation() }
                    tab.ncards++
                    tab.addVotes(contestVote.candVotes.toIntArray())
                }
            }
        }

        return votes
    }

    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.redacted.forEach { redacted ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation() }
                tab.ncards += contestVote.map { it.value }.sum()
                contestVote.forEach { cand, nvotes -> tab.addVote(cand, nvotes) }
            }
        }

        return votes
    }

    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        export.redacted.forEach { redacted ->
            rcvrs.addAll(makeRedactedCvrs(redacted, show))
        }
        return rcvrs
    }

    fun makeRedactedCvrs(redacted: RedactedVotes, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()

        if (show) {
            println("ballotStyle = ${redacted.ballotType}")
            redacted.contestVotes.forEach { (key, votes) ->
                println("  contest $key = ${votes.values.sum()}")
            }
            val expectedCvrs = redacted.contestVotes.values.map { it.values.sum() }.max()
            println("expectedCvrs= $expectedCvrs\n")
        }

        // clumsy way to make a copy
        val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
        redacted.contestVotes.entries.forEach { (key, value) ->
            val copyCandMap = mutableMapOf<Int, Int>()
            value.entries.forEach { (key, value) -> copyCandMap[key] = value }
            contestVotes[key] = copyCandMap
        }

        // make cvrs until we exhaust the votes
        var idx = 0
        var usedOne = true
        while (usedOne) {
            usedOne = false
            val cvb2 = CvrBuilder2("redacted$idx", false)
            contestVotes.entries.forEach { (contestId, candidateCount) ->
                val remainingCandidates = candidateCount.filter{ (_, value) -> value > 0 }
                if (!remainingCandidates.isEmpty()) {
                    // cvb2.addContest(contestId, IntArray(0)) // undervote I guess
                    // } else {
                    usedOne = true
                    // pick a random candidate
                    val useCandidate = remainingCandidates.keys.toList().random()
                    // add it to cvr
                    cvb2.addContest(contestId, listOf(useCandidate).toIntArray())
                    // remove from redacted
                    val decrValue = candidateCount[useCandidate]!! - 1
                    if (decrValue == 0) {
                        candidateCount.remove(useCandidate)
                    } else {
                        candidateCount[useCandidate] = decrValue
                    }
                }
            }
            val rcvr = cvb2.build()
            rcvrs.add(rcvr)
            // println(rcvr)
            idx++

            if (show && (idx % 100 == 0)) {
                contestVotes.forEach { (key, votes) ->
                    println("  contest $key = ${votes.values.sum()}")
                }
                val expectedCvrs = contestVotes.values.map { it.values.sum() }.max()
                println("expectedCvrs left to do = $expectedCvrs\n")
            }
        }
        return rcvrs
    }

    // each RedactedVotes is a pool with multiple contests in it.
    // convert to a Map<ContestId, List<BallotPool>>
    fun makePoolsFromRedacted(): Map<Int, List<BallotPool>> {
        val result = mutableMapOf<Int, MutableList<BallotPool>>()
        val infos: Map<Int, ContestInfo> = makeContestInfo().associateBy { it.id }

        export.redacted.forEachIndexed { redactedCount, redacted ->
            // contestId -> candidateId -> nvotes
            redacted.contestVotes.forEach { (contestId, votes) ->
                val info = infos[contestId]
                if (info == null) {
                    println("Dont have contestId $contestId")
                } else {
                    val contestPools = result.getOrPut(contestId) { mutableListOf<BallotPool>() }
                    votes.forEach { cand, vote ->
                        if (!info.candidateIds.contains(cand))
                            println("Contest ${contestId} missing candidate $cand vote=$vote")
                    }
                    // data class BallotPool(
                    //    val name: String,
                    //    val id: Int,
                    //    val contest:Int,
                    //    val ncards: Int,          // ncards for this contest in this pool
                    //    val votes: Map<Int, Int>, // candid -> nvotes // the diff from ncards tell you the undervotes
                    val ncards = votes.values.sum()
                    contestPools.add(BallotPool("pool$redactedCount", redactedCount, contestId, ncards, votes))
                }
            }
        }
        return result
    }

}



////////////////////////////////////////////////////////////////////
// Create a OneAudit that only has pools (ie "batch level comparison audit")
// Make pools from the redacted cvrs.
fun createBoulderElectionOneAudit(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null) {

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()
    val export: DominionCvrExport = readDominionCvrExport(cvrExportFile, "Boulder")
    val boulderElection = BoulderElectionOneAuditFromCvrs(export, sovo, redactedOnly = false)

    // val cvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(electionFromCvrs.cvrs.iterator())
    // println("added ${electionFromCvrs.cvrs.size} cvrs with ${cvrVotes.values.sumOf { it.values.sum() }} total votes")

    val contests: List<OneAuditContest> = boulderElection.makeContests()
    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, riskLimit = riskLimit, minRecountMargin = minRecountMargin,
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val redactedCvrs = boulderElection.makeRedactedCvrs() // make the pools ??
    // val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(redactedCvrs.iterator())
    // println("added ${redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")
    // val allCvrs = electionFromCvrs.cvrs + redactedCvrs

    /////////////////
    val contestsUA: List<OAContestUnderAudit> = contests.map { it.makeContestUnderAudit() }

    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val allCvrs = boulderElection.cvrs + redactedCvrs
    val cards = createSortedCards(allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${allCvrs.size}")

    // TODO add in the pool counts
    checkContestsWithCards(contestsUA, cards.iterator(), show = true)

    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    checkAssortAvgs(contestsUA, cards)

    println("took = $stopwatch")


    val contest57 = contestsUA.find { it.id == 57 }!!
    val cards57 = cards.filter { it.cvr().hasContest(57)}
    writeAuditableCardCsvFile(cards57, "${publisher.topdir}/sortedCards57.csv")
    println("   writeCvrs57 ${"${publisher.topdir}/sortedCards57.csv"} cvrs = ${cards57.size}")

    val cvrs57 = boulderElection.cvrs.filter { it.hasContest(57)}
    val redacted57 = redactedCvrs.filter { it.hasContest(57)}
    println("     cvrs57 ${cvrs57.size} redacted57 = ${redacted57.size}")

    println(contest57.contestOA)
    checkAssortAvg(contest57, cards57, show = true)
    checkAssortAvg(contest57, cards, show = true)
}

fun checkAssortAvgs(contests: List<OAContestUnderAudit>, cards: List<AuditableCard>) {
    // we are making the contest votes from the cvrs. how does it compare with official tally ??
    contests.forEach { contestUA ->
        if (contestUA.minAssertion() != null) {  // single candidates have no assertions
            checkAssortAvg(contestUA, cards)
        }
    }
}

// we are making the contest votes from the cvrs. how does it compare with official tally ??
fun checkAssortAvg(contestUA: OAContestUnderAudit, cards: List<AuditableCard>, check: Boolean = true, show: Boolean = false) {
    val clcaAssertion = contestUA.minAssertion() as ClcaAssertion
    val clcaAssorter = clcaAssertion.cassorter as OneAuditClcaAssorter
    if (show) println(clcaAssorter)

    val pAssorter = clcaAssorter.assorter()
    val oaAssorter = clcaAssorter.oaAssorter

    val mvrs = cards.map { it.cvr() }
    val passortAvg = margin2mean(pAssorter.calcAssorterMargin(contestUA.id, mvrs, show = false))
    val oassortAvg = margin2mean(oaAssorter.calcAssorterMargin(contestUA.id, mvrs, show = false))

    if (show) {
        println("     pAssorter reportedMargin=${pAssorter.reportedMargin()} reportedAvg=${pAssorter.reportedMean()} assortAvg = $passortAvg")
        println("     oaAssorter reportedMargin=${oaAssorter.reportedMargin()} reportedAvg=${oaAssorter.reportedMean()} assortAvg = $oassortAvg")
    }

    if (check) {
        // the oaAssorter and pAssortAvg give same assortvbg, which I guess is the point of OneAudit.
        require(doubleIsClose(pAssorter.reportedMean(), passortAvg))
        require(doubleIsClose(oaAssorter.reportedMean(), oassortAvg))
        require(doubleIsClose(oassortAvg, passortAvg))
    }
}