package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DhondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtPartyBuilder
import org.cryptobiotic.rlauxe.dhondt.makeDhondtContest
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("BelgiumElection")

class BelgiumElection (
    val contestd: DhondtContest,
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

    override fun unsortedMvrsInternal() = mvrsToAuditableCardsList(allCvrs, null)
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
fun createBelgiumElectionForOneContest(
    topdir: String,
    contestd: DhondtContest,
    creationConfig: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
): Result<AuditRoundIF, ErrorMessages> {
    val stopwatch = Stopwatch()
    val election = BelgiumElection(contestd, MvrSource.testPrivateMvrs)

    createElectionRecord(election, topdir = topdir, clear = false)
    println("createBelgiumElection took $stopwatch")

    val config = Config(election.electionInfo(), creationConfig, roundConfig)
    createAuditRecord(config, election, topdir = topdir) // , externalSortDir=topdir)

    val result = startFirstRound(topdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"startFirstRound took $stopwatch" }

    return result
}

// create one contest and optionally run all rounds
// return ntotalVotes from Json and finalRound.nmvrs
fun createOneContestFromJson(electionName: String,
                             belgiumElectionJson: BelgiumElectionJson,
                             partyIds: Map<String, Int>,
                             toptopdir: String,
                             contestId: Int,
                             runRounds:Boolean = true,
                             stopRound:Int=0,
                             showVerify:Boolean = false,
                             clear: Boolean = true,
): Pair<Int, Int> {
    println("\n======================================================")
    println("electionName $electionName")

    val topdir = "$toptopdir/$electionName"
    if (clear) clearDirectory(Path(topdir))
    Logging.addFileAppender("cases", "$topdir/logs.log")
    logger.info {"-------------- createBelgiumElection ${electionName} in $topdir"}
    validateOutputDir(Path.of(toptopdir))

    val nwinners = belgiumElectionJson.ElectionLists.sumOf { it.NrOfSeats }
    val totalVotes = belgiumElectionJson.NrOfValidVotes + belgiumElectionJson.NrOfBlankVotes // TODO undervotes = belgiumElection.NrOfBlankVotes

    val dhondtParties = belgiumElectionJson.ElectionLists.map {
        DhondtPartyBuilder(
            it.PartyLabel,
            partyIds[it.PartyLabel]!!,
            it.NrOfVotes,
            it.Candidates.filter{ !it.IsSuccessor }.count(),
        )
    }

    val dcontest = makeDhondtContest(electionName, contestId, dhondtParties, nwinners, totalVotes, belgiumElectionJson.NrOfBlankVotes,.05)

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 1),  // why only 1 ??
        ContestSampleControl.NONE,
        ClcaConfig(), null)

    createBelgiumElectionForOneContest(topdir=topdir, dcontest, creation, round)

    if (showVerify) {
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = showVerify)
        println()
        print(results)
        if (results.hasErrors) throw RuntimeException("createBelgiumElection failed to verify")
    }
    println()

    var finalRound: AuditRoundIF? = null
    if (runRounds) {
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            if (lastRound != null) finalRound = lastRound
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
        }
    }

    Logging.removeFileAppender("cases")

    return if (finalRound != null) {
        println("$electionName: ${finalRound.show()}")
        Pair(totalVotes, finalRound.nmvrs)
    } else Pair(0, 0)
}

fun createOneContestFromInputs(year: Int, electionName: String, toptopdir: String, contestId: Int): Pair<Int, Int> {
    val inputs = belgiumInput(year)
    val resourcePath = inputs.contests[electionName]!!
    val partyIds = readPartyTxtResource(inputs.parties) // includes aliases

    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumJsonFromResourcePath(resourcePath)
    val belgiumElectionJson = if (result.isOk) result.unwrap() else throw RuntimeException("$result")

    return createOneContestFromJson(electionName, belgiumElectionJson, partyIds, toptopdir,
        contestId = contestId, runRounds=false)
}

fun makeAllBelgiumContests(year: Int, toptopdir: String) {
    val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
    val inputs = belgiumInput(year)

    // someone has to create the canonicalParties file
    validateOutputDir(Path.of(toptopdir))
    // this is used during creation
    val partyIds = readPartyTxtResource(inputs.parties) // includes aliases

    // this is used by the readers: party id -> canonical name map
    val outputFile = "$toptopdir/${CompositeAuditRecord.canonicalPartiesFilename}"
    val canonicalParties = writeCanonicalParties(year, partyIds, outputFile)

    inputs.contests.entries.forEachIndexed { idx, entry ->
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumJsonFromResourcePath(entry.value)
        val belgiumElectionJson = if (result.isOk) result.unwrap() else throw RuntimeException("$result")

        validateOutputDir(Path.of(toptopdir))

        allmvrs[entry.key] = createOneContestFromJson(entry.key, belgiumElectionJson, partyIds, toptopdir,
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



/* taken from https://resultatselection.belgium.be/fr/search/chambre-des-repr%C3%A9sentants/2024/circonscription-%C3%A9lectorale
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
) */



