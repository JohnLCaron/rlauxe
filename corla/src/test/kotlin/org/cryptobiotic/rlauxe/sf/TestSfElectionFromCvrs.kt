package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.*
import java.io.File
import kotlin.test.Test

class TestSfElectionFromCvrs {

    @Test
    fun createSF2024P() {
        // write sf2024 cvr
        val stopwatch = Stopwatch()
        val topDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$topDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$topDir/CVR_Export_20240322103409/ContestManifest.json"
        createSfElectionCvrs(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCvrs(
            auditDir,
            "$topDir/CVR_Export_20240322103409/ContestManifest.json",
            "$topDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cvrs.csv",
        )

        // create sorted cvrs
        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$topDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
    }

    // out of memory sort by sampleNum()
    @Test
    fun testSortMergeCvrs() {
        val auditDir = "/home/stormy/temp/sf2024"
        val cvrZipFile = "$auditDir/CVR_Export_20241202143051.zip"

        sortCvrs(auditDir, cvrZipFile, "$auditDir/sortChunks")
        mergeCvrs(auditDir, "$auditDir/sortChunks")
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
    fun createSF2024() {
        val stopwatch = Stopwatch()
        val topDir = "/home/stormy/temp/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "$topDir/CVR_Export_20241202143051/ContestManifest.json"
        createSfElectionCvrs(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"

        val auditDir = "$topDir/audit"
        createSfElectionFromCvrs(
            auditDir,
            "$topDir/CVR_Export_20241202143051/ContestManifest.json",
            "$topDir/CVR_Export_20241202143051/CandidateManifest.json",
            "$topDir/cvrs.csv",
        )

        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$topDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
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

        val cvrIter = readCvrsCsvIterator(publisher.cvrsCsvZipFile())
        var count = 0
        while (cvrIter.hasNext()) {
            val cvrUA = cvrIter.next()
            irvCounters.forEach { it.addCvr(cvrUA.cvr)}
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