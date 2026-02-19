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
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.Int


@Serializable
data class PopulationsJson(
    val populations: List<PopulationJson>,
)

fun List<PopulationIF>.publishJson() = PopulationsJson(
    this.map { it.publishJson() },
)

fun PopulationsJson.import(): List<Population> {
    return this.populations.map { it.import() }
}

@Serializable
class PopulationJson(
    val name: String,
    val id: Int,
    val ncards: Int,
    val possibleContests: IntArray,
    val hasSingleCardStyle: Boolean
)

fun PopulationIF.publishJson() = PopulationJson(
    this.name(),
    this.id(),
    this.ncards(),
    this.possibleContests(),
    this.hasSingleCardStyle()
)

fun PopulationJson.import() = Population(
        this.name,
        this.id,
        this.possibleContests,
        this.hasSingleCardStyle,
    ).setNcards(this.ncards)

/////////////////////////////////////////////////////////////////////////////////////////////

@OptIn(ExperimentalSerializationApi::class)
fun writePopulationsJsonFile(populations: List<PopulationIF>, filename: String) {
    val json = populations.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(json, out)
        out.close()
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun readPopulationsJsonFile(filename: String): Result<List<PopulationIF>, ErrorMessages> {
    val errs = ErrorMessages("readPopulationsJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val json = jsonReader.decodeFromStream<PopulationsJson>(inp)
            val contests = json.import()
            if (errs.hasErrors()) Err(errs) else Ok(contests)
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}

fun readPopulationsJsonFileUnwrapped(filename: String): List<PopulationIF> {
    return readPopulationsJsonFile(filename).unwrap()
}

