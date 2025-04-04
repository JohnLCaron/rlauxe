package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportJson
import org.cryptobiotic.rlauxe.dominion.import
import org.cryptobiotic.rlauxe.dominion.readDominionCvrJsonStream
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.CvrCsv
import org.cryptobiotic.rlauxe.persist.csv.publishCsv
import org.cryptobiotic.rlauxe.persist.csv.writeCSV
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream

fun createSfElectionCvrsOA(topDir: String, castVoteRecordZip: String, manifestFile: String) {
    val cvrsOutputFilename = "$topDir/cvrs.csv"
    val cvrsOutputStream = FileOutputStream(cvrsOutputFilename)
    cvrsOutputStream.write(CvrCsv.header.toByteArray())

    val irvIds = readContestManifestForIRV(manifestFile)

    val countingContests = mutableMapOf<Int, ContestCount>()
    val batches = mutableMapOf<String, BallotManifest>()
    val pools = mutableMapOf<String, Pool>()

    var countFiles = 0
    var totalCards = 0
    var countCvrs1 = 0
    var countCvrs2 = 0
    val zipReader = ZipReaderTour(
        castVoteRecordZip,
        silent = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            val result: Result<DominionCvrExportJson, ErrorMessages> = readDominionCvrJsonStream(inputStream)
            val dominionCvrs = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read DominionCvrJson from stream err = $result")
            countFiles++
            dominionCvrs.Sessions.forEach { session ->
                val sessionKey = "${session.TabulatorId}-${session.BatchId}"
                val batch = batches.getOrPut(sessionKey) { BallotManifest(session.TabulatorId, session.BatchId, 0) }
                batch.count += session.Original.Cards.size

                session.Original.Cards.forEach { card ->
                    card.Contests.forEach { contest ->
                        val contestCount = countingContests.getOrPut(contest.Id) { ContestCount(0) }
                        contestCount.total++
                        val groupCount = contestCount.groupCount.getOrPut(session.CountingGroupId) { 0}
                        contestCount.groupCount[session.CountingGroupId] = groupCount + 1
                    }
                    totalCards++
                }

                if (session.CountingGroupId == 2) {
                    val cvrs = session.import(irvIds)
                    cvrs.forEach {
                        val cvrUA = CvrUnderAudit(it, 0, 0)
                        cvrsOutputStream.write(writeCSV(cvrUA.publishCsv()).toByteArray()) // UTF-8
                        countCvrs2++
                    }
                } else {
                    val cvrs = session.import(irvIds)
                    val pool = pools.getOrPut(sessionKey) { Pool() }
                    cvrs.forEach {
                        pool.addPooledVotes(it)
                        countCvrs1++
                    }
                }
            }
        },
    )
    zipReader.tourFiles()
    println(" createSfElectionCvrsOA $countFiles files totalCards=$totalCards group1=$countCvrs1 group2=$countCvrs2")
    cvrsOutputStream.close()

    println(" countingContests")
    countingContests.toSortedMap().forEach { (key, value) -> println("   $key $value") }

    // BallotManifest
    val header = "    ,Tray #,Tabulator Number,Batch Number,Total Ballots,VBMCart.Cart number\n"
    val outputFilename = "$topDir/ballotManifest.csv"
    println(" writing to $outputFilename with ${batches.size} batches")
    val outputStream = FileOutputStream(outputFilename)
    outputStream.write(header.toByteArray()) // UTF-8
    var total = 0
    var lineNo = 0
    val sbatches = batches.toSortedMap()
    sbatches.values.forEach {
        val line = "${lineNo},${lineNo+1},${it.tab},${it.batch},${it.count},${lineNo+1}\n"
        outputStream.write(line.toByteArray())
        lineNo++
        total += it.count
    }
    outputStream.close()
    println(" total ballotManifest = $total")

    // BallotPools
    var pcount = 0
    val pheader = "Pool, Contest, count, candidate:nvotes, ...\n"
    val poutputFilename = "$topDir/ballotPools.csv"
    println(" writing to $outputFilename with ${pools.size} pools")
    val poutputStream = FileOutputStream(poutputFilename)
    poutputStream.write(pheader.toByteArray()) // UTF-8
    val spools = pools.toSortedMap()
    spools.forEach { (poolName, pool) ->
        poutputStream.write(pool.contestLines(poolName).toByteArray())
        pcount += pool.count
    }
    poutputStream.close()
    println(" total cards in pools = $pcount")
}

class Pool() {
    var count = 0
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>()
    fun addPooledVotes(cvr : Cvr) {
        count++
        cvr.votes.forEach { (contestId, choiceIds) ->
            val contestVotes = votes.getOrPut(contestId) { mutableMapOf() }
            choiceIds.forEach { // TODO IRVs (is that even possible?)
                val nvotes = contestVotes[it] ?: 0
                contestVotes[it] = nvotes + 1
            }
        }
    }
    fun contestLines(poolName: String) = buildString {
        votes.forEach { contestId, candidateVotes ->
            if (!candidateVotes.isEmpty()) {
                append("${poolName}, ${contestId}, $count, ")
                candidateVotes.forEach { (candidateId, nvotes) -> append("$candidateId:$nvotes, ") }
                appendLine()
            }
        }
    }
}

class BallotManifest(val tab: Int, val batch: Int, var count: Int)
class ContestCount(var total: Int) {
    val groupCount = mutableMapOf<Int, Int>()
    override fun toString(): String {
        return "total=$total, groupCount=$groupCount"
    }
}

////////////////////////////////////

fun createSfElectionFromCvrsOA(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cvrFile: String,
    onlyContests: List<Int>,
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

    // No IRV contests are allowed

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
    )

    val contestsUA = contests.filter { onlyContests.contains(it.id) }.map {
        OAContestUnderAudit(it, isComparison=true, auditConfig.hasStyles).makeClcaAssertions()
    }
    // these checks may modify the contest status
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}