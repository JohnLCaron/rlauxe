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

    // make a OneAudit from Dominion exprted CVRs, using CountingGroupId=1 as the pooled votes
    @Test
    fun createSF2024PoaCvrs() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/sf2024Poa"
        createSfElectionCvrsOA(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"

        //  createSfElectionCvrsOA 8957 files totalCards=467063 group1=55810 group2=411253
        // countingContests
        //   1 total=176637, groupCount={2=155705, 1=20932}
        //   2 total=19175, groupCount={2=15932, 1=3243}
        //   3 total=4064, groupCount={2=3300, 1=764}
        //   4 total=1314, groupCount={2=1112, 1=202}
        //   5 total=919, groupCount={2=647, 1=272}
        //   6 total=1092, groupCount={2=889, 1=203}
        //   8 total=94083, groupCount={2=83709, 1=10374}
        //   9 total=72733, groupCount={2=64285, 1=8448}
        //   10 total=8818, groupCount={2=7304, 1=1514}
        //   11 total=10357, groupCount={2=8628, 1=1729}
        //   12 total=233465, groupCount={2=205536, 1=27929}
        //   13 total=233465, groupCount={2=205536, 1=27929}
        //   15 total=211793, groupCount={2=186075, 1=25718}
        //   17 total=21672, groupCount={2=19461, 1=2211}
        //   19 total=233598, groupCount={2=205717, 1=27881}
        //   21 total=127791, groupCount={2=112879, 1=14912}
        //   23 total=105807, groupCount={2=92838, 1=12969}
        //   24 total=233598, groupCount={2=205717, 1=27881}
        //   25 total=233598, groupCount={2=205717, 1=27881}
        //   26 total=233598, groupCount={2=205717, 1=27881}
        //   27 total=233598, groupCount={2=205717, 1=27881}
        //   28 total=233598, groupCount={2=205717, 1=27881}
        //   29 total=233598, groupCount={2=205717, 1=27881}
        //   30 total=233598, groupCount={2=205717, 1=27881}
        //   31 total=233598, groupCount={2=205717, 1=27881}
        //   32 total=233598, groupCount={2=205717, 1=27881}
        //   33 total=233598, groupCount={2=205717, 1=27881}
        // writing to /home/stormy/temp/sf2024Poa/ballotManifest.csv with 8957 batches
        // total ballotManifest = 467063
        // writing to /home/stormy/temp/sf2024Poa/ballotManifest.csv with 2086 pools
        // total cards in pools = 55810
    }

    @Test
    fun createSF2024Poa() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val topDir = "/home/stormy/temp/sf2024Poa"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCvrsOA(
            auditDir,
            "$sfDir/CVR_Export_20240322103409/ContestManifest.json",
            "$sfDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cvrs.csv",
            listOf(1, 2)
        )

        // create sorted cvrs
        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
    }

    @Test
    fun createSF2024P() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/sf2024P"
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
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "auditDir/sortedCvrs.csv"
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
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "auditDir/sortedCvrs.csv"
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