package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.convertCvrExportToCard
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CvrExport
import org.cryptobiotic.rlauxe.persist.csv.CvrExportAdapter
import org.cryptobiotic.rlauxe.persist.csv.CvrExportCsvHeader
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream

private val quiet = false

const val cvrExportCsvFile = "cvrExport.csv"
const val sortedCardsFile = "sortedCards.csv"

// read the CvrExport_* files out of the castVoteRecord JSON zip file, convert them to "CvrExport" CSV file.
// use the contestManifestFile to add the undervotes, and to identify the IRV contests
// write to "$topDir/$cvrExportCsvFile"
fun createCvrExportCsvFile(topDir: String, castVoteRecordZip: String, contestManifestFilename: String) {
    val stopwatch = Stopwatch()
    val outputFilename = "$topDir/$cvrExportCsvFile"
    val cvrExportCsvStream = FileOutputStream(outputFilename)
    cvrExportCsvStream.write(CvrExportCsvHeader.toByteArray())

    val irvIds = readContestManifestForIRVids(castVoteRecordZip, contestManifestFilename)
    val manifest = readBallotTypeContestManifestJsonFromZip(castVoteRecordZip, "BallotTypeContestManifest.json").unwrap()
    println("IRV contests = $irvIds")

    var countFiles = 0
    var countCards = 0
    val zipReader = ZipReaderTour(
        castVoteRecordZip, silent = true, sortPaths = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            countCards += convertCvrExportToCard(inputStream, cvrExportCsvStream, irvIds, manifest)
            countFiles++
        },
    )
    zipReader.tourFiles()
    cvrExportCsvStream.close()

    println("read $countCards cards in $countFiles files took $stopwatch")
    println("took = $stopwatch")
}

fun createSortedCards(topDir: String, auditDir: String, cvrCsvFilename: String, auditConfigIn: AuditConfig? = null, zip: Boolean = true) {
    SortMerge(auditDir, cvrCsvFilename, "$topDir/sortChunks", "$auditDir/$sortedCardsFile").run()
    if (zip) {
        createZipFile("$auditDir/$sortedCardsFile", delete = false)
    }
}

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
fun createSfElectionFromCsvExport(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrCsvFilename: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()

    val resultContestM: Result<ContestManifestJson, ErrorMessages> =  readContestManifestJsonFromZip(castVoteRecordZip, contestManifestFilename)
    val contestManifest = if (resultContestM is Ok) resultContestM.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from $castVoteRecordZip/$contestManifestFilename err = $resultContestM")
    if (show) println("contestManifest = $contestManifest")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJsonFromZip(castVoteRecordZip, candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest).sortedBy { it.id }
    if (show) contestInfos.forEach { println("   ${it} nwinners = ${it.nwinners} choiceFunction = ${it.choiceFunction}") }

    val regularVoteMap = makeContestVotesFromCsvExport(cvrCsvFilename)
    val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    val irvInfos = contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
    val irvContests = if (irvInfos.isEmpty()) emptyList() else {
        val cvrIter = CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename))
        val irvVoteMap = makeIrvContestVotes( irvInfos.associateBy { it.id }, cvrIter)
        if (show) {
            irvVoteMap.values.forEach { println("IrvVotes( ${it.irvContestInfo.id} ${it.irvContestInfo.choiceFunction} ${it.irvContestInfo}")
                it.notfound.forEach { (cand, count) -> println("  candidate $cand not found $count times")}
            }
        }
        makeRaireContests(irvInfos, irvVoteMap)
    }

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
    )
    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
    val allContests = contestsUA + irvContests

    // make all the clca assertions in one go
    val auditableContests = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    makeClcaAssertions(auditableContests, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)))

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)), show = true)

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

// sum all of the cards' votes
fun makeContestVotesFromCsvExport(cvrCsvFilename: String): Map<Int, ContestVotes> {
    val contestVotes = mutableMapOf<Int, ContestVotes>()

    var count = 0
    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)

    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        cvrExport.votes.forEach { (contestId, choiceIds) ->
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
            contests.add( Contest(info, contestVote.votes, contestVote.countBallots, 0)) // TODO phantoms
        }
    }
    return contests
}