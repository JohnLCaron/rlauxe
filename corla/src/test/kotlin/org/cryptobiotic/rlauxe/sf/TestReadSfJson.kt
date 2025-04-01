package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.CvrCsv
import org.cryptobiotic.rlauxe.persist.csv.publishCsv
import org.cryptobiotic.rlauxe.persist.csv.writeCSV
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.ZipReaderTour
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test

class TestReadSfJson {

    @Test
    fun testContestManifestJsonFile() {
        val filename = "src/test/data/SF2024/ContestManifest.json"
        val result: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(filename)
        val contestManifest = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from ${filename} err = $result")
        println(contestManifest)
    }

    @Test
    fun testCandidateManifestJsonFile() {
        val filename = "src/test/data/SF2024/CandidateManifest.json"
        val result: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJson(filename)
        val candidateManifest = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read CandidateManifestJson from ${filename} err = $result")
        println(candidateManifest)
    }

}