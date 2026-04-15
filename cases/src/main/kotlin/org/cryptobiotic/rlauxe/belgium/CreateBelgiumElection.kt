package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeDhondtContest
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("BelgiumClca")

class BelgiumClca (
    val contestd: DHondtContest,
    val mvrSource: MvrSource,
): ElectionBuilder {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestWithAssertions>
    val allCvrs: List<Cvr>

    init {
        val contestUA = ContestWithAssertions(contestd, isClca=true).addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        allCvrs = contestd.createSimulatedCvrs()
    }

    override fun electionInfo() = ElectionInfo(contestd.name, AuditType.CLCA, ncards(), contestsUA.size,
        cvrsContainUndervotes = true, mvrSource = mvrSource)
    override fun cardStyles() = infoMap.values.map { CardStyle(it.name, it.id, intArrayOf(it.id), true)} // dont really need I think
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = allCvrs.size

    override fun createUnsortedMvrsInternal() = mvrsToAuditableCardsList(allCvrs, null)
    override fun createUnsortedMvrsExternal() = null

    fun createCards(): CloseableIterator<CardWithBatchName> {
        return CvrsToCardsWithBatchNameIterator(
            AuditType.CLCA,
            Closer(allCvrs.iterator()),
            makePhantomCvrs(contestsUA().map { it.contest }),
            cardStyles(),
        )
    }
}

////////////////////////////////////////////////////////////////////
fun createBelgiumElection(
    topdir: String,
    contestd: DHondtContest,
    creation: AuditCreationConfig,
    round: AuditRoundConfig,
    clear: Boolean = true): Result<AuditRoundIF, ErrorMessages>
{
    val auditdir = "$topdir/audit"
    val stopwatch = Stopwatch()
    val election = BelgiumClca(contestd, MvrSource.testPrivateMvrs)

    createElectionRecord(election, auditDir = auditdir, clear = clear)
    println("createBelgiumElection took $stopwatch")

    val config = Config(election.electionInfo(), creation, round)
    createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"startFirstBoulderRound took $stopwatch" }

    return result
}

// create election, run all rounds
// return ntotalVotes from Json and finalRound.nmvrs
fun createAndRunBelgiumElection(electionName: String, filename: String, toptopdir: String, contestId: Int,
                          riskMeasuringSampleLimit: Int? = null,
                          stopRound:Int=0,
                          showVerify:Boolean = false): Pair<Int, Int> {
    println("======================================================")
    println("electionName $electionName")
    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
    val belgiumElection = if (result.isOk) result.unwrap()
        else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

    val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
    val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
    val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes // TODO undervotes = belgiumElection.NrOfBlankVotes

    //     name: String,
    //    id: Int,
    //    parties: List<DhondtCandidate>,
    //    nseats: Int,
    //    undervotes: Int,
    //    minFraction: Double,
    val contest = makeDhondtContest(electionName, contestId, dhondtParties, nwinners, totalVotes, belgiumElection.NrOfBlankVotes,.05)

    val topdir = "$toptopdir/$electionName"
    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, riskMeasuringSampleLimit=riskMeasuringSampleLimit)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 1),  // why only 1 ??
        ContestSampleControl.NONE,
        ClcaConfig(), null)

    createBelgiumElection(topdir=topdir, contest, creation, round)

    val auditdir = "$topdir/audit"
    val results = RunVerifyContests.runVerifyContests(auditdir, null, show = showVerify)
    println()
    print(results)
    if (results.hasErrors) throw RuntimeException("createBelgiumElection failed to verify")

    println("============================================================")
    var done = false
    var finalRound: AuditRoundIF? = null
    while (!done) {
        val lastRound = runRound(inputDir = auditdir)
        if (lastRound != null) finalRound = lastRound
        done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
    }

    return if (finalRound != null) {
        println("$electionName: ${finalRound.show()}")
        Pair(totalVotes, finalRound.nmvrs)
    } else Pair(0, 0)
}


