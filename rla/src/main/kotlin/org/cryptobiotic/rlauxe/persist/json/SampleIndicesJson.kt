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
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.ErrorMessages

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Serializable
data class SampleIndicesJson(
    val sampleNumbers: List<Int>,
)

fun List<Int>.publishJson() = SampleIndicesJson(
    this.map { it },
)

fun SampleIndicesJson.import(): List<Int> {
    return this.sampleNumbers
}

fun writeSampleIndicesJsonFile(sampleNumbers: List<Int>, filename: String) {
    val cvrsj = sampleNumbers.publishJson()
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }
    FileOutputStream(filename).use { out ->
        jsonReader.encodeToStream(cvrsj, out)
        out.close()
    }
}

fun readSampleIndicesJsonFile(filename: String): Result<List<Int>, ErrorMessages> {
    val errs = ErrorMessages("readSampleIndicesJsonFile '${filename}'")
    val filepath = Path.of(filename)
    if (!Files.exists(filepath)) {
        return errs.add("file does not exist")
    }
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true }

    return try {
        Files.newInputStream(filepath, StandardOpenOption.READ).use { inp ->
            val cvrs = jsonReader.decodeFromStream<SampleIndicesJson>(inp)
            if (errs.hasErrors()) Err(errs) else Ok(cvrs.import())
        }
    } catch (t: Throwable) {
        errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
    }
}