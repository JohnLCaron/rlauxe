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
import org.cryptobiotic.rlauxe.audit.Batch
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int

@Serializable
data class BatchesJson(
    val batches: List<BatchJson>,
)

fun List<BatchIF>.publishJson() = BatchesJson(
    this.map { it.publishJson() },
)

fun BatchesJson.import(): List<Batch> {
    return this.batches.map { it.import() }
}

@Serializable
class BatchJson(
    val name: String,
    val id: Int,
    val ncards: Int,
    val possibleContests: IntArray,
    val hasSingleCardStyle: Boolean
)

fun BatchIF.publishJson() = BatchJson(
    this.name(),
    this.id(),
    this.ncards(),
    this.possibleContests(),
    this.hasSingleCardStyle()
)

fun BatchJson.import() = Batch(
        this.name,
        this.id,
        this.possibleContests,
        this.hasSingleCardStyle,
    ).setNcards(this.ncards)

/////////////////////////////////////////////////////////////////////////////////////////////

@OptIn(ExperimentalSerializationApi::class)
fun writeBatchesJsonFile(batches: List<BatchIF>, filename: String) {
    val json = batches.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun readBatchesJsonFile(filename: String): Result<List<Batch>, ErrorMessages> {
    val errs = ErrorMessages("readBatchesJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<BatchesJson>(inp)
            val contests = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

// this requires the file to exist or you get an Exception
fun readBatchesJsonFileUnwrapped(filename: String): List<Batch> {
    return readBatchesJsonFile(filename).unwrap()
}

