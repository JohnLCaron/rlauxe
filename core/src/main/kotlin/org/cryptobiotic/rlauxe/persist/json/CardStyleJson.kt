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
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int

@Serializable
data class CardStylesJson(
    val batches: List<CardStyleJson>,
)

fun List<CardStyleIF>.publishJson() = CardStylesJson(
    this.map { it.publishJson() },
)

fun CardStylesJson.import(): List<CardStyle> {
    return this.batches.map { it.import() }
}

@Serializable
class CardStyleJson(
    val name: String,
    val id: Int,
    // val ncards: Int,
    val possibleContests: IntArray,
    val hasSingleCardStyle: Boolean
)

fun CardStyleIF.publishJson() = CardStyleJson(
    this.name(),
    this.id(),
    // this.ncards(),
    this.possibleContests(),
    this.hasSingleCardStyle()
)

fun CardStyleJson.import() = CardStyle(
        this.name,
        this.id,
        this.possibleContests,
        this.hasSingleCardStyle,
    ) // .setNcards(this.ncards)

/////////////////////////////////////////////////////////////////////////////////////////////

@OptIn(ExperimentalSerializationApi::class)
fun writeCardStylesJsonFile(batches: List<CardStyleIF>, filename: String) {
    val json = batches.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun readCardStylesJsonFile(filename: String): Result<List<CardStyle>, ErrorMessages> {
    val errs = ErrorMessages("readBatchesJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<CardStylesJson>(inp)
            val contests = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

// this requires the file to exist or you get an Exception
fun readCardStylesJsonFileUnwrapped(filename: String): List<CardStyle> {
    return readCardStylesJsonFile(filename).unwrap()
}

