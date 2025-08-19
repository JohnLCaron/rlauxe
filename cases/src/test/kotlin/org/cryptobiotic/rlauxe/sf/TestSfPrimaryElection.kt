package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class TestSfPrimaryElection {

    @Test
    fun createSfPrimaryElection() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/rla/cases/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "ContestManifest.json"
        val topDir = "/home/stormy/rla/cases/sf2024P"
        createAuditableCards(topDir, zipFilename, manifestFile) // write to "$topDir/cards.csv"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCards(
            auditDir,
            zipFilename,
            manifestFile,
            "CandidateManifest.json",
            "$topDir/cards.csv",
        )

        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCards.csv"
        println("that took $stopwatch")
    }

    // out of memory sort by sampleNum()
    // @Test
    fun testSortMergeCvrs() {
        val topDir = "/home/stormy/rla/cases/sf2024P"
        val auditDir = "$topDir/audit"
        // val zipFilename = "$auditDir/CVR_Export_20241202143051.zip"

        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks")
    }
}