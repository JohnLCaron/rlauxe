package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.DominionCvrSummary
import org.cryptobiotic.rlauxe.dominion.convertCvrExportJsonToCsv
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CvrExportAdapter
import org.cryptobiotic.rlauxe.persist.csv.CvrExportCsvHeader
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.poolNameToId
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.collections.component1
import kotlin.collections.component2

private val quiet = false

// TODO why isnt this in publisher ??
const val cvrExportCsvFile = "cvrExport.csv"
const val sortedCardsFile = "sortedCards.csv"
const val ballotPoolsFile = "ballotPools.csv"

private val logger = KotlinLogging.logger("SfElectionFromCards")

// read the CvrExport_* files out of the castVoteRecord JSON zip file, convert them to "CvrExport" CSV file.
// use the contestManifestFile to add the undervotes, and to identify the IRV contests
// write to "$topDir/$cvrExportCsvFile"
fun createCvrExportCsvFile(topDir: String, castVoteRecordZip: String, contestManifestFilename: String): DominionCvrSummary {
    val stopwatch = Stopwatch()
    val outputFilename = "$topDir/$cvrExportCsvFile"
    val cvrExportCsvStream = FileOutputStream(outputFilename)
    cvrExportCsvStream.write(CvrExportCsvHeader.toByteArray())

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    var countFiles = 0
    val summaryTotal = DominionCvrSummary()
    val zipReader = ZipReaderTour(
        castVoteRecordZip, silent = true, sortPaths = true,
        filter = { path -> path.toString().contains("CvrExport_") },
        visitor = { inputStream ->
            val summary = convertCvrExportJsonToCsv(inputStream, cvrExportCsvStream, contestManifest)
            summaryTotal.add(summary)
            countFiles++
        },
    )
    zipReader.tourFiles()
    cvrExportCsvStream.close()

    println("read ${summaryTotal.ncvrs} cvrs in $countFiles files; took $stopwatch")
    println("took = $stopwatch")
    return summaryTotal
}

// add phantoms here, but there arent any
fun createSortedCards(topDir: String, auditDir: String, cvrExportCsv: String, zip: Boolean = true, workingDir: String? = null, ballotPoolFile: String? = null) {
    val ballotPools = if (ballotPoolFile != null) readBallotPoolCsvFile(ballotPoolFile) else null
    val pools = ballotPools?.poolNameToId() // all we need is to know what the id is for each pool, so we can assign
    val working = workingDir ?: "$topDir/sortChunks"
    SortMerge(auditDir, cvrExportCsv, workingDir = working, "$auditDir/$sortedCardsFile", pools = pools).run()
    if (zip) {
        createZipFile("$auditDir/$sortedCardsFile", delete = false)
    }
}

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
fun createSfElectionFromCvrExport(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 50,
        clcaConfig = ClcaConfig(strategy=ClcaStrategyType.noerror),
    )
    val (contestNcs, contestInfos) = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)
    val infoMap = contestInfos.associateBy { it.id }

    val contestTabs = makeContestTabulations(cvrExportCsv, infoMap)
    val contests = makeRegularContests(contestInfos.filter { !it.isIrv }, contestTabs, contestNcs)

    val irvInfos = contestInfos.filter { it.isIrv }
    val irvContests = if (irvInfos.isEmpty()) emptyList() else {
        makeRaireContests(irvInfos, contestTabs, contestNcs)
    }

    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
    val allContests = (contestsUA + irvContests).filter { it.preAuditStatus == TestH0Status.InProgress && !auditConfig.skipContests.contains(it.id) }

    // make all the clca assertions in one go
    addClcaAssertions(allContests, CvrExportAdapter(cvrExportCsvIterator(cvrExportCsv)))

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrExportAdapter(cvrExportCsvIterator(cvrExportCsv)), show = true)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

fun makeContestInfos(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    show: Boolean = false
): Pair<Map<Int, Int>, List<ContestInfo>> {

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    if (show) println("contestManifest = $contestManifest")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJsonFromZip(castVoteRecordZip, candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest).sortedBy { it.id }
    if (show) contestInfos.forEach { println("   ${it} nwinners = ${it.nwinners} choiceFunction = ${it.choiceFunction}") }

    // The contest Ncs come from the contestManifest
    val contestNcs: Map<Int, Int> = makeContestNcs(contestManifest, contestInfos) // contestId -> Nc
    return Pair(contestNcs, contestInfos)
}

fun makeContestInfos(contestManifest: ContestManifest, candidateManifest: CandidateManifestJson): List<ContestInfo> {
    return contestManifest.contests.values.map { contestM: ContestMJson ->
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

fun makeContestNcs(contestManifest: ContestManifest, contestInfos: List<ContestInfo>): Map<Int, Int> { // contestId -> Nc
    val staxContests: List<StaxReader.StaxContest> = StaxReader().read("src/test/data/SF2024/summary.xml") // sketchy
    val contestNcs= mutableMapOf<Int, Int>()
    contestInfos.forEach { info ->
        val contestM = contestManifest.contests.values.find { it.Description == info.name }
        if (contestM != null) {
            val staxContest = staxContests.find { it.id == info.name }
            if (staxContest != null) contestNcs[info.id] = staxContest.ncards()!!
            else println("*** cant find contest '${info.name}' in summary.xml")

        } else println("*** cant find contest '${info.name}' in ContestManifest")
    }
    return contestNcs
}

fun makeContestTabulations(cvrExportCsv: String, infoMap: Map<Int, ContestInfo>): Map<Int, ContestTabulation> {
    val contestTabs = mutableMapOf<Int, ContestTabulation>()
    val cvrIter = cvrExportCsvIterator(cvrExportCsv)

    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        cvrExport.votes.forEach { (contestId, cands) ->
            val contestTab = contestTabs.getOrPut(contestId) { ContestTabulation(infoMap[contestId]!!) }
            contestTab.addVotes(cands)
        }
    }
    return contestTabs
}

// sum all of the cards' votes
fun makeContestVotesFromCrvExport(cvrExportCsv: String): Map<Int, ContestVotes> {
    val contestVotes = mutableMapOf<Int, ContestVotes>()

    var count = 0
    val cvrIter = cvrExportCsvIterator(cvrExportCsv)

    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        cvrExport.votes.forEach { (contestId, choiceIds) ->
            val contestVote = contestVotes.getOrPut(contestId) { ContestVotes(contestId) }
            contestVote.countBallots++  // each cvr is a cast ballot
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

fun makeRegularContests(contestInfos: List<ContestInfo>, contestTabs: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): List<Contest> {
    val contests = mutableListOf<Contest>()
    contestInfos.forEach { info: ContestInfo ->
        val contestTab = contestTabs[info.id]
        if (contestTab == null) {
            logger.warn {"*** Cant find reg contest '${info.id}' in contestTabulations, presume no votes"}
        } else {
            // TODO another source of Nc ? Currently the ElectionSummary StaxContest agrees with the cvr list
            contests.add( Contest(info, contestTab.votes, contestNcs[info.id] ?: contestTab.ncards, contestTab.ncards))
        }
    }
    return contests
}

fun makeRaireContests(contestInfos: List<ContestInfo>, contestTabs: Map<Int, ContestTabulation>, contestNc: Map<Int, Int>): List<RaireContestUnderAudit> {
    val contests = mutableListOf<RaireContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val contestTab = contestTabs[info.id] // candidate indexes
        if (contestTab == null) {
            logger.warn {"*** Cant find irv contest '${info.id}' in contestTabulations"}
        } else {
            contests.add(makeRaireContestUA(info, contestTab, Nc = contestNc[info.id] ?: contestTab.ncards))
        }
    }
    return contests
}
