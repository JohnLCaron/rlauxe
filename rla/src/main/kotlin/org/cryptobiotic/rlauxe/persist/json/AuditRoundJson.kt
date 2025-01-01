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
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.safeEnumValueOf

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

////////////////// writer

fun writeAuditRoundJsonFile(auditRound: AuditRound, filename: String) {
    val json = auditRound.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

data class AuditRound(
    val round: Int,
    val contests: List<ContestUnderAudit>,
    val done: Boolean,
)

@Serializable
data class AuditRoundJson(
    val round: Int,
    val contests: List<AuditContestJson>,
    val done: Boolean,
)

@Serializable
data class AuditContestJson(
    val name: String,
    val id: Int,
    val assertions: List<AssertionResultJson>,
    val done: Boolean,
)

@Serializable
data class AssertionResultJson(
    val desc: String,
    val roundIdx: Int,
    val estSampleSize: Int,   // estimated sample size
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val pvalue: Double,       // last pvalue when testH0 terminates
    val status: String, // testH0 status
)

fun AuditRound.publishJson(): AuditRoundJson {
    val showContests = contests.filter {
        var want = false
        // if any assertion contains a result for this round
        it.assertions().forEach {
            if (it.roundResults.size == this.round) want = true
        }
        want
    }
    return AuditRoundJson(
        this.round,
        showContests.map { it.publishJson(this.round) },
        this.done,
    )
}

fun ContestUnderAudit.publishJson(round: Int) : AuditContestJson {
    val showAssertions = this.assertions().filter { it.roundResults.size == round }
    return AuditContestJson(
        this.name,
        this.id,
        showAssertions.map { it.publishJson(round) },
        this.done,
    )
}

fun Assertion.publishJson(round: Int) : AssertionResultJson {
    val roundResult = this.roundResults[round-1]
    return AssertionResultJson(
        this.toString(),
        roundResult.roundIdx,
        roundResult.estSampleSize,
        roundResult.samplesNeeded,
        roundResult.samplesUsed,
        roundResult.pvalue,
        roundResult.status.name,
    )
}

//////////////////////////////////////////////////////////////
// reasing

fun readAuditRoundJsonFile(filename: String): Result<AuditResult, ErrorMessages> {
    val errs = ErrorMessages("readAuditRoundJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<AuditRoundJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(json.import())
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

data class AuditResult(
    val round: Int,
    val contests: List<ContestResult>,
    val done: Boolean,
)

data class ContestResult(
    val name: String,
    val id: Int,
    val assertions: List<AssertionResult>,
    val done: Boolean,
)

data class AssertionResult(
    val desc: String,
    val round: Int,
    val estSampleSize: Int,   // estimated sample size
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates, usually maxSamples
    val pvalue: Double,       // last pvalue when testH0 terminates
    val status: TestH0Status, // testH0 status
)

fun AuditRoundJson.import() = AuditResult (
    this.round,
    this.contests.map { it.import() },
    this.done,
)

fun AuditContestJson.import(): ContestResult {
    return ContestResult(
        this.name,
        this.id,
        this.assertions.map { it.import() },
        this.done,
    )
}

fun AssertionResultJson.import() : AssertionResult {
    val status = safeEnumValueOf(this.status) ?: TestH0Status.InProgress

    return AssertionResult(
        this.desc,
        this.roundIdx,
        this.estSampleSize,
        this.samplesNeeded,
        this.samplesUsed,
        this.pvalue,
        status,
    )
}





