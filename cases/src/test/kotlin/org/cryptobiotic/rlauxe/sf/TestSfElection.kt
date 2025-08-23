package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.audit.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.CvrExportAdapter
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.raire.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestSfElection {

    // extract the cvrs from json
    @Test
    fun createSF2024cvrs() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
        val manifestFile = "ContestManifest.json"
        val summary = createCvrExportCsvFile(topDir, zipFilename, manifestFile) // write to "$topDir/cvrExport.csv"
        println(summary)

        // check that the cvrs agree with the summary XML
        val xmlFile = "src/test/data/SF2024/summary.xml"
        val reader = StaxReader()
        val staxContests = reader.read(xmlFile)
        // staxContests.forEach { println(it) }

        val contestManifest = readContestManifestFromZip(zipFilename, manifestFile)
        summary.contestSums.forEach { (id, contest) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest? = staxContests.find { it.id == contestName}
            assertNotNull(staxContest)
            assertEquals(staxContest.ncards(), contest.ncards)
            assertEquals(staxContest.undervotes(), contest.undervotes)
            if (contest.overvotes != contest.isOvervote) {
                println("cvrSummary $contestName ($id) has overvotes = ${contest.overvotes} not equal to isOvervote = ${contest.isOvervote} ")
                // assertEquals(contest.overvotes, contest.isOvervote)
            }
            assertEquals(staxContest.overvotes(), contest.overvotes)
            if (staxContest.blanks() != contest.isBlank) {
                println("staxContest $contestName ($id) has blanks = ${staxContest.blanks()} not equal to cvr summary = ${contest.isBlank} ")
                // assertEquals(staxContest.blanks(), contest.blanks)
            }
        }

        // TODO serialize summary so can use in contest creation?
        //   or just use staxContests, since the ncards agree?

        // IRV contests = [18, 23, 24, 25, 26, 27, 28, 19, 21, 22, 20]
        // read 1603908 cvrs in 27554 files; took 55.16 s

        // IRV contests = [18, 23, 24, 25, 26, 27, 28, 19, 21, 22, 20]
        // read 1603908 cards in 27554 files took 55.13 s
    }

    // create the audit contests using the cvrExport records
    @Test
    fun createSF2024contests() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        clearDirectory(Path.of(auditDir))

        val zipFilename = "$topDir/CVR_Export_20241202143051.zip"

        createSfElectionFromCsvExport(
            auditDir,
            zipFilename,
            "ContestManifest.json",
            "CandidateManifest.json",
            "$topDir/$cvrExportCsvFile",
            show = false,
        )
    }

    // create sorted cards, assumes auditDir/auditConfig already exists
    @Test
    fun createSF2024cards() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        val cvrCsv = "$topDir/cvrExport.csv"
        createSortedCards(topDir, auditDir, cvrCsv, zip = true) // write to "$auditDir/sortedCards.csv"
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

    @Test
    fun showIrvCounts() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val publisher = Publisher("$topDir/audit")
        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        val contestsUA = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        val irvCounters = mutableListOf<IrvCounter>()
        contestsUA.filter { it.choiceFunction == SocialChoiceFunction.IRV}.forEach { contestUA ->
            println("$contestUA")
            println("   winners=${contestUA.contest.winnerNames()}")
            irvCounters.add(IrvCounter(contestUA.contest as RaireContest))
        }

        val cvrCsv = "$topDir/cvrExport.csv"
        val cvrIter = CvrExportAdapter(cvrExportCsvIterator(cvrCsv))
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