package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
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
        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        // staxContests.forEach { println(it) }

        val contestManifest = readContestManifestFromZip(zipFilename, manifestFile)
        summary.contestSums.forEach { (id, contest) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest? = staxContests.find { it.id == contestName }
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
        //read 1603908 cvrs in 27554 files; took 52.61 s
        //took = 52.62 s
        //DominionCvrSummary ncvrs=1603908 ncards=1641744 cvrs=[]
        //  ContestSummary 1 ncards=412121 undervotes=8463 overvotes=661 isOvervote=0 isBlank = 9124
        //  ContestSummary 2 ncards=412121 undervotes=34597 overvotes=171 isOvervote=0 isBlank = 34768
        //  ContestSummary 3 ncards=412121 undervotes=38960 overvotes=103 isOvervote=0 isBlank = 39063
        //  ContestSummary 5 ncards=369231 undervotes=30043 overvotes=77 isOvervote=0 isBlank = 30120
        //  ContestSummary 7 ncards=42890 undervotes=6726 overvotes=14 isOvervote=0 isBlank = 6740
        //  ContestSummary 9 ncards=412121 undervotes=42947 overvotes=107 isOvervote=0 isBlank = 43054
        //  ContestSummary 11 ncards=226823 undervotes=26383 overvotes=50 isOvervote=0 isBlank = 26433
        //  ContestSummary 13 ncards=185298 undervotes=32860 overvotes=102 isOvervote=0 isBlank = 32962
        //  ContestSummary 14 ncards=412231 undervotes=632530 overvotes=3616 isOvervote=0 isBlank = 85729
        //  ContestSummary 15 ncards=412121 undervotes=823782 overvotes=1408 isOvervote=0 isBlank = 105412
        //  ContestSummary 16 ncards=14663 undervotes=4813 overvotes=18 isOvervote=0 isBlank = 4831
        //  ContestSummary 17 ncards=191007 undervotes=52961 overvotes=159 isOvervote=0 isBlank = 53120
        //  ContestSummary 18 ncards=410105 undervotes=19700 overvotes=1319 isOvervote=2062 isBlank = 18540
        //  ContestSummary 19 ncards=410105 undervotes=82399 overvotes=145 isOvervote=0 isBlank = 80726
        //  ContestSummary 20 ncards=410105 undervotes=109691 overvotes=3 isOvervote=0 isBlank = 106097
        //  ContestSummary 21 ncards=410105 undervotes=64220 overvotes=183 isOvervote=0 isBlank = 62474
        //  ContestSummary 22 ncards=410105 undervotes=96407 overvotes=179 isOvervote=0 isBlank = 94989
        //  ContestSummary 23 ncards=39257 undervotes=3806 overvotes=46 isOvervote=0 isBlank = 3732
        //  ContestSummary 24 ncards=33672 undervotes=4951 overvotes=69 isOvervote=1 isBlank = 4838
        //  ContestSummary 25 ncards=34405 undervotes=4937 overvotes=69 isOvervote=0 isBlank = 4628
        //  ContestSummary 26 ncards=42846 undervotes=5747 overvotes=23 isOvervote=1 isBlank = 5502
        //  ContestSummary 27 ncards=37091 undervotes=4344 overvotes=129 isOvervote=2 isBlank = 4220
        //  ContestSummary 28 ncards=32274 undervotes=4469 overvotes=147 isOvervote=0 isBlank = 4186
        //  ContestSummary 29 ncards=409893 undervotes=27399 overvotes=89 isOvervote=0 isBlank = 27488
        //  ContestSummary 30 ncards=409893 undervotes=22851 overvotes=107 isOvervote=0 isBlank = 22958
        //  ContestSummary 31 ncards=409893 undervotes=25798 overvotes=61 isOvervote=0 isBlank = 25859
        //  ContestSummary 32 ncards=409893 undervotes=31537 overvotes=80 isOvervote=0 isBlank = 31617
        //  ContestSummary 33 ncards=409893 undervotes=35724 overvotes=90 isOvervote=0 isBlank = 35814
        //  ContestSummary 34 ncards=409893 undervotes=27562 overvotes=80 isOvervote=0 isBlank = 27642
        //  ContestSummary 35 ncards=409893 undervotes=30820 overvotes=214 isOvervote=0 isBlank = 31034
        //  ContestSummary 36 ncards=409893 undervotes=45179 overvotes=161 isOvervote=0 isBlank = 45340
        //  ContestSummary 37 ncards=409893 undervotes=38540 overvotes=174 isOvervote=0 isBlank = 38714
        //  ContestSummary 38 ncards=409893 undervotes=30984 overvotes=91 isOvervote=0 isBlank = 31075
        //  ContestSummary 39 ncards=409515 undervotes=32146 overvotes=72 isOvervote=0 isBlank = 32218
        //  ContestSummary 40 ncards=409515 undervotes=32831 overvotes=47 isOvervote=0 isBlank = 32878
        //  ContestSummary 41 ncards=409515 undervotes=38991 overvotes=158 isOvervote=0 isBlank = 39149
        //  ContestSummary 42 ncards=409515 undervotes=43036 overvotes=152 isOvervote=0 isBlank = 43188
        //  ContestSummary 43 ncards=409515 undervotes=45520 overvotes=141 isOvervote=0 isBlank = 45661
        //  ContestSummary 44 ncards=409515 undervotes=47715 overvotes=129 isOvervote=0 isBlank = 47844
        //  ContestSummary 45 ncards=409515 undervotes=38594 overvotes=97 isOvervote=0 isBlank = 38691
        //  ContestSummary 46 ncards=409515 undervotes=43492 overvotes=81 isOvervote=0 isBlank = 43573
        //  ContestSummary 47 ncards=409515 undervotes=45993 overvotes=63 isOvervote=0 isBlank = 46056
        //  ContestSummary 48 ncards=409515 undervotes=46680 overvotes=50 isOvervote=0 isBlank = 46730
        //  ContestSummary 49 ncards=409515 undervotes=32912 overvotes=114 isOvervote=0 isBlank = 33026
        //  ContestSummary 50 ncards=409515 undervotes=39843 overvotes=97 isOvervote=0 isBlank = 39940
        //  ContestSummary 51 ncards=409515 undervotes=67068 overvotes=137 isOvervote=0 isBlank = 67205
        //  ContestSummary 52 ncards=409515 undervotes=45990 overvotes=93 isOvervote=0 isBlank = 46083
        //  ContestSummary 53 ncards=409515 undervotes=36175 overvotes=91 isOvervote=0 isBlank = 36266
        //
        //cvrSummary MEASURE A (39) has overvotes = 72 not equal to isOvervote = 0
        //cvrSummary MEASURE B (40) has overvotes = 47 not equal to isOvervote = 0
        //cvrSummary MEASURE C (41) has overvotes = 158 not equal to isOvervote = 0
        //cvrSummary MEASURE D (42) has overvotes = 152 not equal to isOvervote = 0
        //cvrSummary MEASURE E (43) has overvotes = 141 not equal to isOvervote = 0
        //cvrSummary MEASURE F (44) has overvotes = 129 not equal to isOvervote = 0
        //cvrSummary MEASURE G (45) has overvotes = 97 not equal to isOvervote = 0
        //cvrSummary MEASURE H (46) has overvotes = 81 not equal to isOvervote = 0
        //cvrSummary MEASURE I (47) has overvotes = 63 not equal to isOvervote = 0
        //cvrSummary MEASURE J (48) has overvotes = 50 not equal to isOvervote = 0
        //cvrSummary MEASURE K (49) has overvotes = 114 not equal to isOvervote = 0
        //cvrSummary MEASURE L (50) has overvotes = 97 not equal to isOvervote = 0
        //cvrSummary MEASURE M (51) has overvotes = 137 not equal to isOvervote = 0
        //cvrSummary MEASURE N (52) has overvotes = 93 not equal to isOvervote = 0
        //cvrSummary MEASURE O (53) has overvotes = 91 not equal to isOvervote = 0
        //cvrSummary PRESIDENT AND VICE PRESIDENT (1) has overvotes = 661 not equal to isOvervote = 0
        //cvrSummary UNITED STATES SENATOR (2) has overvotes = 171 not equal to isOvervote = 0
        //cvrSummary UNITED STATES SENATOR PARTIAL TERM (3) has overvotes = 103 not equal to isOvervote = 0
        //cvrSummary UNITED STATES REPRESENTATIVE, DISTRICT 15 (7) has overvotes = 14 not equal to isOvervote = 0
        //cvrSummary STATE SENATOR, DISTRICT 11 (9) has overvotes = 107 not equal to isOvervote = 0
        //cvrSummary STATE ASSEMBLY MEMBER, DISTRICT 19 (13) has overvotes = 102 not equal to isOvervote = 0
        //cvrSummary MEMBER, BOARD OF EDUCATION (14) has overvotes = 3616 not equal to isOvervote = 0
        //staxContest MEMBER, BOARD OF EDUCATION (14) has blanks = 636146 not equal to cvr summary = 85729
        //cvrSummary TRUSTEE, COMMUNITY COLLEGE BOARD (15) has overvotes = 1408 not equal to isOvervote = 0
        //staxContest TRUSTEE, COMMUNITY COLLEGE BOARD (15) has blanks = 825190 not equal to cvr summary = 105412
        //cvrSummary PROPOSITION 2 (29) has overvotes = 89 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 3 (30) has overvotes = 107 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 4 (31) has overvotes = 61 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 5 (32) has overvotes = 80 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 6 (33) has overvotes = 90 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 32 (34) has overvotes = 80 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 33 (35) has overvotes = 214 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 34 (36) has overvotes = 161 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 35 (37) has overvotes = 174 not equal to isOvervote = 0
        //cvrSummary PROPOSITION 36 (38) has overvotes = 91 not equal to isOvervote = 0
        //cvrSummary MAYOR (18) has overvotes = 1319 not equal to isOvervote = 2062
        //staxContest MAYOR (18) has blanks = 21019 not equal to cvr summary = 18540
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 9 (27) has overvotes = 129 not equal to isOvervote = 2
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 9 (27) has blanks = 4473 not equal to cvr summary = 4220
        //cvrSummary CITY ATTORNEY (19) has overvotes = 145 not equal to isOvervote = 0
        //staxContest CITY ATTORNEY (19) has blanks = 82544 not equal to cvr summary = 80726
        //cvrSummary DISTRICT ATTORNEY (21) has overvotes = 183 not equal to isOvervote = 0
        //staxContest DISTRICT ATTORNEY (21) has blanks = 64403 not equal to cvr summary = 62474
        //cvrSummary SHERIFF (22) has overvotes = 179 not equal to isOvervote = 0
        //staxContest SHERIFF (22) has blanks = 96586 not equal to cvr summary = 94989
        //cvrSummary TREASURER (20) has overvotes = 3 not equal to isOvervote = 0
        //staxContest TREASURER (20) has blanks = 109694 not equal to cvr summary = 106097
        //cvrSummary UNITED STATES REPRESENTATIVE, DISTRICT 11 (5) has overvotes = 77 not equal to isOvervote = 0
        //cvrSummary STATE ASSEMBLY MEMBER, DISTRICT 17 (11) has overvotes = 50 not equal to isOvervote = 0
        //cvrSummary BART BOARD OF DIRECTORS, DISTRICT 9 (17) has overvotes = 159 not equal to isOvervote = 0
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 11 (28) has overvotes = 147 not equal to isOvervote = 0
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 11 (28) has blanks = 4616 not equal to cvr summary = 4186
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 3 (24) has overvotes = 69 not equal to isOvervote = 1
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 3 (24) has blanks = 5020 not equal to cvr summary = 4838
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 1 (23) has overvotes = 46 not equal to isOvervote = 0
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 1 (23) has blanks = 3852 not equal to cvr summary = 3732
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 5 (25) has overvotes = 69 not equal to isOvervote = 0
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 5 (25) has blanks = 5006 not equal to cvr summary = 4628
        //cvrSummary MEMBER, BOARD OF SUPERVISORS, DISTRICT 7 (26) has overvotes = 23 not equal to isOvervote = 1
        //staxContest MEMBER, BOARD OF SUPERVISORS, DISTRICT 7 (26) has blanks = 5770 not equal to cvr summary = 5502
        //cvrSummary BART BOARD OF DIRECTORS, DISTRICT 7 (16) has overvotes = 18 not equal to isOvervote = 0
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
    fun createSF2024sortedCards() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        val cvrCsv = "$topDir/cvrExport.csv"
        createSortedCards(topDir, auditDir, cvrCsv, zip = true, ballotPoolFile = null) // write to "$auditDir/sortedCards.csv"
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