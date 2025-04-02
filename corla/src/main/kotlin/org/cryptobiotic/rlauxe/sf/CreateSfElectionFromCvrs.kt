package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.convertCvrExportToCvr
import org.cryptobiotic.rlauxe.persist.csv.CvrCsv
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream

fun createSfElectionCvrs(topDir: String, castVoteRecordZip: String, manifestFile: String) {
    val stopwatch = Stopwatch()
    val outputFilename = "$topDir/cvrs.csv"
    val outputStream = FileOutputStream(outputFilename)
    outputStream.write(CvrCsv.header.toByteArray())

    val irvIds = readContestManifestForIRV(manifestFile)

    var countFiles = 0
    var countCvrs = 0
    val zipReader = ZipReaderTour(
        castVoteRecordZip, silent = true, sort = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            countCvrs += convertCvrExportToCvr(inputStream, outputStream, irvIds)
            countFiles++
        },
    )
    zipReader.tourFiles()
    outputStream.close()
    println("read $countCvrs cvrs $countFiles files took $stopwatch")
    // read 1,641,744 cvrs 27,554 files took 58.67 s
}

fun createSfElectionFromCvrs(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cvrFile: String,
    auditConfigIn: AuditConfig? = null
) {
    // clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()

    val resultContestM: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(contestManifestFile)
    val contestManifest = if (resultContestM is Ok) resultContestM.unwrap()
    else throw RuntimeException("Cannot read ContestManifestJson from ${contestManifestFile} err = $resultContestM")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJson(candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest)
    println("contests = ${contestInfos.size}")

    val regularVoteMap = makeRegularContestVotes(cvrFile)
    val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    val irvInfos = contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
    val irvContests = if (irvInfos.isEmpty()) emptyList() else {
        val cvrIter = CvrIteratorAdapter(readCvrsCsvIterator(cvrFile))
        val irvVoteMap = makeIrvContestVotes(irvInfos.associateBy { it.id }, cvrIter)
        makeIrvContests(irvInfos, irvVoteMap)
    }

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )

    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles).makeClcaAssertions() }
    val allContests = contestsUA + irvContests
    // these checks may modify the contest status
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

val quiet = false

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

// sum all of the cvrs
fun makeRegularContestVotes(cvrFile: String): Map<Int, ContestVotes> {
    val contestVotes = mutableMapOf<Int, ContestVotes>()

    var count = 0
    val cvrIter = readCvrsCsvIterator(cvrFile)

    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next().cvr
        cvr.votes.forEach { (contestId, choiceIds) ->
            val contestVote = contestVotes.getOrPut(contestId) { ContestVotes(contestId) }
            contestVote.countBallots++
            choiceIds.forEach {
                val nvotes = contestVote.votes[it] ?: 0
                contestVote.votes[it] = nvotes + 1
            }
            count++
            if (count % 10000 == 0) print("$count ")
            if (count % 100000 == 0) println()
        }
    }
    println(" read ${count} cvrs")
    return contestVotes
}

data class ContestVotes(val contestId: Int) {
    val votes = mutableMapOf<Int, Int>()
    var countBallots = 0
}

fun makeRegularContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, ContestVotes>): List<Contest> {
    val contests = mutableListOf<Contest>()
    contestInfos.forEach { info: ContestInfo ->
        val contestVote = contestVotes[info.id]
        if (contestVote == null) {
            println("*** Cant find contest '${info.id}' in contestVotes")
        } else {
            contests.add( Contest(info, contestVote.votes, contestVote.countBallots, 0))
        }
    }
    return contests
}