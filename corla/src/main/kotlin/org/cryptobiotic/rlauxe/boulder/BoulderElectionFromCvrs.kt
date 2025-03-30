package org.cryptobiotic.rlauxe.boulder

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.RedactedVotes
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExport
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Path

class BoulderElectionFromCvrs(val export: DominionCvrExport, val sovo: BoulderStatementOfVotes, val quiet: Boolean = true) {
    val cvrs: List<Cvr> = export.cvrs.map { it.convert() }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    if (columns[col].choice != "Write-in") {
                        candidateMap1[columns[col].choice] = candIdx
                    }
                    candIdx++
                }

            // TODO needed for IRV ??
                // TODO not sure if this is needed
                val candidates = mutableListOf<String>()
                for (col in exportContest.startCol .. exportContest.startCol+exportContest.ncols-1) {
                    candidates.add(columns[col].choice)
                }
                val pairs = mutableListOf<Pair<String, Int>>()
                repeat(exportContest.nchoices) { idx ->
                    pairs.add(Pair(candidates[idx], idx))
                }
                val candidateMap2 = pairs.toMap()


            // val candidateNames = if (!exportContest.isIRV) candidateMap1 else candidateMap2
            val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestName(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap1, choiceFunction, nwinners)
        }
    }

    // make contest votes from the export.cvrs and export.redacted
    fun countVotes() : List<ContestCount> { // contestId -> candidateId -> nvotes
        val cvrCount = mutableMapOf<Int, Int>() // use the count for Nc until we have something better
        val contestCount = mutableMapOf<Int, MutableMap<Int, Int>>()
        export.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote ->
                cvrCount[contestVote.contestId] = cvrCount.getOrDefault(contestVote.contestId, 0) + 1
                val candidateCount = contestCount.getOrPut(contestVote.contestId) { mutableMapOf() }
                contestVote.candVotes.forEach { candidateCount[it] = candidateCount.getOrDefault(it, 0) + 1 }
            }
        }
        export.redacted.forEach { redacted ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val candidateCount = contestCount.getOrPut(contestId) { mutableMapOf() }
                contestVote.entries.forEach { (candidateId, nvotes) ->
                    candidateCount[candidateId] = candidateCount.getOrDefault(candidateId, 0) + nvotes
                }
            }
        }
        return cvrCount.map { (contestId, cvrCount) ->
            ContestCount(contestId, cvrCount, contestCount[contestId]!!)
        }.sortedBy { it.contestId }
    }

    fun makeContests(): Pair<List<Contest>, List<RaireContestUnderAudit>> {
        val infos = makeContestInfo()
        if (!quiet) println("ncontests with info = ${infos.size}")

        val countVotes = countVotes()
        val allContests = infos.map { info ->
            val contestCount = countVotes.find { it.contestId == info.id }!!
            val sovContest = sovo.contests.find {
                it.contestTitle == info.name
            }
            if (sovContest == null) {
                println("*** cant find '${info.name}' in BoulderStatementOfVotes")
            }
            val Nc = if (sovContest == null) contestCount.Nc else sovContest.totalBallots
            // TODO undervotes, phantoms?
            // remove Write-Ins
            val votesIn = contestCount.candidateCounts.filter { info.candidateIds.contains(it.key) }
            Contest(info, votesIn, Nc, 0)
        }
        // TODO no losers - leave in and mark "done? "
        val contests = allContests.filter { it.info.choiceFunction != SocialChoiceFunction.IRV && it.losers.size > 0 }
        if (!quiet) {
            println("ncontests with votes = ${contests.size}")
            contests.forEach { contest ->
                println(contest.show2())
            }
        }
        val irvContests = allContests.filter { it.info.choiceFunction == SocialChoiceFunction.IRV }
        val raireContests = if (irvContests.isEmpty()) emptyList() else {
            irvContests.map { contest ->
                makeRaireContest(contest)
            }
        }
        if (!quiet) {
            println("ncontests with IRV = ${raireContests.size}")
            raireContests.forEach { contest ->
                println(contest.show2())
            }
        }
        return Pair(contests, raireContests)
    }

    // partial duplicate from RaireContestTestData
    fun makeRaireContest(contest: Contest): RaireContestUnderAudit {
        val vc = VoteConsolidator()
        cvrs.forEach {
            val votes = it.votes[contest.info.id]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val startingVotes = vc.makeVoteList()
        val cvotes = vc.makeVotes()
        val votes = Votes(cvotes, contest.ncandidates)

        // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
        val result: IRVResult = votes.runElection(TimeOut.never())
        if (!quiet) println(" runElection: possibleWinners=${result.possibleWinners.contentToString()} eliminationOrder=${result.eliminationOrder.contentToString()}")

        if (1 != result.possibleWinners.size) {
            throw RuntimeException("nwinners ${result.possibleWinners.size} must be 1")
        }
        val winner: Int = result.possibleWinners[0] // we need a winner in order to generate the assertions

        val problem = RaireProblem(
            mapOf("candidates" to contest.info.candidateNames.keys.toList()),
            cvotes,
            contest.ncandidates,
            winner,
            BallotComparisonOneOnDilutedMargin(cvrs.size),
            null,
            null,
            null,
        )
        val raireSolution: RaireSolution = problem.solve()
        if (raireSolution.solution.Err != null) {
            throw RuntimeException("solution.solution.Err=${raireSolution.solution.Err}")
        }
        requireNotNull(raireSolution.solution.Ok) // TODO
        val raireResult: RaireResult = raireSolution.solution.Ok

        val raireAssertions = raireResult.assertions.map { aand ->
            val votes = if (aand.assertion is NotEliminatedNext) {
                val nen = (aand.assertion as NotEliminatedNext)
                val voteSeq = VoteSequences.eliminate(startingVotes, nen.continuing.toList())
                val nenChoices = voteSeq.nenChoices(nen.winner, nen.loser)
                val margin = voteSeq.margin(nen.winner, nen.loser, nenChoices)
                // println("    nenChoices = $nenChoices margin=$margin\n")
                require(aand.margin == margin)
                nenChoices

            } else {
                val neb = (aand.assertion as NotEliminatedBefore)
                val voteSeq = VoteSequences(startingVotes)
                val nebChoices = voteSeq.nebChoices(neb.winner, neb.loser)
                val margin = voteSeq.margin(neb.winner, neb.loser, nebChoices)
                // println("    nebChoices = $nebChoices margin=$margin\n")
                require(aand.margin == margin)
                nebChoices
            }

            RaireAssertion.convertAssertion(contest.info.candidateIds, aand, votes)
        }

        val rcontestUA = RaireContestUnderAudit.makeFromInfo(
            contest.info,
            winner = raireResult.winner,
            Nc = contest.Nc,
            Np = contest.Np,
            raireAssertions,
        )
        rcontestUA.makeClcaAssertions()

        return rcontestUA
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
                if (remainingCandidates.isEmpty()) {
                    cvb2.addContest(contestId, IntArray(0)) // undervote I guess
                } else {
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
}


data class ContestCount(val contestId: Int, val Nc: Int, val candidateCounts: Map<Int, Int>)

fun ContestInfo.show() = buildString {
    appendLine("$name ($id) choiceFunction=${choiceFunction} nwinners=$nwinners")
    candidateNames.forEach { (name, id) -> appendLine("  $name -> $id") }
}

fun Contest.show2() = buildString {
    appendLine("$id '$name': choiceFunction=${choiceFunction} nwinners=${info.nwinners}, Nc=$Nc, Np=$Np, winners=$winners)")
    info.candidateNames.forEach { (name, id) ->
        appendLine("  $id '$name': votes=${votes[id]}") }
}

fun RaireContestUnderAudit.show2() = buildString {
    val info = contest.info
    appendLine("$id '$name': choiceFunction=${choiceFunction} nwinners=${info.nwinners}, Nc=$Nc, Np=$Np, winners=${contest.winners})")
    info.candidateNames.forEach { (name, id) ->
        appendLine("  $id '$name'") }
}

fun CastVoteRecord.convert(): Cvr {
    val cvrb = CvrBuilder2(this.cvrNumber.toString(),  false)
    this.contestVotes.forEach{
        cvrb.addContest(it.contestId, it.candVotes.toIntArray())
    }
    return cvrb.build()
}

fun parseContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Vote For=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Vote For=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val name = tokens[0].trim()
    val ncand = tokens[1].substringBefore(")").toInt()
    return Pair(name, ncand)
}

// City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)
fun parseIrvContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Number of positions=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Number of positions=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val name = tokens[0].trim()
    val ncand = tokens[1].substringBefore(",").toInt()
    return Pair(name, ncand)
}

// use sov to define what contests are in the audit (?)
fun createElectionFromDominionCvrs(
    cvrExportFile: String,
    auditDir: String,
    sovoFile: String,
    riskLimit: Double = 0.03,
    auditConfigIn: AuditConfig? = null) {

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    createElectionFromDominionCvrs(cvrExportFile, auditDir, sovo, riskLimit, auditConfigIn)
}

// use sov to define what contests are in the audit
fun createElectionFromDominionCvrs(
    cvrExportFile: String,
    auditDir: String,
    sovo: BoulderStatementOfVotes,
    riskLimit: Double = 0.03,
    auditConfigIn: AuditConfig? = null,
) {
    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()
    val export: DominionCvrExport = readDominionCvrExport(cvrExportFile, "Boulder")
    val electionFromCvrs = BoulderElectionFromCvrs(export, sovo)

    // val cvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(electionFromCvrs.cvrs.iterator())
    // println("added ${electionFromCvrs.cvrs.size} cvrs with ${cvrVotes.values.sumOf { it.values.sum() }} total votes")

    val (contests, raireContests) = electionFromCvrs.makeContests()
    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, riskLimit = riskLimit,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val redactedCvrs = electionFromCvrs.makeRedactedCvrs()
    // val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(redactedCvrs.iterator())
    // println("added ${redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")
    val allCvrs = electionFromCvrs.cvrs + redactedCvrs

    /////////////////
    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles).makeClcaAssertions() }
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val cvrsUA = createSortedCvrs(allCvrs, auditConfig.seed)
    writeCvrsCsvFile(cvrsUA, publisher.cvrsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cvrsCsvFile()}")

    checkContestsWithCvrs(contestsUA, cvrsUA.iterator())
    checkCvrsVsSovo(contests, sovo)

    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    println("took = $stopwatch")
}

fun checkCvrsVsSovo(contests: List<Contest>, sovo: BoulderStatementOfVotes) {
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
                    print("why")
                }
                require(contestVote == sovoVote)
            }
        }
    }
}