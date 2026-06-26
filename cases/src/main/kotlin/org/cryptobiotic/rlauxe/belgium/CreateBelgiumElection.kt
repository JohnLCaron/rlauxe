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
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path

private val logger = KotlinLogging.logger("BelgiumClca")

class BelgiumClca (
    val contestd: DHondtContest,
    val mvrSource: MvrSource,
): ElectionBuilder {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestWithAssertions>
    val allCvrs: List<Cvr>

    init {
        val contestUA = ContestWithAssertions(contestd, isClca=true, hasStyle=true).addAssertionsFromAssorters(contestd.assorters)
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

    override fun unsortedMvrsInternal() = mvrsToAuditableCardsListM(allCvrs, null)
    override fun unsortedMvrsExternal() = null

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsToCardStylesIterator(
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
    creationConfig: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    clear: Boolean = true): Result<AuditRoundIF, ErrorMessages>
{
    val stopwatch = Stopwatch()
    val election = BelgiumClca(contestd, MvrSource.testPrivateMvrs)

    createElectionRecord(election, topdir = topdir, clear = clear)
    println("createBelgiumElection took $stopwatch")

    val config = Config(election.electionInfo(), creationConfig, roundConfig)
    createAuditRecord(config, election, topdir = topdir) // , externalSortDir=topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"startFirstBoulderRound took $stopwatch" }

    return result
}

// create election, run all rounds
// return ntotalVotes from Json and finalRound.nmvrs
fun createAndRunAllRounds(electionName: String,
                          belgiumElectionJson: BelgiumElectionJson,
                          toptopdir: String,
                          contestId: Int,
                          runRounds:Boolean = true,
                          stopRound:Int=0,
                          showVerify:Boolean = false,
): Pair<Int, Int> {
    println("\n======================================================")
    println("electionName $electionName")

    val partyIds = readPartyTxtResource("$belgiumData/parties.txt")
    validateOutputDir(Path.of(toptopdir))
    copyResourceFile("$belgiumData/canonicalParties.txt", "$toptopdir/canonicalParties.txt")

    val dhondtParties = belgiumElectionJson.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, partyIds[it.PartyLabel]!!, it.NrOfVotes) }
    val nwinners = belgiumElectionJson.ElectionLists.sumOf { it.NrOfSeats }
    val totalVotes = belgiumElectionJson.NrOfValidVotes + belgiumElectionJson.NrOfBlankVotes // TODO undervotes = belgiumElection.NrOfBlankVotes

    val dcontest = makeDhondtContest(electionName, contestId, dhondtParties, nwinners, totalVotes, belgiumElectionJson.NrOfBlankVotes,.05)

    val topdir = "$toptopdir/$electionName"
    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 1),  // why only 1 ??
        ContestSampleControl.NONE,
        ClcaConfig(), null)

    createBelgiumElection(topdir=topdir, dcontest, creation, round)

    if (showVerify) {
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = showVerify)
        println()
        print(results)
        if (results.hasErrors) throw RuntimeException("createBelgiumElection failed to verify")
    }
    println()
    if (runRounds == false) return Pair(0, 0)

    var done = false
    var finalRound: AuditRoundIF? = null
    while (!done) {
        val lastRound = runRound(inputDir = topdir)
        if (lastRound != null) finalRound = lastRound
        done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
    }

    return if (finalRound != null) {
        println("$electionName: ${finalRound.show()}")
        Pair(totalVotes, finalRound.nmvrs)
    } else Pair(0, 0)
}

fun createAllBelgiumElections(toptopdir: String) {
    val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
    belgiumJsonInputResource.keys.forEachIndexed { idx, name ->
        val resourcePath = belgiumJsonInputResource[name]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumJsonFromResourcePath(resourcePath)
        val belgiumElectionJson = if (result.isOk) result.unwrap() else throw RuntimeException("$result")

        allmvrs[name] = createAndRunAllRounds(name, belgiumElectionJson, toptopdir,
                contestId = idx+1, runRounds=false)
    }
    println("============================================================")
    allmvrs.forEach {
        val pct = (100.0 * it.value.second) / it.value.first.toDouble()
        println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
    }
    // showAllBelgiumElection()
}

fun createAndRunOneBelgiumElection(electionName: String, toptopdir: String, contestId: Int): Pair<Int, Int> {
    val resourcePath = belgiumJsonInputResource[electionName]!!
    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumJsonFromResourcePath(resourcePath)
    val belgiumElectionJson = if (result.isOk) result.unwrap() else throw RuntimeException("$result")

    return createAndRunAllRounds(electionName, belgiumElectionJson, toptopdir,
        contestId = contestId, runRounds=false)
}

val belgiumData = "/resources/data/cases/belgium/belgium2024"
val belgiumJsonInputResource = mapOf(
    "Anvers" to "$belgiumData/2024_chambre-des-représentants_Circonscription d'Anvers.json",
    "Bruxelles" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Bruxelles-Capitale.json",
    "FlandreWest" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Flandre occidentale.json",
    "FlandreEast" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Flandre orientale.json",
    "Hainaut" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Hainaut.json",
    "Liège" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Liège.json",
    "Limbourg" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Limbourg.json",
    "Luxembourg" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Luxembourg.json",
    "Namur" to "$belgiumData/2024_chambre-des-représentants_Circonscription de Namur.json",
    "BrabantFlamant" to "$belgiumData/2024_chambre-des-représentants_Circonscription du Brabant flamand.json",
    "BrabantWallon" to "$belgiumData/2024_chambre-des-représentants_Circonscription du Brabant wallon.json",
)


