@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.enumValueOf
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// data class ContestInfo(
//    val name: String,
//    val id: Int,
//    val candidateNames: Map<String, Int>, // candidate name -> candidate id
//    val choiceFunction: SocialChoiceFunction,
//    val nwinners: Int = 1,
//    val minFraction: Double? = null,
//)
@Serializable
data class ContestInfoJson(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: String,
    val nwinners: Int,
    val voteForN: Int? = null,
    val minFraction: Double?,
    val metadata: Map<String, Int>?,
)

fun ContestInfo.publishJson() : ContestInfoJson {
    return ContestInfoJson(
        this.name,
        this.id,
        this.candidateNames,
        this.choiceFunction.name,
        this.nwinners,
        this.voteForN,
        this.minFraction,
        this.metadata,
    )
}

fun ContestInfoJson.import(): ContestInfo {
    val choiceFunction = enumValueOf(this.choiceFunction, SocialChoiceFunction.entries) ?: SocialChoiceFunction.PLURALITY
    val info = ContestInfo(
        this.name,
        this.id,
        this.candidateNames,
        choiceFunction,
        this.nwinners,
        this.voteForN ?: this.nwinners,
        this.minFraction,
    )
    if (this.metadata != null) {
        this.metadata.forEach { (key, value) -> info.metadata[key] = value}
    }
    return info
}

// open class Contest(
//        val info: ContestInfo,
//        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
//        val Nc: Int,               // trusted maximum ballots/cards that contain this contest
//        val Ncast: Int,            // number of cast ballots containing this Contest, including undervotes
//    ): ContestIF
//
// data class RaireContest(
//    val info: ContestInfo,
//    val winners: List<Int>,
//    val Nc: Int,
//    val Ncast: Int,
//) : ContestIF {
//     val roundsPaths = mutableListOf<IrvRoundsPath>()

@Serializable
data class ContestIFJson(
    val className: String,
    val votes: Map<Int, Int>?, // candidate name -> candidate id
    val winners: List<Int>?,
    val Nc: Int,
    val Ncast: Int,
    val irvRoundsPaths: List<IrvRoundsPathJson>? = null,
    val undervotes: Int? = null,
    val sortedScores: List<DhondtScoreJson>? = null
)

fun ContestIF.publishJson() : ContestIFJson {
    return when (this) {
        is DHondtContest ->
            ContestIFJson(
                "DHondtContest",
                votes = this.votes,
                this.winners,
                this.Nc,
                this.Ncast,
                sortedScores = this.sortedScores.map { it.publishJson() }
            )
        is Contest ->
            ContestIFJson(
                "Contest",
                this.votes,
                null,
                this.Nc,
                this.Ncast,
            )
        is RaireContest ->
            ContestIFJson(
                "RaireContest",
                votes = null,
                this.winners,
                this.Nc,
                this.Ncast,
                irvRoundsPaths = this.roundsPaths.map { it.publishJson() },
                undervotes = this.undervotes
            )
        else -> throw RuntimeException("unknown contest type ${this.javaClass.simpleName} = $this")
    }
}

fun ContestIFJson.import(info: ContestInfo): ContestIF {
    return when (this.className) {
        "Contest" ->
            Contest(
                info,
                this.votes!!,
                this.Nc,
                this.Ncast,
            )
        "RaireContest" -> {
            val rcontest = RaireContest(
                info,
                this.winners!!,
                this.Nc,
                this.Ncast,
                undervotes = this.undervotes ?: 0,
            )
            if (this.irvRoundsPaths != null) {
                rcontest.roundsPaths.addAll(this.irvRoundsPaths.map { it.import() })
            }
            rcontest
        }
        "DHondtContest",
        "ContestDHondt" -> {
            DHondtContest(
                info,
                this.votes!!,
                this.Nc,
                this.Ncast,
                sortedScores = this.sortedScores!!.map { it.import() }
            )
        }
        else -> throw RuntimeException()
    }
}

// TODO multiple paths
@Serializable
data class IrvRoundsPathJson(
    val rounds: List<Map<Int, Int>>,
    val done: Boolean,
    val winners: Set<Int>,
)

// IrvRoundsPath(val rounds: List<IrvRound>, val irvWinner: IrvWinners)
// IrvRound(val count: Map<Int, Int>)
// IrvWinners(val done:Boolean = false, val winners: Set<Int> = emptySet())
fun IrvRoundsPath.publishJson() = IrvRoundsPathJson(
    this.rounds.map { round -> round.count }, // List<Map<Int, Int>>
    this.irvWinner.done,
    this.irvWinner.winners,
)

fun IrvRoundsPathJson.import() = IrvRoundsPath(
    this.rounds.map { count -> IrvRound(count) },
    IrvWinners(this.done, this.winners),
)

// data class DhondtScore(val candidate: Int, val score: Double, val divisor: Int) {
@Serializable
data class DhondtScoreJson(
    val candidate: Int,
    val score: Double,
    val divisor: Int,
    val winningSeat: Int?,
)

fun DhondtScore.publishJson() = DhondtScoreJson(candidate, score, divisor, winningSeat)

fun DhondtScoreJson.import() = DhondtScore(candidate, score, divisor).setWinningSeat(this.winningSeat)


// open class ContestUnderAudit(
//    val contest: ContestIF,
//    val isComparison: Boolean = true,
//    val hasStyle: Boolean = true,
//) {
//    var preAuditStatus = TestH0Status.InProgress // pre-auditing status: NoLosers, NoWinners, ContestMisformed, MinMargin, TooManyPhantoms
//    var pollingAssertions: List<Assertion> = emptyList() // mutable needed for Raire override and serialization
//    var clcaAssertions: List<ClcaAssertion> = emptyList() // mutable needed for serialization

@Serializable
data class ContestUnderAuditJson(
    val info: ContestInfoJson, // This is where the infos are kept.
    val contest: ContestIFJson,
    val isComparison: Boolean,
    val hasStyle: Boolean,
    var pollingAssertions: List<AssertionIFJson>,
    var clcaAssertions: List<ClcaAssertionJson>,
    val status: TestH0Status,
    val Npop: Int? = null,
)

fun ContestUnderAudit.publishJson() : ContestUnderAuditJson {
    return ContestUnderAuditJson(
        this.contest.info().publishJson(),
        this.contest.publishJson(),
        this.isClca,
        this.hasCompleteCvrs,
        this.pollingAssertions.map { it.publishIFJson() },
        this.clcaAssertions.map { it.publishJson() },
        this.preAuditStatus,
        this.Npop,
    )
}

fun ContestUnderAuditJson.import(): ContestUnderAudit {
    val info = this.info.import()
    val contestUA = ContestUnderAudit(this.contest.import(info), isClca=this.isComparison, hasStyle=this.hasStyle, NpopIn = this.Npop)
    contestUA.pollingAssertions = this.pollingAssertions.map { it.import(info) }
    contestUA.clcaAssertions = this.clcaAssertions.map { it.import(info) }
    contestUA.preAuditStatus = this.status
    return contestUA
}

//////////////////////////////////////////////////////////////////////////
@Serializable
data class ContestsUnderAuditJson(
    val contestsUnderAudit: List<ContestUnderAuditJson>,
    val rcontestsUnderAudit: List<RaireContestUnderAuditJson>,
)

fun List<ContestUnderAudit>.publishJson() : ContestsUnderAuditJson {
    val contests = mutableListOf<ContestUnderAuditJson>()
    val rcontests = mutableListOf<RaireContestUnderAuditJson>()
    this.forEach {
        if (it is RaireContestUnderAudit) {
            rcontests.add( it.publishRaireJson())
        } else {
            contests.add( it.publishJson())
        }
    }
    return ContestsUnderAuditJson(contests, rcontests)
}

fun ContestsUnderAuditJson.import() : List<ContestUnderAudit> {
    return this.contestsUnderAudit.map { it.import() } +
            this.rcontestsUnderAudit.map { it.import() }
}

/////////////////////////////////////////////////////////////////////////////////////////////

fun writeContestsJsonFile(contests: List<ContestUnderAudit>, filename: String) {
    val json = contests.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

fun readContestsJsonFile(filename: String): Result<List<ContestUnderAudit>, ErrorMessages> {
    val errs = ErrorMessages("readContestsJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<ContestsUnderAuditJson>(inp)
            val contests = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readContestsJsonFileUnwrapped(filename: String): List<ContestUnderAudit> {
    return readContestsJsonFile(filename).unwrap()
}