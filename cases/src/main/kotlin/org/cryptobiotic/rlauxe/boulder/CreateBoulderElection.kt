package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExport
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Path

class BoulderElectionFromCvrs(val export: DominionCvrExport, val sovo: BoulderStatementOfVotes, val quiet: Boolean = true) {
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

    fun makeContests(): Pair<List<Contest>, List<RaireContestUnderAudit>> {
        val infos = makeContestInfo().sortedBy{ it.id }
        if (!quiet) println("ncontests with info = ${infos.size}")

        val countVotes = countVotes()

        val regContests = infos.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val contestCount = countVotes[info.id]!!
            val sovContest = sovo.contests.find {
                it.contestTitle == info.name
            }
            if (sovContest == null) {
                println("*** cant find '${info.name}' in BoulderStatementOfVotes")
            } else {
                val diff = sovContest.totalBallots - contestCount.ncards
                println(" makeContest ${info.id} cvrCount = ${contestCount.ncards} sovContest.totalBallots = ${sovContest.totalBallots} undercount=$diff" )
                // TODO undervotes, phantoms to deal with diff?
            }
            // remove Write-Ins
            val votesIn = contestCount.votes.filter { info.candidateIds.contains(it.key) }
            Contest(info, votesIn, contestCount.ncards, 0)
        }

        if (!quiet) {
            println("Regular contests (No IRV)) = ${regContests.size}")
            regContests.forEach { contest ->
                println(contest.show2())
            }
        }
        // val irvContests = allContests.filter { it.info.choiceFunction == SocialChoiceFunction.IRV }
        val irvInfos = infos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
        val irvContests = if (irvInfos.isEmpty()) emptyList() else {
            val irvVoteMap = makeIrvContestVotes(irvInfos.associateBy { it.id }, cvrs.iterator())
            makeIrvContests(irvInfos, irvVoteMap)
        }

        if (!quiet) {
            println("contests with IRV = ${irvContests.size}")
            irvContests.forEach { contest ->
                println(contest.show2())
            }
        }
        return Pair(regContests, irvContests)
    }

    // make contest votes from the export.cvrs and export.redacted
    fun countVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation() }
                tab.ncards++
                tab.addVotes(contestVote.candVotes.toIntArray())
            }
        }
        export.redacted.forEach { redacted ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation() }
                // TODO how many cards depends if multiple votes are allowed. assume 1 vote = 1 card
                //   would be safer to make the cvrs first, then just use them to make the Contest
                //   problem is we cant distinguish phantoms from undervotes in redacted cvrs
                //   one could also try to use OneAudit for the redacted cvrs.
                //   but we still need to make an accurate CardLocationManifest
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

    fun makeRedactedCvrs(redacted: RedactedGroup, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes
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
    val info = contest.info()
    appendLine("$id '$name': choiceFunction=${choiceFunction} nwinners=${info.nwinners}, Nc=$Nc, Np=$Np, winners=${contest.winners()})")
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

////////////////////////////////////////////////////////////////////

// read in the sov file
fun createBoulderElection(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null) {

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    createBoulderElectionWithSov(cvrExportFile, auditDir, sovo, riskLimit, minRecountMargin, auditConfigIn)
}

// the sov is already read in
fun createBoulderElectionWithSov(
    cvrExportFile: String,
    auditDir: String,
    sovo: BoulderStatementOfVotes,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null,
) {
    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()
    val export: DominionCvrExport = readDominionCvrExport(cvrExportFile, "Boulder")
    val electionFromCvrs = BoulderElectionFromCvrs(export, sovo)

    // val cvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(electionFromCvrs.cvrs.iterator())
    // println("added ${electionFromCvrs.cvrs.size} cvrs with ${cvrVotes.values.sumOf { it.values.sum() }} total votes")

    val (contests, irvContests) = electionFromCvrs.makeContests()
    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, riskLimit = riskLimit, minRecountMargin = minRecountMargin,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val redactedCvrs = electionFromCvrs.makeRedactedCvrs()
    // val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotes(redactedCvrs.iterator())
    // println("added ${redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")
    val allCvrs = electionFromCvrs.cvrs + redactedCvrs

    /////////////////
    val contestsUA = contests.map {
        ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles)
    }
    makeClcaAssertions(contestsUA, allCvrs.iterator())
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val cards = createSortedCards(allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${allCvrs.size}")

    checkContestsWithCards(contestsUA, cards.iterator(), show = true)
    checkVotesVsSovo(contests, sovo)

    writeContestsJsonFile(contestsUA + irvContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    println("took = $stopwatch")
}

fun createSortedCards(cvrs: List<Cvr>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    return cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()) }.sortedBy { it.prn }
}

fun checkVotesVsSovo(contests: List<Contest>, sovo: BoulderStatementOfVotes) {
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
                // require(contestVote == sovoVote)
            }
        }
    }
}