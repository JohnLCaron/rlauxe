package org.cryptobiotic.rlaux.corla


import org.cryptobiotic.rlaux.util.ZipReader
import org.cryptobiotic.rlaux.util.nameCleanup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.nio.file.Path

fun createElectionFromAudit(
    auditDir: String,
    tabulateFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null
) {

    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()

    val tabulatedContests: Map<String, TabulateContestCsv> = readTabulateCsv(tabulateFile)
    val roundContests: List<ContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)

    val contests = makeContests(tabulatedContests, roundContests)
    //  val name: String,
    //    val id: Int,
    //    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    //    val choiceFunction: SocialChoiceFunction,
    //    val nwinners: Int = 1
    // contests.forEach { println(it) }
    println("contests = ${contests.size}")

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    /// cvrs
    val allCvrs = mutableListOf<Cvr>()

    val reader = ZipReader(precinctFile)
    val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
    val precincts: List<ColoradoPrecinctLevelResults> = readColoradoPrecinctLevelResults(input)
    println("precincts = ${precincts.size}")

    /*
    val precinctCvrs = makeCvrs(precincts[0], contests)
    allCvrs.addAll(precinctCvrs)
    val precinctCvrsUA = precinctCvrs.map{ CvrUnderAudit(it, 0, 0L)}
    val outputDir = "$auditDir/cvrs/${precincts[0].county}"
    validateOutputDir(Path.of(outputDir), ErrorMessages("precinctCvrsUA"))
    writeCvrsCsvFile(precinctCvrsUA, "$outputDir/${precincts[0].precinct}.csv")

     */

    var count = 0
    precincts.forEach { precinct ->
        val precinctCvrs = makeCvrs(precinct, contests)
        val outputDir = "$auditDir/cvrs/${precinct.county}"
        validateOutputDir(Path.of(outputDir), ErrorMessages("precinctCvrsUA"))
        val precinctCvrsUA = precinctCvrs.map{ CvrUnderAudit(it, 0, 0L)}
        writeCvrsCsvFile(precinctCvrsUA, "$outputDir/${precinct.precinct}.csv")
        //allCvrs.addAll(precinctCvrs)
        count += precinctCvrs.size
    }
    println("   total cvrs = $count")

    /*
    val ballotCards = MvrManagerClcaForStarting(allCvrs, auditConfig.seed)
    val clcaWorkflow = ClcaAudit(auditConfig, contests, emptyList(), ballotCards, allCvrs)
    writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

     */

    /*

    val ballotCards = MvrManagerClcaForStarting(allCvrs, auditConfig.seed)

    writeCvrsCsvFile(ballotCards.cvrsUA, publisher.cvrsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cvrsCsvFile()}")

    val mvrFile = "$auditDir/private/testMvrs.csv"
    publisher.validateOutputDirOfFile(mvrFile)
    writeCvrsCsvFile(ballotCards.cvrsUA, mvrFile) // no errors
    println("   writeCvrsCsvFile ${mvrFile}")

    val clcaWorkflow = ClcaAudit(auditConfig, contests, raireContests, ballotCards, allCvrs)
    writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    if (runEstimation) {
        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(clcaWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")
    }

     */

    println("took = $stopwatch")

    //    total cvrs = 3193034
    //took = 31.49 s
}

val quiet = false

fun makeContests(tabulatedContests: Map<String, TabulateContestCsv>, roundContests: List<ContestRoundCsv>): List<Contest> {
    val contests = roundContests.map { roundContest ->
        val tabContest = tabulatedContests[roundContest.contestName]!!
        val candidates = tabContest.choices.sortedBy { it.idx }
        val candidateNames = candidates.map { choice -> Pair(nameCleanup(choice.choiceName), choice.idx) }.toMap()
        val candidateVotes = candidates.map { choice -> Pair(choice.idx, choice.totalVotes) }.toMap()
        val info = ContestInfo(nameCleanup(tabContest.contestName), tabContest.idx, candidateNames, SocialChoiceFunction.PLURALITY, roundContest.nwinners)
        Contest( info, candidateVotes, roundContest.contestBallotCardCount, 0) // TODO or Nc = roundContest.ballotCardCount?
    }

    // "line number","contest name","choice name","party name","total votes","percent of votes", "registered voters","ballots cast","num Area total","num Area rptg","over votes","under votes"
    // 497,"San Juan County Court Judge - Edwards (Vote For 1)","Yes","Y",471,87.38, 734,562,1,0,"0","0"
    // 498,"San Juan County Court Judge - Edwards (Vote For 1)","No","N",68,12.62, 734,562,1,0,"0","0"
    val info = ContestInfo("San Juan County Court Judge Edwards", tabulatedContests.size,
        mapOf("Yes" to 0, "No" to 1), SocialChoiceFunction.PLURALITY, 1)
    val leftout = Contest( info, mapOf(0 to 471, 1 to 68), 562, 0)

    return contests + leftout
}

// each precinct has exactly one "ballot style", namely the one with all precinct.contestChoices on it.
fun makeCvrs(precinct: ColoradoPrecinctLevelResults, contests: List<Contest>): List<Cvr>{
    val contestsByName = contests.associateBy { it.info.name }

    // we are making the cvrs out of these votes.
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
    precinct.contestChoices.forEach { orgContestName: String, choices: List<ContestChoice> ->
        val votes = mutableMapOf<Int, Int>()
        val contestName = nameCleanup(orgContestName)
        var contest = contestsByName[contestName]
        if (contest == null) {
            contest = contestsByName[mutatisMutandi(contestName)]
        }
        if (contest == null) {
            println("*** Cant find contestName '$contestName' - skipping")
        } else {
            // ContestChoice(val choice: String, val totalVotes: Int)
            choices.forEach {
                val candName = nameCleanup(it.choice)
                var candidateId = contest.info.candidateNames[candName]
                if (candidateId == null) {
                    candidateId = contest.info.candidateNames[mutatisMutandi(candName)]
                }
                if (candidateId == null) {
                    println("*** Cant find candidateId '$candName' in contest '$contestName' - skipping")
                } else {
                    votes[candidateId] = it.totalVotes
                }
            }
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
        val cvb2 = CvrBuilder2("${precinct.precinct}$idx", false)
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
    require(rcvrs.size == maxVotes)
    // println(" made ${rcvrs.size} cvrs for precinct ${precinct.precinct}")
    return rcvrs
}

fun mutatisMutandi(choiceName: String): String {
    return when (choiceName) {
        "Randall Terry / Stephen E. Broden" -> "Randall Terry / Stephen E Broden"
        "Claudia De la Cruz / Karina García" -> "Claudia De la Cruz / Karina Garcia"
        "Colorado Supreme Court Justice Márquez" -> "Colorado Supreme Court Justice Marquez"
        "Colorado Court of Appeals Judge Román" -> "Colorado Court of Appeals Judge Roman"
        "Daniel Campaña" -> "Daniel Campana"
        "Yes/For" -> "Yes"
        "No/Against" -> "No"
        "Yes" -> "Yes/For"
        "No" -> "No/Against"
        //"Arapahoe County Court Judge - Hernandez" -> "Arapahoe County Court - Hernandez"
        //"Arapahoe County Court Judge - Williford" -> "Arapahoe County Court - Williford"
        //"Bent County Court Judge - Clark" -> "Bent County Court - Clark"
        //"Boulder County Court Judge - Martin" -> "Boulder County Court - Martin"
        else -> {
            if (choiceName.contains("Judge ")) choiceName.replace("Judge ", "")
            else {
                // println("HEY $choiceName")
                choiceName
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////
fun makeContests(electionDetail: ElectionDetailXml): List<Contest>{
    // data class ContestInfo(
    //    val name: String,
    //    val id: Int,
    //    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    //    val choiceFunction: SocialChoiceFunction,
    //    val nwinners: Int = 1,
    //    val minFraction: Double? = null, // supermajority only.
    val infos = electionDetail.contests.map {
        val candidateNames = it.choices.mapIndexed { idx, choice -> Pair(choice.text, idx) }.toMap()
        val candidateVotes = it.choices.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        val info = ContestInfo(it.text, it.key, candidateNames, SocialChoiceFunction.PLURALITY, it.voteFor)
        Contest( info, candidateVotes, 0, 0)
    }
    if (!quiet) println("ncontests with info = ${infos.size}")

    /*
    val countVotes = countVotes()
    val allContests = infos.map { info ->
        val contestCount = countVotes.find { it.contestId == info.id }!!
        val sovContest = sovo.contests.find {
            it.contestTitle == info.name
        }
        if (sovContest == null) {
            println("HEY cant find '${info.name}' in BoulderStatementOfVotes")
        }
        val Nc = if (sovContest == null) contestCount.Nc else sovContest.totalBallots
        // TODO undervotes
        Contest(info, contestCount.candidateCounts, Nc, 0)
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
    return Pair(contests, raireContests) */
    return emptyList()
}