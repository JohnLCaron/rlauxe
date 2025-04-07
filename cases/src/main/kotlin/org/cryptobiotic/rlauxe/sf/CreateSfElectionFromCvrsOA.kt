package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportJson
import org.cryptobiotic.rlauxe.dominion.import
import org.cryptobiotic.rlauxe.dominion.readDominionCvrJsonStream
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.persist.csv.*
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

    val countingContestsByGroup = mutableMapOf<Int, ContestCount>()
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
                        val contestCount = countingContestsByGroup.getOrPut(contest.Id) { ContestCount() }
                        contestCount.ncards++
                        val groupCount = contestCount.counts.getOrPut(session.CountingGroupId) { 0}
                        contestCount.counts[session.CountingGroupId] = groupCount + 1
                    }
                    totalCards++
                }

                if (session.CountingGroupId == 2) {
                    val cvrs = session.import(irvIds)
                    cvrs.forEach {
                        val cvrUA = CvrUnderAudit(it, 0, 0)
                        cvrsOutputStream.write(writeCvrCSV(cvrUA.publishCsv()).toByteArray()) // UTF-8
                        countCvrs2++
                    }
                } else {
                    val cvrs = session.import(irvIds)
                    val pool = pools.getOrPut(sessionKey) { Pool(pools.size + 1) }
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

    println(" countingContestsByGroup")
    countingContestsByGroup.toSortedMap().forEach { (key, value) -> println("   $key $value") }

    // BallotManifest: SHANGRLA want this
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
    val poutputFilename = "$topDir/ballotPools.csv"
    println(" writing to $poutputFilename with ${pools.size} pools")
    val poutputStream = FileOutputStream(poutputFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    var pcount1 = 0
    val spools = pools.toSortedMap()
    var startIdx = 1
    spools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools(poolName) // one for each contest
        startIdx += bpools.size
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        pcount1 += pool.contestMap[1]?.ncards ?: 0
    }
    poutputStream.close()
    println(" total contest1 cards in pools = $pcount1")
}

class Pool(val poolId: Int) {
    val contestMap = mutableMapOf<Int, ContestCount>() // contestId -> cand/group -> count
    val cvrs = mutableListOf<Cvr>()

    fun addPooledVotes(cvr : Cvr) {
        cvr.votes.forEach { (contestId, choiceIds) ->
            val contestCount = contestMap.getOrPut(contestId) { ContestCount() }
            contestCount.ncards++
            choiceIds.forEach { cand -> // TODO IRVs (is that even possible?)
                val nvotes = contestCount.counts[cand] ?: 0
                contestCount.counts[cand] = nvotes + 1
            }
        }
    }

    fun toBallotPools(poolName: String): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestMap.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.counts))
            }
        }
        return bpools
    }
}

class BallotManifest(val tab: Int, val batch: Int, var count: Int)

class ContestCount() {
    var ncards = 0
    val counts = mutableMapOf<Int, Int>() // groupCount or candCount

    fun reportedMargin(winner: Int, loser: Int): Double {
        val winnerVotes = counts[winner] ?: 0
        val loserVotes = counts[loser] ?: 0
        return (winnerVotes - loserVotes) / ncards.toDouble()
    }

    override fun toString(): String {
        return "total=$ncards, counts=${counts.toSortedMap()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestCount

        if (ncards != other.ncards) return false
        if (counts != other.counts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ncards
        result = 31 * result + counts.hashCode()
        return result
    }

}

////////////////////////////////////

fun createSfElectionFromCvrsOA(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cvrFile: String,
    ballotPoolFile: String,
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

    val regularVoteMap = makeRegularContestVotes(cvrFile) // contest -> ContestVotes
    // val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    // No IRV contests are allowed

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
    )

    // read in ballotPools
    val ballotPools: List<BallotPool> =  readBallotPoolCsvFile(ballotPoolFile)

    val contestsUA = contestInfos.filter { onlyContests.contains(it.id) }.map { info ->
        // class OneAuditContest (
        //    override val info: ContestInfo,
        //    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        //    val cvrNc: Int,
        //    val pools: Map<Int, BallotPool>, // pool id -> pool
        //)
        val contestVotes = regularVoteMap[info.id]!!
        val pools = ballotPools.filter{ it.contest == info.id }.associateBy { it.id }
        val contestOA = OneAuditContest(info, contestVotes.votes, contestVotes.countBallots, pools)
        OAContestUnderAudit(contestOA, isComparison=true, auditConfig.hasStyles).makeClcaAssertions()
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