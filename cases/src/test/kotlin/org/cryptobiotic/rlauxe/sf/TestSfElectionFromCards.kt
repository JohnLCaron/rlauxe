package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.persist.csv.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.File
import kotlin.test.Test

class TestSfElectionFromCvrs {

    @Test
    fun createSF2024P() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/sf2024P"
        createAuditableCards(topDir, zipFilename, manifestFile) // write to "$topDir/cards.csv"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCards(
            auditDir,
            "$topDir/CVR_Export_20240322103409/ContestManifest.json",
            "$topDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cards.csv",
        )

        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCards.csv"
        println("that took $stopwatch")
    }

    // out of memory sort by sampleNum()
    @Test
    fun testSortMergeCvrs() {
        val topDir = "/home/stormy/temp/sf2024P"
        val auditDir = "$topDir/audit"
        // val zipFilename = "$auditDir/CVR_Export_20241202143051.zip"

        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks")
    }

    @Test
    fun testCopyFile() {
        val auditDir = "/home/stormy/temp/sf2024"
        val fromFile = File("src/test/data/SF2024/sortedCvrs.zip")
        val targetFile = File("$auditDir/sortedCvrs.zip")
        fromFile.copyTo(targetFile)
    }

    // write sf2024 cvrs
    @Test
    fun createSF2024cards() {
        val topDir = "/home/stormy/temp/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "$topDir/CVR_Export_20241202143051/ContestManifest.json"
        createAuditableCards(topDir, zipFilename, manifestFile) // write to "$topDir/cards.csv"
    }

    @Test
    fun createSF2024cardsDebug() {
        val topDir = "/home/stormy/temp/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val contestManifestFile = "$topDir/CVR_Export_20241202143051/ContestManifest.json"
        val candidateManifestFile = "$topDir/CVR_Export_20241202143051/CandidateManifest.json"
        createAuditableCardsDebug(topDir, zipFilename, contestManifestFile, candidateManifestFile)
    }

    @Test
    fun checkSF2024cardsDebug() {
        val topDir = "/home/stormy/temp/sf2024"
        val contestManifestFile = "$topDir/CVR_Export_20241202143051/ContestManifest.json"
        val candidateManifestFile = "$topDir/CVR_Export_20241202143051/CandidateManifest.json"
        checkAuditableCardsDebug("$topDir/cards.csv", contestManifestFile, candidateManifestFile)
    }

    @Test
    fun createSF2024() {
        val topDir = "/home/stormy/temp/sf2024"
        val auditDir = "$topDir/audit"
        createSfElectionFromCards(
            auditDir,
            "$topDir/CVR_Export_20241202143051/ContestManifest.json",
            "$topDir/CVR_Export_20241202143051/CandidateManifest.json",
            "$topDir/cards.csv",
            show = false,
        )
    }

    @Test
    fun sortSF2024() {
        val topDir = "/home/stormy/temp/sf2024"
        val auditDir = "$topDir/audit"
        sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
        mergeCards(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCards.csv"
        // manually zip (TODO)
    }

    @Test
    fun showSfElectionContests() {
        val publisher = Publisher("/home/stormy/temp/sf2024/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        contestsUA.forEach { contestUA ->
            println("$contestUA ${contestUA.contest.choiceFunction}")
            contestUA.contest.info.candidateNames.forEach { println("  $it") }
        }
    }

    @Test
    fun showIrvCounts() {
        val publisher = Publisher("/home/stormy/temp/sf2024/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        val irvCounters = mutableListOf<IrvCounter>()
        contestsUA.filter { it.choiceFunction == SocialChoiceFunction.IRV}.forEach { contestUA ->
            println("$contestUA")
            println("   winners=${contestUA.contest.winnerNames}")
            irvCounters.add(IrvCounter(contestUA.contest as RaireContest))
        }

        val cvrIter = CvrIteratorAdapter(readCardsCsvIterator(publisher.cardsCsvZipFile()))
        var count = 0
        while (cvrIter.hasNext()) {
            irvCounters.forEach { it.addCvr(cvrIter.next())}
            count++
        }
        println("processed $count cvrs")

        irvCounters.forEach { counter ->
            println("${counter.rcontest}")
            val cvotes = counter.vc.makeVotes()
            val irvCount = IrvCount(cvotes, counter.rcontest.info.candidateIds)
            showIrvCount(counter.rcontest, irvCount)
        }
    }
}

data class IrvCounter(val rcontest: RaireContest) {
    val vc = VoteConsolidator()
    val contestId = rcontest.id

    fun addCvr( cvr: Cvr) {
        val votes = cvr.votes[contestId]
        if (votes != null) {
            vc.addVote(votes)
        }
    }
}

fun showIrvCount(rcontest: RaireContest, irvCount: IrvCount) {
    val roundResult = irvCount.runRounds()
    println(showIrvCountResult(roundResult, rcontest.info))
    println("================================================================================================\n")
}