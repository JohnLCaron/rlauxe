package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportJson
import org.cryptobiotic.rlauxe.dominion.import
import org.cryptobiotic.rlauxe.dominion.readDominionCvrJsonStream
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import java.io.FileOutputStream

// read Dominion "cvr export" json file
// write "$topDir/cards.csv", "$topDir/ballotPools.csv"
fun createAuditableCardsWithPools(
    topDir: String,
    dominionCvrJson: String, // DominionCvrJson
    manifestFile: String) {

    val cardsOutputFilename = "$topDir/cards.csv"
    val cardsOutputStream = FileOutputStream(cardsOutputFilename)
    cardsOutputStream.write(AuditableCardHeader.toByteArray())

    val irvIds = readContestManifestForIRV(manifestFile)

    val countingContestsByGroup = mutableMapOf<Int, ContestCount>()
    val batches = mutableMapOf<String, BallotManifest>()
    val pools = mutableMapOf<String, CardPool>()

    var countFiles = 0
    var totalCards = 0
    var countCvrs1 = 0
    var countCvrs2 = 0
    val zipReader = ZipReaderTour(
        dominionCvrJson,
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

                var sessionCards = 0
                session.Original.Cards.forEach { card ->
                    card.Contests.forEach { contest ->
                        val contestCount = countingContestsByGroup.getOrPut(contest.Id) { ContestCount() }
                        contestCount.ncards++
                        val groupCount = contestCount.counts.getOrPut(session.CountingGroupId) { 0}
                        contestCount.counts[session.CountingGroupId] = groupCount + 1
                    }
                    totalCards++
                    sessionCards++
                }

                // data class AuditableCard (
                //    val desc: String, // info to find the card for a manual audit. Part of the info the Prover commits to before the audit.
                //    val index: Int,  // index into the original, canonical (committed-to) list of cards
                //    val sampleNum: Long,
                //    val phantom: Boolean,
                //    val contests: IntArray, // aka ballot style
                //    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, ranked first to last
                //    val poolId: Int?, // for OneAudit
                //)

                if (session.CountingGroupId == 2) {
                    val cvrs = session.import(irvIds)
                    cvrs.forEach {
                        val card = AuditableCard.fromCvrWithZeros(it)
                        cardsOutputStream.write(writeAuditableCardCsv(card).toByteArray()) // UTF-8
                        countCvrs2++
                    }
                    require(sessionCards == cvrs.size)
                } else {
                    val cvrs = session.import(irvIds)
                    val pool = pools.getOrPut(sessionKey) { CardPool(pools.size + 1) }
                    cvrs.forEach {
                        pool.addPooledVotes(it)
                        val card = AuditableCard.fromCvrWithPool(it, 0, 0, pool.poolId)
                        cardsOutputStream.write(writeAuditableCardCsv(card).toByteArray())
                        countCvrs1++
                    }
                    require(sessionCards == cvrs.size)
                }
            }
        },
    )
    zipReader.tourFiles()
    println(" createAuditableCards $countFiles files totalCards=$totalCards group1=$countCvrs1 + group2=$countCvrs2 = ${countCvrs1 + countCvrs2}")

    // for each tally_pool, ensure every CVR in that tally_pool has every contest mentioned in that pool TODO always?
    /* so have to wait until all Cvrs are read in before writing. TODO doesnt matter here
    pools.forEach { (poolName, pool) ->
        val allContests = pool.contestMap.keys.sorted()
        pool.cvrs.forEach {
            val card = AuditableCard.fromCvrWithPool(it, 0, 0, allContests, pool.poolId)
            cardsOutputStream.write(writeAuditableCardCsv(card).toByteArray())
        }
    } */
    cardsOutputStream.close()

    //     fun writePooledVotes() {
    //        val allContests = contestMap.keys.sorted()
    //        cvrs.forEach { cvr ->
    //            cvr.votes.forEach { (contestId, choiceIds) ->
    //                val contestCount = contestMap.getOrPut(contestId) { ContestCount() }
    //                contestCount.ncards++
    //                choiceIds.forEach { cand -> // TODO IRVs (is that even possible?)
    //                    val nvotes = contestCount.counts[cand] ?: 0
    //                    contestCount.counts[cand] = nvotes + 1
    //                }
    //            }
    //        }
    //    }

    println(" countingContestsByGroup")
    countingContestsByGroup.toSortedMap().forEach { (key, value) -> println("   $key $value") }

    // BallotPools
    val poutputFilename = "$topDir/ballotPools.csv"
    println(" writing to $poutputFilename with ${pools.size} pools")
    val poutputStream = FileOutputStream(poutputFilename)
    poutputStream.write(BallotPoolCsvHeader.toByteArray()) // UTF-8

    var poolCount = 0
    var pcount1 = 0
    var pcount2 = 0
    val spools = pools.toSortedMap()
    spools.forEach { (poolName, pool) ->
        val bpools = pool.toBallotPools(poolName) // one for each contest
        bpools.forEach { poutputStream.write(writeBallotPoolCSV(it).toByteArray()) }
        poolCount += bpools.size
        pcount1 += pool.contestMap[1]?.ncards ?: 0
        pcount2 += pool.contestMap[2]?.ncards ?: 0
    }
    poutputStream.close()
    println(" total ${spools.size} pools")
    println(" total contest1 cards in pools = $pcount1")
    println(" total contest2 cards in pools = $pcount2")
}

class CardPool(val poolId: Int) {
    val contestMap = mutableMapOf<Int, ContestCount>() // contestId -> cand/group -> count
    val cvrs = mutableListOf<Cvr>()

    fun addPooledVotes(cvr : Cvr) {
        cvrs.add(cvr)
        cvr.votes.forEach { (contestId, choiceIds) ->
            val contestCount = contestMap.getOrPut(contestId) { ContestCount() }
            contestCount.ncards++
            choiceIds.forEach { cand ->
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


//////////////////////////////////////////////////////////////////////////////////////////////////////

// write ""$topdir/contests.json"", ""$topdir/auditConfig.json""
fun createSfElectionFromCardsOA(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cardFile: String,
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

    val regularVoteMap = makeContestVotesFromCards(cardFile) // contest -> ContestVotes
    // val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    // No IRV contests are allowed

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
    )

    // read in ballotPools
    val ballotPools: List<BallotPool> =  readBallotPoolCsvFile(ballotPoolFile)

    val contestsUA = mutableListOf<ContestUnderAudit>()
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
            val pools = ballotPools.filter { it.contest == info.id }.associateBy { it.id }
            val contestOA = OneAuditContest(info, contestVotes.votes, contestVotes.countBallots, pools)
            println(contestOA)
            contestsUA.add(OAContestUnderAudit(contestOA, isComparison = true, auditConfig.hasStyles).makeClcaAssertions())
        }
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