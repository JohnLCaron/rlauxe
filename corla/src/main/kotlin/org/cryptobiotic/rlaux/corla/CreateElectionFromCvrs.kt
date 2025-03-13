package org.cryptobiotic.rlaux.corla

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.cli.runChooseSamples
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.tabulateVotes
import org.cryptobiotic.rlauxe.workflow.*

class CreateElectionFromCvrs(val export: DominionCvrExport, val sovo: BoulderStatementOfVotes? = null) {
    val cvrs: List<Cvr> = export.cvrs.map { it.convert() }

    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns
        return export.schema.contests.map { contest ->
            val candidates = mutableListOf<String>()
            for (col in contest.startCol .. contest.startCol+contest.ncols-1) {
                val extra = if (columns[col].header.trim().isEmpty()) "" else " (${columns[col].header.trim()})"
                candidates.add(columns[col].choice + extra)
            }
            val candidateMap = candidates.mapIndexed { idx, it -> it to idx}.toMap()
            val choiceFunction = if (contest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (contest.isIRV) parseIrvContestName(contest.contestName) else parseContestName(contest.contestName)
            ContestInfo( name, contest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    fun countVotes() : List<ContestCount> { // contestId -> candidateId -> nvotes
        val cvrCount = mutableMapOf<Int, Int>() // use the count for Nc until we have something better
        val contestCount = mutableMapOf<Int, MutableMap<Int, Int>>()
        export.cvrs.forEach { cvr ->
            cvr.votes.forEach { contestVote ->
                cvrCount[contestVote.contestId] = cvrCount.getOrDefault(contestVote.contestId, 0) + 1
                val candidateCount = contestCount.getOrPut(contestVote.contestId) { mutableMapOf() }
                contestVote.votes.forEach { candidateCount[it] = candidateCount.getOrDefault(it, 0) + 1 }
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
        println("ncontests with info = ${infos.size}")

        val countVotes = countVotes()
        val allContests = countVotes.map { contestCount ->
            val info = infos.find { it.id == contestCount.contestId }!!
            val sovContest = if (sovo == null) null else sovo.contests.find { it.contestTitle == info.name }
            if (sovo != null && sovContest == null) {
                println("HEY cant find '${info.name}' in BoulderStatementOfVotes")
            }
            val Nc = if (sovContest == null) contestCount.Nc else sovContest.totalBallots
            // TODO undervotes
            Contest(info, contestCount.candidateCounts, Nc, 0)
        }
        // TODO no losers - leave in and mark "done? "
        val contests = allContests.filter { it.info.choiceFunction != SocialChoiceFunction.IRV && it.losers.size > 0 }
        println("ncontests with votes = ${contests.size}")
        contests.forEach { contest ->
            println(contest.show2())
        }
        val irvContests = allContests.filter { it.info.choiceFunction == SocialChoiceFunction.IRV }
        val raireContests = if (irvContests.isEmpty()) emptyList() else {
            irvContests.map { contest ->
                makeRaireContest(contest)
            }
        }
        println("ncontests with IRV = ${raireContests.size}")
        raireContests.forEach { contest ->
            println(contest.show2())
        }
        return Pair(contests, raireContests)
    }

    // partial duplicate from RaireContestTestData
    // TODO does not handle redacted
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
        println(" runElection: possibleWinners=${result.possibleWinners.contentToString()} eliminationOrder=${result.eliminationOrder.contentToString()}")

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
        rcontestUA.makeClcaAssertions(cvrs)

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
            val cvb2 = CvrBuilder2("redacted${redacted.ballotType}$idx", false)
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
    this.votes.forEach{
        cvrb.addContest(it.contestId, it.votes.toIntArray())
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
fun createElectionFromDominionCvrs(exportFile: String, auditDir: String, sovoFile: String? = null, riskLimit: Double = 0.03) {
    val stopwatch = Stopwatch()
    val export: DominionCvrExport = readDominionCvrExport(exportFile, "Boulder")

    val sovo = if (sovoFile == null) null else readBoulderStatementOfVotes(sovoFile)

    val electionFromCvrs = CreateElectionFromCvrs(export, sovo)
    val (contests, raireContests) = electionFromCvrs.makeContests()

    val publisher = Publisher(auditDir)
    val auditConfig = AuditConfig(
        AuditType.CLCA, hasStyles = true, riskLimit = riskLimit,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val rcvrs = electionFromCvrs.makeRedactedCvrs()
    val cvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(rcvrs)
    println("added ${rcvrs.size} redacted cvrs with ${cvrVotes.values.sumOf { it.values.sum() }} total votes")
    val allCvrs = electionFromCvrs.cvrs + rcvrs

    val clcaWorkflow = ClcaWorkflow(auditConfig, contests, raireContests, allCvrs)
    val cvrsUA = clcaWorkflow.sortedBallotsOrCvrs().map{ it as CvrUnderAudit }
    writeCvrsJsonFile(cvrsUA, publisher.cvrsFile())
    println("   writeCvrsJsonFile ${publisher.cvrsFile()}")

    // copy to mvrs, ie no errors
    val mvrFile = "$auditDir/private/testMvrs.json"
    val mvrus = allCvrs.mapIndexed { idx, org ->
        val cvr = cvrsUA[idx]
        CvrUnderAudit(org, cvr.index(), cvr.sampleNumber())
    }
    publisher.validateOutputDirOfFile(mvrFile)
    writeCvrsJsonFile(mvrus, mvrFile)
    println("   writeMvrsJsonFile ${mvrFile}")

    writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    // get the first round of samples wanted, write them to round1 subdir
    val auditRound = runChooseSamples(clcaWorkflow, publisher)

    // write the partial audit state to round1
    writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
    println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

    println("took = $stopwatch")
}