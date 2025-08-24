package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.core.makeClcaAssertions
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

/* TODO

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
fun createSfElectionFromCsvExportOA(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrCsvFilename: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()
    val contestInfos = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)

    val publisher = Publisher(auditDir) // creates auditDir

    val ballotPools: Map<String, CardPool> = createBallotPools(
        auditDir,
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )
    val irvContests = makeOneAuditIrvContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }, ballotPools)

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 10,
    )

    val contests = makeOneAuditContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, ballotPools)
    val allContests = irvContests + contests

    // make all the clca assertions in one go
    val auditableContests = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    makeClcaAssertions(auditableContests, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)))

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contests)

    // TODO check that the sum of pooled and unpooled votes is correct
    /*  check that the votes agree with the summary XML
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")

    val votes = tabulateCvrs(CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)))
    votes.forEach { (id, ct) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        if (staxContest.ncards() != ct.ncards) {
            println("staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${ct.ncards} ")
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
    }

    //  check that the contests agree with the summary XML
    contests.forEach { contest ->
        val contestName = contestManifest.contests[contest.id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        val ncards = staxContest.ncards()
        if (ncards != contest.Nc) {
            println("staxContest $contestName ($contest.id) has ncards = ${staxContest.ncards()} not equal to contest Nc = ${contest.Nc} ")
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
    }

     */

    checkContestsWithCvrs(contests, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)), show = true)

    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

fun makeOneAuditContests(contestInfos: List<ContestInfo>, ballotPools: Map<String, CardPool>): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestInfos.map { info ->
        val cvrTabulation: ContestTabulation? = ballotPools[unpooled] ?.contestMap[info.id]
        if (cvrTabulation == null) {
            println("*** NO votes for contest ${info}")
        } else {
            val cardPools = ballotPools.keys.filter{ it != unpooled }.map {
                ballotPools[it]!!.ballotPoolForContest(info.id)
            }
            val cardPoolsNotNull = cardPools.filterNotNull()
            val pooledCast = cardPoolsNotNull.sumOf{ it.ncards }
            val contestOA = OneAuditContest.make(
                info,
                cvrVotes = cvrTabulation.votes,
                cvrNcards = cvrTabulation.ncards,
                cardPoolsNotNull,
                Nc = pooledCast + cvrTabulation.ncards, // TODO where do we get this??
                Ncast = pooledCast + cvrTabulation.ncards)
            println(contestOA)
            contestsUAs.add( OAContestUnderAudit(contestOA) )
        }
    }
    return contestsUAs
}

fun makeOneAuditIrvContests(contestInfos: List<ContestInfo>, ballotPools: Map<String, CardPool>): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestInfos.map { info ->
        val cvrTabulation: ContestTabulation? = ballotPools[unpooled] ?.contestMap[info.id]
        if (cvrTabulation == null) {
            println("*** NO votes for contest ${info}")
        } else {
            val cardPools = ballotPools.keys.filter{ it != unpooled }.map {
                ballotPools[it]!!.ballotPoolForContest(info.id)
            }
            val cardPoolsNotNull = cardPools.filterNotNull()
            val pooledCast = cardPoolsNotNull.sumOf{ it.ncards }
            val contestOA = OneAuditContest.make(
                info,
                cvrVotes = cvrTabulation.votes,
                cvrNcards = cvrTabulation.ncards,
                cardPoolsNotNull,
                Nc = pooledCast + cvrTabulation.ncards, // TODO where do we get this??
                Ncast = pooledCast + cvrTabulation.ncards)
            println(contestOA)

            val totalVoteConsolidator = VoteConsolidator()
            ballotPools.values.forEach { cardPool ->
                val vc = cardPool.irvMap[info.id]
                if (vc != null) {
                    totalVoteConsolidator.addVotes(vc)
                }
            }

            // fun makeRaireContestUA(info: ContestInfo, voteConsolidator: VoteConsolidator, Nc: Int, Ncast: Int, Nundervotes: Int): RaireContestUnderAudit {
            val rau : RaireContestUnderAudit = makeRaireContestUA(
                info,
                totalVoteConsolidator,
                contestOA.Nc(),
                contestOA.Nc())

            // class OneAuditIrvContest(
            //    contestOA: OneAuditContest,
            //    hasStyle: Boolean = true,
            //    val rassertions: List<RaireAssertion>,
            //)
            contestsUAs.add( OAIrvContestUA(contestOA,  true, rau.rassertions) )
        }
    }
    return contestsUAs
}

/* write ""$topdir/contests.json"", ""$topdir/auditConfig.json""
fun createSfElectionFromCsvExportOA2(
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

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJsonFromZip(castVoteRecordZip, candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest)
    println("contests = ${contestInfos.size}")

    val regularVoteMap = makeContestVotesFromCsvExport(cardFile) // contest -> ContestVotes
    // val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    val irvInfos = contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
    /* val irvContests = emptyList() // if (irvInfos.isEmpty()) emptyList() else {
        val cvrIter = CvrIteratorAdapter(readCardsCsvIterator(cardFile))
        val irvVoteMap = makeIrvContestVotes( irvInfos.associateBy { it.id }, cvrIter)
        if (show) {
            irvVoteMap.values.forEach { println("IrvVotes( ${it.irvContestInfo.id} ${it.irvContestInfo.choiceFunction} ${it.irvContestInfo}")
                it.notfound.forEach { (cand, count) -> println("  candidate $cand not found $count times")}
            }
        }
        // makeOneAuditIrvContests(irvInfos, irvVoteMap)
    } */

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 10,
    )

    // read in ballotPools
    val ballotPools: List<BallotPool> =  readBallotPoolCsvFile(ballotPoolFile)

    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestInfos.filter { onlyContests.isEmpty() || onlyContests.contains(it.id) }.forEach { info ->
        // class OneAuditContest (
        //    override val info: ContestInfo,
        //    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        //    val cvrNc: Int,
        //    val pools: Map<Int, BallotPool>, // pool id -> pool
        //)
        val contestVotes = regularVoteMap[info.id]
        if (contestVotes == null) {
            println("*** NO votes for contest ${info}")
        } else {
            val pools = ballotPools.filter { it.contest == info.id }
            val contestOA = OneAuditContest.make(info,
                cvrVotes = contestVotes.votes,
                cvrNc = contestVotes.countBallots,
                pools,
                Np = 0) // TODO what should Np be?
            println(contestOA)
            contestsUAs.add(OAContestUnderAudit(contestOA, auditConfig.hasStyles))
        }
    }

    /*
    val allContests = contestsUAs + irvContests

    // make all the clca assertions in one go
    // TODO: the card file only has the cvrs, not the non-cvrs....
    makeClcaAssertions(allContests, CvrIteratorAdapter(readCardsCsvIterator(cardFile)))

    // these checks may modify the contest status
    checkContestsCorrectlyFormed(auditConfig, allContests)
    checkContestsWithCards(allContests, readCardsCsvIterator(cardFile), show = true)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")
*/
    println("took = $stopwatch")
} */

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun createBallotPools(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
    ): Map<String, CardPool>
{

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    val cardPools: MutableMap<String, CardPool> = mutableMapOf<String, CardPool>()

    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPools.getOrPut(cvrExport.poolKey() ) { CardPool(cvrExport.poolKey(), cardPools.size + 1, contestManifest.irvContests) }
        pool.addPooledVotes(cvrExport)
    }

    // BallotPools
    val poolFilename = "$auditDir/ballotPools.csv"
    println(" writing to $poolFilename with ${cardPools.size} pools")
    val poutputStream = FileOutputStream(poolFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    // write the ballot pool file
    var poolCount = 0
    val sortedPools = cardPools.toSortedMap()
    sortedPools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools() // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
    }
    poutputStream.close()
    println(" total ${sortedPools.size} pools")

    /*  check that the cardPools agree with the summary XML
    val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")

    val contestCount = mutableMapOf<Int, ContestTabulation>()
    sortedPools.forEach { (_poolName, pool) ->
        pool.contestMap.forEach { id, poolCt ->
            val ct = contestCount.getOrPut(id) { ContestTabulation() }
            ct.addVotes( poolCt.votes)
            ct.ncards += poolCt.ncards
        }
    }

    contestCount.forEach { (id, ct) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        if (staxContest.ncards() != ct.ncards) {
            println("staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${ct.ncards} ")
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
    }
     */

    return cardPools
}

// maybe all you have to do is keep the cvrs and run the assorters over them when those are ready
class CardPool(val poolName: String, val poolId: Int, val irvIds: Set<Int>) {
    val contestMap = mutableMapOf<Int, ContestTabulation>()
    val irvMap = mutableMapOf<Int, VoteConsolidator>()
    val cvrs = mutableListOf<CvrExport>()  // have to run the assorter over these

    fun addPooledVotes(cvr : CvrExport) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (irvIds.contains(contestId)) {
                val irvContest = irvMap.getOrPut(contestId) { VoteConsolidator() }
                irvContest.addVote(candIds) // TODO not switching to index space !!
            } else {
                val contestTab = contestMap.getOrPut(contestId) { ContestTabulation() }
                contestTab.ncards++
                contestTab.addVotes(candIds)
            }
            cvrs.add(cvr)
        }
    }

    fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestMap.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
    }

    fun ballotPoolForContest(contestId: Int): BallotPool? {
        val cm = contestMap[contestId]
        return if (cm == null) null else BallotPool(poolName, poolId, contestId, cm.ncards, cm.votes)
    }
}


// class ContestTabulation {
//    val votes = mutableMapOf<Int, Int>()
//    var ncards = 0

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//// obsolete
// TODO use ContestTabulation in CheckAudits
data class ContestCount(var ncards: Int = 0, val counts: MutableMap<Int, Int> = mutableMapOf() ) {

    fun reportedMargin(winner: Int, loser: Int): Double {
        val winnerVotes = counts[winner] ?: 0
        val loserVotes = counts[loser] ?: 0
        return (winnerVotes - loserVotes) / ncards.toDouble()
    }

    override fun toString(): String {
        return "total=$ncards, counts=${counts.toSortedMap()}"
    }
}

 */