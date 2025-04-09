package org.cryptobiotic.rlauxe.corla


import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path

private val showMissingCandidates = false

fun coloradoElectionFromAudit(
    auditDir: String,
    detailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null
) {
    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()

    // val tabulatedContests: Map<String, TabulateContestCsv> = readTabulateCsv(tabulateFile)
    val roundContests: List<ContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)

    val contests = makeContests(electionDetailXml, roundContests)
    println("contests = ${contests.size}")

    // auditConfig
    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .03,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    //// cards
    val reader = ZipReader(precinctFile)
    val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
    val precincts: List<ColoradoPrecinctLevelResults> = readColoradoPrecinctLevelResults(input)
    println("precincts = ${precincts.size}")

    var count = 0
    precincts.forEach { precinct ->
        val precinctCvrs = makeCvrs(precinct, contests)
        val outputDir = "$auditDir/cards/${precinct.county}"
        validateOutputDir(Path.of(outputDir), ErrorMessages("precinctCvrs"))
        val precinctCvrsUA = precinctCvrs.map{ AuditableCard.fromCvr(it, 0, 0L)}
        writeAuditableCardCsvFile(precinctCvrsUA, "$outputDir/${precinct.precinct}.csv")
        count += precinctCvrs.size
    }
    println("   total cards = $count")

    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles).makeClcaAssertions() }
    // these checks may modify the contest status
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val precinctReader = TreeReaderIterator(
        "$auditDir/cards/",
        fileFilter = { true },
        reader = { path -> readCardsCsvIterator(path.toString()) }
    )
    // val precinctReader = PrecinctReader("$auditDir/cards/")
    checkContestsWithCards(contestsUA, precinctReader)

    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    println("took = $stopwatch")
}

val quiet = false

fun makeContests(electionDetailXml: ElectionDetailXml, roundContests: List<ContestRoundCsv>): List<Contest> {
    val roundContestMap = roundContests.associateBy { contestNameCleanup(it.contestName) }
    val contests = mutableListOf<Contest>()

    electionDetailXml.contests.forEachIndexed { detailIdx, detailContest ->
        var contestName = contestNameCleanup(detailContest.text)
        var roundContest = roundContestMap[contestName]
        if (roundContest == null) {
            roundContest = roundContestMap[mutatisMutandi(contestName)]
            if (roundContest == null) {
                val mname = mutatisMutandi(contestName)
                println("*** Cant find ContestRoundCsv $mname")
            }
        }

        val candidates = detailContest.choices
        val candidateNames = candidates.mapIndexed { idx, choice -> Pair(candidateNameCleanup(choice.text), idx) }.toMap()
        val candidateVotes = candidates.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()

        // all we need ContestRoundCsv is for Nc; TODO or Nc = roundContest.ballotCardCount?
        val totalVotes = candidateVotes.map { it.value }.sum()
        var useNc = roundContest?.contestBallotCardCount ?: candidateVotes.map { it.value }.sum()
        if (useNc < totalVotes ) {
            println("*** Contest $contestName has $totalVotes total votes, but contestBallotCardCount is ${roundContest!!.contestBallotCardCount} - using ballotCardCount = ${roundContest!!.ballotCardCount}")
            useNc = roundContest!!.ballotCardCount
        } // buggers

        val info = ContestInfo(
            contestName,
            detailIdx,
            candidateNames,
            SocialChoiceFunction.PLURALITY,
            detailContest.voteFor
        )
        val contest = Contest(
            info,
            candidateVotes,
            useNc,
            0
        )
        contests.add(contest)
    }

    // TODO
    // "line number","contest name","choice name","party name","total votes","percent of votes", "registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
    // 497,"San Juan County Court Judge - Edwards (Vote For 1)","Yes","Y",471,87.38, 734,562,1,0,"0","0"
    // 498,"San Juan County Court Judge - Edwards (Vote For 1)","No","N",68,12.62, 734,562,1,0,"0","0"
    //val info = ContestInfo("San Juan County Court Judge Edwards", contests.size,
    //    mapOf("Yes" to 0, "No" to 1), SocialChoiceFunction.PLURALITY, 1)
    //val leftout = Contest( info, mapOf(0 to 471, 1 to 68), 562, 0)

    return contests
}

// each precinct has exactly one "ballot style", namely the one with all precinct.contestChoices on it.
fun makeCvrs(precinct: ColoradoPrecinctLevelResults, contests: List<Contest>): List<Cvr>{
    val contestsByName = contests.associateBy { it.info.name }

    // we are making the cvrs out of these votes.
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
    precinct.contestChoices.forEach { orgContestName: String, choices: List<ContestChoice> ->
        val votes = mutableMapOf<Int, Int>()
        val contestName = contestNameCleanup(orgContestName)
        var contest = contestsByName[contestName]
        if (contest == null) {
            contest = contestsByName[mutatisMutandi(contestName)]
        }
        if (contest == null) {
            println("*** Cant find contestName '$contestName' - skipping")
        } else {
            // ContestChoice(val choice: String, val totalVotes: Int)
            choices.forEach {
                val candName = candidateNameCleanup(it.choice)
                var candidateId = contest.info.candidateNames[candName]
                if (candidateId == null) {
                    candidateId = contest.info.candidateNames[mutatisMutandi(candName)]
                }
                if (candidateId == null) {
                    if (showMissingCandidates) println("*** Cant find candidateId '$candName' in contest '$contestName' - skipping")
                } else {
                    votes[candidateId] = it.totalVotes
                }
            }
            if (contest.id > 260)
                print("")
            contestVotes[contest.id] = votes
        }
    }

    // make cvrs until we exhaust the votes
    // Assume that the cvr has all of the contests on it, even if theres no vote in the contest
    val rcvrs = mutableListOf<Cvr>()
    var idx = 0
    var usedOne = true
    while (usedOne) {
        usedOne = false
        val cvb2 = CvrBuilder2("${precinct.precinct}-$idx", false)
        contestVotes.entries.forEach { (contestId, candidateCount) ->
            val remainingCandidates = candidateCount.filter { (_, value) -> value > 0 }
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
        if (usedOne) {
            rcvrs.add(rcvr)
            // println(rcvr)
            idx++
        }
    }

    // the number of cvrs should be the maximum votes across contests
    val maxVotes = precinct.contestChoices.map {
        it.value.map { it.totalVotes } .sum()
    }.max()
    // require(rcvrs.size == maxVotes)
    // println(" made ${rcvrs.size} cvrs for precinct ${precinct.precinct}")
    return rcvrs
}