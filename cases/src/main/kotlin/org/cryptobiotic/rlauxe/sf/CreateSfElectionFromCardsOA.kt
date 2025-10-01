package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
fun createSfElectionFromCvrExportOA(
    auditDir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrCsvFilename: String,
    auditConfigIn: AuditConfig? = null,
    show: Boolean = false
) {
    val stopwatch = Stopwatch()
    clearDirectory(Path(auditDir))

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 50,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = false)
    )
    val publisher = Publisher(auditDir) // creates auditDir

    val (contestNcs, contestInfos) = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)

    // pass 1 through cvrs, make card pools
    val (cardPools: Map<Int, CardPoolFromCvrs>, contestTabSums) = createCardPools(
        auditDir,
        contestInfos.associateBy { it.id },
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )

    // write ballot pools
    val ballotPools = cardPools.values.map { it.toBallotPools() }.flatten()
    writeBallotPoolCsvFile(ballotPools, publisher.ballotPoolsFile())
    logger.info{" total ${ballotPools.size} pools to ${publisher.ballotPoolsFile()}"}

    // make contests based on cardPool tabulations
    val unpooled = cardPools.values.find { it.poolName == unpooled }!!
    val allContests =  makeAllOneAuditContests(contestTabSums, contestNcs, unpooled).sortedBy { it.id }

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    addOAClcaAssortersFromCvrExport(auditableContests, cvrExportCsvIterator(cvrCsvFilename), cardPools)

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, allContests)
    // leave out ballot pools since the cvrs havethe votes in them
    val state = checkContestsWithCvrs(allContests, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)), ballotPools=emptyList(), show = true)
    logger.info{state}

    writeContestsJsonFile(allContests, publisher.contestsFile())
    logger.info{"   writeContestsJsonFile ${publisher.contestsFile()}"}
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    logger.info{"   writeAuditConfigJsonFile ${publisher.auditConfigFile()}"}

    logger.info{"took = $stopwatch"}
}

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

fun createCardPools(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
): Pair<Map<Int, CardPoolFromCvrs>, Map<Int, ContestTabulation>> {

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    // make the card pools
    var count = 0
    val cardPools: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPools.getOrPut(cvrExport.poolKey() ) {
            CardPoolFromCvrs(cvrExport.poolKey(), cardPools.size + 1, contestInfos)
        }
        pool.accumulateVotes(cvrExport.toCvr())
        count++
    }
    println("$count cvrs")

    // write the ballot pool file. read back in createSortedCards to mark the pooled cvrs TODO needed?
    val poolFilename = "$auditDir/$ballotPoolsFile"
    logger.info{" writing to $poolFilename with ${cardPools.size} pools"}
    val poutputStream = FileOutputStream(poolFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    //// because the unpooled is in a pool, the sum of pools are all the votes
    val sortedPools = cardPools.toSortedMap()
    val contestTabSums = mutableMapOf<Int, ContestTabulation>()
    sortedPools.forEach { (_, pool : CardPoolFromCvrs) ->
        pool.sum( contestTabSums)
    }

    val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
    println("staxContests")
    contestTabSums.toSortedMap().forEach { (id, ct) ->
        val contestName = contestManifest.contests[id]!!.Description
        val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
        if (staxContest.ncards() != ct.ncards) {
            logger.warn{"staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${ct.ncards} "}
            // assertEquals(staxContest.blanks(), contest.blanks)
        }
        println("  $contestName ($id) has ncards stax = ${staxContest.ncards()}, ct.ncards = ${ct.ncards}")
    }

    return Pair(cardPools.values.associateBy { it.poolId }, contestTabSums)
}

fun makeAllOneAuditContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, unpooled: CardPoolFromCvrs): List<OAContestUnderAudit> {
    val contestsUAs = mutableListOf<OAContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info
        val unpooledTab: ContestTabulation = unpooled.contestTabs[contestId]!!

        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0) {
            val contestOA: OAContestUnderAudit = if (!contestSumTab.isIrv) {
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                OAContestUnderAudit(contest)
            } else {
                val rau : RaireContestUnderAudit = makeRaireContestUA(contestSumTab.info, contestSumTab, useNc)
                OAIrvContestUA(rau.contest as RaireContest,  true, rau.rassertions)
            }
            // annotate with the pool %
            val unpooledPct = 100.0 * unpooledTab.ncards / contestSumTab.ncards
            val poolPct = (100 - unpooledPct).toInt()
            contestOA.contest.info().metadata["PoolPct"] = poolPct
            contestsUAs.add(contestOA)
        }
    }
    return contestsUAs
}

fun addOAClcaAssortersFromCvrExport(
    oaContests: List<OAContestUnderAudit>,
    cardIter: Iterator<CvrExport>,
    cardPools: Map<Int, CardPoolFromCvrs>
) {
    val poolsOnly = cardPools.filter { it.value.poolName != unpooled }
    val poolMap = poolsOnly.values.associate { it.poolName to it.poolId }
    val cvrIter = CvrExportAdapter(cardIter, poolMap)
    addOAClcaAssortersFromCvrs(oaContests, cvrIter, poolsOnly)
}
