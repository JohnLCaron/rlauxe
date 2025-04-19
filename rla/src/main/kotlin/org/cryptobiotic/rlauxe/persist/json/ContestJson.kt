@file:OptIn(ExperimentalSerializationApi::class)
package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
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
//    val minFraction: Double? = null, // supermajority only.
//)
@Serializable
data class ContestInfoJson(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: String,
    val nwinners: Int,
    val voteForN: Int,
    val minFraction: Double?,
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
    )
}

fun ContestInfoJson.import(): ContestInfo {
    val choiceFunction = enumValueOf(this.choiceFunction, SocialChoiceFunction.entries) ?: SocialChoiceFunction.PLURALITY
    return ContestInfo(
        this.name,
        this.id,
        this.candidateNames,
        choiceFunction,
        this.nwinners,
        this.voteForN,
        this.minFraction,
    )
}

// class Contest(
//        override val info: ContestInfo,
//        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
//        override val Nc: Int,
//        override val Np: Int,
//    ): ContestIF {
// data class RaireContest(
//    override val info: ContestInfo,
//    override val winners: List<Int>,
//    override val Nc: Int,
//    override val Np: Int,
//)
@Serializable
data class ContestIFJson(
    val className: String,
    val votes: Map<Int, Int>?, // candidate name -> candidate id
    val winners: List<Int>?,
    val Nc: Int,
    val Np: Int,
    val irvRoundsPaths: List<IrvRoundsPathJson>? = null,
)

fun ContestIF.publishJson() : ContestIFJson {
    return when (this) {
        is Contest ->
            ContestIFJson(
                "Contest",
                // this.info.publishJson(),
                this.votes,
                null,
                this.Nc,
                this.Np,
            )
        is RaireContest ->
            ContestIFJson(
                "RaireContest",
                null,
                this.winners,
                this.Nc,
                this.Np,
                this.roundsPaths.map { it.publishJson() },
            )
        else -> throw RuntimeException("unknown assorter type ${this.javaClass.simpleName} = $this")
    }
}

fun ContestIFJson.import(info: ContestInfo): ContestIF {
    return when (this.className) {
        "Contest" ->
            Contest(
                info,
                this.votes!!,
                this.Nc,
                this.Np,
            )
        "RaireContest" -> {
            val rcontest = RaireContest(
                info,
                this.winners!!,
                this.Nc,
                this.Np,
            )
            if (this.irvRoundsPaths != null) {
                rcontest.roundsPaths.addAll(this.irvRoundsPaths.map { it.import() })
            }
            rcontest
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

// open class ContestUnderAudit(
//    val contest: ContestIF,
//    val isComparison: Boolean = true,
//    val hasStyle: Boolean = true,
//) {
//    val id = contest.info.id
//    val name = contest.info.name
//    val choiceFunction = contest.info.choiceFunction
//    val ncandidates = contest.info.candidateIds.size
//    val Nc = contest.Nc
//    val Np = contest.Np
//
//    var pollingAssertions: List<Assertion> = emptyList()
//    var clcaAssertions: List<ClcaAssertion> = emptyList()
@Serializable
data class ContestUnderAuditJson(
    val info: ContestInfoJson,
    val contest: ContestIFJson,
    val isComparison: Boolean,
    val hasStyle: Boolean,
    var pollingAssertions: List<AssertionJson>,
    var clcaAssertions: List<ClcaAssertionJson>,
    val status: TestH0Status,
)

fun ContestUnderAudit.publishJson() : ContestUnderAuditJson {
    return ContestUnderAuditJson(
        this.contest.info.publishJson(),
        this.contest.publishJson(),
        this.isComparison,
        this.hasStyle,
        this.pollingAssertions.map { it.publishJson() },
        this.clcaAssertions.map { it.publishJson() },
        this.preAuditStatus
    )
}

fun ContestUnderAuditJson.import(): ContestUnderAudit {
    val info = this.info.import()
    val contestUA = ContestUnderAudit(
        this.contest.import(info),
        this.isComparison,
        this.hasStyle,
    )
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
    val oacontestsUnderAudit: List<OAContestUnderAuditJson>,
)

fun List<ContestUnderAudit>.publishJson() : ContestsUnderAuditJson {
    val contests = mutableListOf<ContestUnderAuditJson>()
    val rcontests = mutableListOf<RaireContestUnderAuditJson>()
    val oacontests = mutableListOf<OAContestUnderAuditJson>()
    this.forEach {
        if (it is RaireContestUnderAudit) {
            rcontests.add( it.publishRaireJson())
        } else if (it is OAContestUnderAudit) {
            oacontests.add( it.publishOAJson())
        } else {
            contests.add( it.publishJson())
        }
    }
    return ContestsUnderAuditJson(contests, rcontests, oacontests)
}

fun ContestsUnderAuditJson.import() : List<ContestUnderAudit> {
    return this.contestsUnderAudit.map { it.import() } +
            this.rcontestsUnderAudit.map { it.import() } +
            this.oacontestsUnderAudit.map { it.import() }
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