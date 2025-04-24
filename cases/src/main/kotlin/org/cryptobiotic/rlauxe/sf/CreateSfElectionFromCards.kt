package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.convertCvrExportToCard
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardHeader
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream

private val quiet = false

fun createAuditableCards(topDir: String, castVoteRecordZip: String, manifestFile: String) {
    val stopwatch = Stopwatch()
    val outputFilename = "$topDir/cards.csv"
    val cardsOutputStream = FileOutputStream(outputFilename)
    cardsOutputStream.write(AuditableCardHeader.toByteArray())

    val irvIds = readContestManifestForIRV(manifestFile)
    println("IRV contests = $irvIds")

    var countFiles = 0
    var countCards = 0
    val zipReader = ZipReaderTour(
        castVoteRecordZip, silent = true, sort = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            countCards += convertCvrExportToCard(inputStream, cardsOutputStream, irvIds)
            countFiles++
        },
    )
    zipReader.tourFiles()
    cardsOutputStream.close()
    println("read $countCards cards in $countFiles files took $stopwatch")
    println("took = $stopwatch")
}

fun createSfElectionFromCards(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cardFile: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()

    val resultContestM: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(contestManifestFile)
    val contestManifest = if (resultContestM is Ok) resultContestM.unwrap()
    else throw RuntimeException("Cannot read ContestManifestJson from ${contestManifestFile} err = $resultContestM")
    if (show) println("contestManifest = $contestManifest")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJson(candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest).sortedBy { it.id }
    if (show) contestInfos.forEach { println("   ${it} nwinners = ${it.nwinners} choiceFunction = ${it.choiceFunction}") }

    val regularVoteMap = makeContestVotesFromCards(cardFile)
    val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    val irvInfos = contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
    val irvContests = if (irvInfos.isEmpty()) emptyList() else {
        val cvrIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        val irvVoteMap = makeIrvContestVotes( irvInfos.associateBy { it.id }, cvrIter)
        if (show) {
            irvVoteMap.values.forEach { println("IrvVotes( ${it.irvContestInfo.id} ${it.irvContestInfo.choiceFunction} ${it.irvContestInfo}")
                it.notfound.forEach { (cand, count) -> println("  candidate $cand not found $count times")}
            }
        }
        makeIrvContests(irvInfos, irvVoteMap)
    }

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
    val allContests = contestsUA + irvContests

    // make all the clca assertions in one go
    val auditableContests = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    makeClcaAssertions(auditableContests, CvrIteratorAdapter(readCardsCsvIterator(cardFile)))

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCards(contestsUA, readCardsCsvIterator(cardFile), show = true)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

fun makeContestInfos(contestManifest: ContestManifestJson, candidateManifest: CandidateManifestJson): List<ContestInfo> {
    return contestManifest.List.map { contestM: ContestM ->
        val candidateNames =
            candidateManifest.List.filter { it.ContestId == contestM.Id }.associate { candidateM: CandidateM ->
                Pair(candidateM.Description, candidateM.Id)
            }
        ContestInfo(
            contestM.Description,
            contestM.Id,
            candidateNames,
            if (contestM.NumOfRanks == 0) SocialChoiceFunction.PLURALITY else SocialChoiceFunction.IRV,
            contestM.VoteFor
        )
    }
}

// sum all of the cards' votes, and the pools' votes
fun makeContestVotesFromCards(cardFile: String): Map<Int, ContestVotes> {
    val contestVotes = mutableMapOf<Int, ContestVotes>()

    var count = 0
    val cardIter = readCardsCsvIterator(cardFile)

    while (cardIter.hasNext()) {
        val card: AuditableCard = cardIter.next()
        val cvr = card.cvr()

        if (cvr.poolId == null) {
            cvr.votes.forEach { (contestId, choiceIds) ->
                val contestVote = contestVotes.getOrPut(contestId) { ContestVotes(contestId) }
                contestVote.countBallots++
                choiceIds.forEach {
                    val nvotes = contestVote.votes[it] ?: 0
                    contestVote.votes[it] = nvotes + 1
                }
            }
            count++
            if (count % 10000 == 0) print("$count ")
            if (count % 100000 == 0) println()
        }
    }
    return contestVotes
}

data class ContestVotes(val contestId: Int) {
    val votes = mutableMapOf<Int, Int>()
    var countBallots = 0

    override fun toString(): String {
        val nvotes = votes.values.sum()
        return "ContestVotes(contestId=$contestId, countBallots=$countBallots, votes=$votes, nvotes=$nvotes, underVotes=${countBallots-nvotes})"
    }
}

fun makeRegularContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, ContestVotes>): List<Contest> {
    val contests = mutableListOf<Contest>()
    contestInfos.forEach { info: ContestInfo ->
        val contestVote = contestVotes[info.id]
        if (contestVote == null) {
            println("*** Contest '${info}' has no contestVotes") // TODO why ?
        } else {
            contests.add( Contest(info, contestVote.votes, contestVote.countBallots, 0))
        }
    }
    return contests
}