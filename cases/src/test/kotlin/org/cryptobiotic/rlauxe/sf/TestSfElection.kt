package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestSfElection {

    // extract the cvrExport from zipFilename json, write to cvrExport.csv
    @Test
    fun createSF2024cvrs() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "ContestManifest.json"
        val summary = createCvrExportCsvFile(topDir, zipFilename, manifestFile) // write to "$topDir/cvrExport.csv"
        println(summary)

        // check that the cvrs agree with the summary XML
        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        // staxContests.forEach { println(it) }

        val contestManifest = readContestManifestFromZip(zipFilename, manifestFile)
        summary.contestSums.forEach { (id, contestSum) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest? = staxContests.find { it.id == contestName }
            assertNotNull(staxContest)
            assertEquals(staxContest.ncards(), contestSum.ncards)
            assertEquals(staxContest.undervotes(), contestSum.undervotes)
        }
    }

    // create the audit contests using the cvrExport records
    @Test
    fun createSF2024election() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        clearDirectory(Path.of(auditDir))
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"

        createSfElectionFromCvrExport(
            auditDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            "$topDir/$cvrExportCsvFile",
            show = false,
        )
        createSF2024sortedCards()
    }

    // create sorted cards, assumes auditDir/auditConfig already exists
    // @Test
    fun createSF2024sortedCards() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        val cvrCsv = "$topDir/cvrExport.csv"
        createSortedCards(topDir, auditDir, cvrCsv, zip = true) // write to "$auditDir/sortedCards.csv"
    }

    // create the audit contests using the cvrExport records
    @Test
    fun createSF2024contestsRepeat() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val cvrCsv = "$topDir/cvrExport.csv"

        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 50,
            minRecountMargin = 0.0,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.noerror),
            skipContests = listOf(14, 28)
        )

        repeat(10) { run ->
            val auditDir = "$topDir/audit$run"
            clearDirectory(Path.of(auditDir))

            createSfElectionFromCvrExport(
                auditDir,
                castVoteRecordZip = zipFilename,
                contestManifestFilename = "ContestManifest.json",
                candidateManifestFile = "CandidateManifest.json",
                cvrExportCsv = "$topDir/$cvrExportCsvFile",
                auditConfigIn = auditConfig,
                show = false,
            )

            val workingDir = "$topDir/sortChunks$run"
            createSortedCards(
                topDir,
                auditDir,
                cvrCsv,
                zip = true,
                workingDir = workingDir
            ) // write to "$auditDir/sortedCards.csv"
        }
    }

    @Test
    fun showSfElectionContests() {
        val publisher = Publisher("/home/stormy/rla/cases/sf2024/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        contestsUA.forEach { contestUA ->
            println("$contestUA ${contestUA.contest.choiceFunction}")
            contestUA.contest.info().candidateNames.forEach { println("  $it") }
        }
    }
}