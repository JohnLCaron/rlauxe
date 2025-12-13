package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class TestReadSfJson {

    @Test
    fun testContestManifestJsonFile() {
        val filename = "src/test/data/SF2024/manifests/ContestManifest.json"
        val result: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(filename)
        val contestManifest = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from ${filename} err = $result")
        println(contestManifest)
    }

    @Test
    fun testCandidateManifestJsonFile() {
        val filename = "src/test/data/SF2024/manifests/CandidateManifest.json"
        val result: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJson(filename)
        val candidateManifest = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read CandidateManifestJson from ${filename} err = $result")
        println(candidateManifest)
    }

    @Test
    fun makeContestInfo() {
        val topDir = "$testdataDir/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"

        val resultContestM: Result<ContestManifestJson, ErrorMessages> =  readContestManifestJsonFromZip(zipFilename, "ContestManifest.json")
        val contestManifestJson = if (resultContestM is Ok) resultContestM.unwrap()
        else throw RuntimeException("Cannot read ContestManifestJson from $zipFilename err = $resultContestM")

        val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJsonFromZip(zipFilename, "CandidateManifest.json")
        val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
        else throw RuntimeException("Cannot read CandidateManifestJson from ${zipFilename} err = $resultCandidateM")

        val contestManifest = readContestManifestFromZip(zipFilename, "ContestManifest.json")
        val contestInfos = makeContestInfos(contestManifest, candidateManifest).sortedBy { it.id }
        val mayor = contestInfos.find { it.id == 18 }!!
        println(mayor)
    }

    @Test
    fun testBallotTypeContestManifestJsonFile() {
        val filename = "src/test/data/SF2024/manifests/BallotTypeContestManifest.json"
        val result: Result<BallotTypeContestManifest, ErrorMessages> = readBallotTypeContestManifestJson(filename)
        val manifest1 = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read BallotTypeContestManifest from ${filename} err = $result")
        println(manifest1)

        val topDir = "$testdataDir/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val ifilename = "BallotTypeContestManifest.json"
        val result2: Result<BallotTypeContestManifest, ErrorMessages> = readBallotTypeContestManifestJsonFromZip(zipFilename, ifilename)
        val manifest2 = if (result2 is Ok) result2.unwrap()
            else throw RuntimeException("Cannot read BallotTypeContestManifest from $zipFilename/$ifilename err = $result")
        // println(manifest2)

        manifest2.ballotStyles.forEach { (key, value2) ->
            val values1 = manifest1.ballotStyles[key]!!
            assertTrue (value2.contentEquals(values1))
        }
    }

}