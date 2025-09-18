package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.core.CvrExport.Companion.unpooled
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.CardPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssorters
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

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
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    val publisher = Publisher(auditDir) // creates auditDir

    val (contestNcs, contestInfos) = makeContestInfos(castVoteRecordZip, contestManifestFilename, candidateManifestFile)

    // pass 1 through cvrs, make card pools
    val cardPools: Map<Int, CardPool> = createCardPools(
        auditDir,
        contestInfos.associateBy { it.id },
        castVoteRecordZip,
        contestManifestFilename,
        cvrCsvFilename,
    )

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


    // make contests based on cardPools, which must include the unpooled
    val irvContests = makeOneAuditIrvContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }, cardPools, contestNcs)
    val contestsUA = makeOneAuditContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, cardPools, contestNcs)
    val allContests = irvContests + contestsUA

    // pass 2 through cvrs, create all the clca assertions in one go
    val auditableContests: List<OAContestUnderAudit> = allContests.filter { it.preAuditStatus == TestH0Status.InProgress }
    addOAClcaAssortersFromCvrExport(auditableContests, cvrExportCsvIterator(cvrCsvFilename), cardPools)

    // these checks may modify the contest status; dont call until clca assertions are created
    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrExportAdapter(cvrExportCsvIterator(cvrCsvFilename)), show = true)

    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

fun createCardPools(
    auditDir: String,
    contestInfos: Map<Int, ContestInfo>,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    cvrCsvFilename: String,
): Map<Int, CardPool>
{
    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    println("IRV contests = ${contestManifest.irvContests}")

    // make the card pools
    val cardPools: MutableMap<String, CardPool> = mutableMapOf()
    val cvrIter = cvrExportCsvIterator(cvrCsvFilename)
    while (cvrIter.hasNext()) {
        val cvrExport: CvrExport = cvrIter.next()
        val pool = cardPools.getOrPut(cvrExport.poolKey() ) {
            CardPool(cvrExport.poolKey(), cardPools.size + 1, contestManifest.irvContests, contestInfos)
        }
        pool.accumulateVotes(cvrExport.toCvr())
    }

    // write the ballot pool file. read back in createSortedCards to mark the pooled cvrs TODO needed?
    val poolFilename = "$auditDir/$ballotPoolsFile"
    println(" writing to $poolFilename with ${cardPools.size} pools")
    val poutputStream = FileOutputStream(poolFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    var poolCount = 0
    val sortedPools = cardPools.toSortedMap()
    sortedPools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools() // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
    }
    poutputStream.close()
    println(" total ${sortedPools.size} pools")

    ////  check that the cardPools agree with the summary XML
    val contestTabSums = mutableMapOf<Int, ContestTabulation>()
    sortedPools.forEach { (_, pool : CardPool) ->
        pool.sumRegular( contestTabSums)

        // TODO HEY
        pool.irvVoteConsolidations.forEach { contestId, irv ->
            val ct = contestTabSums.getOrPut(contestId) { ContestTabulation(contestInfos[contestId]?.voteForN) }
            ct.ncards += irv.ncards
        }
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

    return cardPools.values.associateBy { it.poolId }
}

// non-IRV. This assumes that the CVRS are in CardPool.
fun makeOneAuditContests(contestInfos: List<ContestInfo>, cardPools: Map<Int, CardPool>, contestNcs: Map<Int, Int>): List<OAContestUnderAudit> {
    val contestsUAs = mutableListOf<OAContestUnderAudit>()
    contestInfos.map { info ->
        // get a complete tabulation over all the pools
        val allPools = ContestTabulation(info.voteForN)
        cardPools.values.forEach { pool ->
            val poolTab = pool.contestTabulations[info.id]
            if (poolTab != null) {
                allPools.sum(poolTab)
            }
        }

        if (allPools.ncards > 0) {
            val contest = Contest(
                info,
                allPools.votes,
                contestNcs[info.id] ?: allPools.ncards,
                allPools.ncards)
            contestsUAs.add(OAContestUnderAudit(contest))
        }
    }
    return contestsUAs
}

// IRV
fun makeOneAuditIrvContests(contestInfos: List<ContestInfo>, cardPools: Map<Int, CardPool>, contestNcs: Map<Int, Int>): List<OAIrvContestUA> {
    val contestsUAs = mutableListOf<OAIrvContestUA>()
    contestInfos.map { info ->
        // get a complete tabulation over all the pools
        val allPools = VoteConsolidator()
        var ncards = 0
        cardPools.values.forEach { pool ->
            val poolVC = pool.irvVoteConsolidations[info.id]
            if (poolVC != null) {
                allPools.addVotes(poolVC.vc)
                ncards += poolVC.ncards
            }
        }

        // fun makeRaireContestUA(info: ContestInfo, voteConsolidator: VoteConsolidator, Nc: Int, Ncast: Int, Nundervotes: Int): RaireContestUnderAudit {
        val rau : RaireContestUnderAudit = makeRaireContestUA(
            info,
            allPools,
            contestNcs[info.id] ?: ncards,
            ncards,
        )
        contestsUAs.add( OAIrvContestUA(rau.contest as RaireContest,  true, rau.rassertions) )
    }
    return contestsUAs
}

fun addOAClcaAssortersFromCvrExport(
    oaContests: List<OAContestUnderAudit>,
    cardIter: Iterator<CvrExport>,
    cardPools: Map<Int, CardPool>
) {
    val poolsOnly = cardPools.filter { it.value.poolName != unpooled }
    val poolMap = poolsOnly.values.associate { it.poolName to it.poolId }
    val cvrIter = CvrExportAdapter(cardIter, poolMap)
    addOAClcaAssorters(oaContests, cvrIter, poolsOnly)
}
