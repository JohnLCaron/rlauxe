package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.rairejson.RaireCvrs
import org.cryptobiotic.rlauxe.rairejson.import
import org.cryptobiotic.rlauxe.rairejson.readRaireBallotsCsv
import org.cryptobiotic.rlauxe.rairejson.readRaireResultsJson
import org.cryptobiotic.rlauxe.util.margin2mean
import org.junit.jupiter.api.Assertions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// reading raire output json format - but margin not reported here, so cant actually use....
class TestReadRaireBallotsCsv {
    val cvrFile = "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
    val raireCvrs = readRaireBallotsCsv(cvrFile)
    val cvrs = raireCvrs.cvrs

    // create plausible Nc for each contest
    val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
    val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()

    val rr = readRaireResultsJson("src/test/data/raire/SFDA2019/SF2019Nov8Assertions.json")
    val raireResults = rr.import(ncs, nps)

    // @Test TODO failing
    fun testRaireAssertions() {
        val contestsUA = raireResults.contests // TODO incorporate into reading ??
        tabulateRaireMargins(contestsUA, cvrs)

        val cassertions = contestsUA.first().clcaAssertions
        assertTrue(cassertions.isNotEmpty())
        cassertions.forEach { cassertion ->
            assertTrue(0.0 < cassertion.assorter.dilutedMargin())
            assertTrue(0.5 < margin2mean(cassertion.assorter.dilutedMargin()))
            cvrs.forEach {
                assertTrue(cassertion.cassorter.assorter().assort(it) in 0.0..cassertion.cassorter.assorter().upperBound())
                if (!it.phantom) assertEquals(cassertion.cassorter.noerror(), cassertion.cassorter.bassort(it, it, hasStyle=true))
                else assertEquals(cassertion.cassorter.noerror()/2, cassertion.cassorter.bassort(it, it, hasStyle=true))
            }
        }
    }

    @Test
    fun testReadAspenCityCouncilCvrs() {
        val cvrFile = "src/test/data/raire/ballotCsv/Aspen_2009_CityCouncil.raire"
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        Assertions.assertEquals(1, raireCvrs.contests.size)
        val contest = raireCvrs.contests.first()
        Assertions.assertEquals(1, contest.contestNumber)
        Assertions.assertEquals(11, contest.candidates.size)
        Assertions.assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), contest.candidates)
        Assertions.assertEquals(8, contest.winner)
        Assertions.assertEquals(2487, contest.ncvrs)
    }

    // 1
    //Contest,339,4,15,16,17,18
    //339,99813_1_1,17
    //339,99813_1_3,16
    //339,99813_1_6,18,17,15,16

    @Test
    fun testReadSfdaCvrs() { // ??
        val cvrFile = "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        Assertions.assertEquals(1, raireCvrs.contests.size)
        val contest = raireCvrs.contests.first()
        Assertions.assertEquals(339, contest.contestNumber)
        Assertions.assertEquals(4, contest.candidates.size)
        Assertions.assertEquals(listOf(15, 16, 17, 18), contest.candidates)
        Assertions.assertEquals(-1, contest.winner)
        Assertions.assertEquals(146662, contest.ncvrs)
    }

    @Test
    fun testReadAspenCvrs() {
        val dataDir = "src/test/data/raire/ballotCsv"
        val dataDirFile = File(dataDir)
        dataDirFile.listFiles().forEach {
            if (!it.isDirectory) {
                println(it.path)
                testReadRaireCvrs(it.path)
            }
        }
    }

    fun testReadRaireCvrs(cvrFile: String) {
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        raireCvrs.contests.forEach {
            println("  ${it.show()}")
        }
    }
}

///////////////////////////////////////////////////////////////////////

// TODO make this part of readRaireResultsJson
fun tabulateRaireMargins(rcontests: List<RaireContestUnderAudit>, cvrs: List<Cvr>) {
    if (rcontests.isEmpty()) return

    // we have to calculate the margin ourselves, since they are not in the RaireResults file (!)
    rcontests.forEach { rcontest ->
        val vc = VoteConsolidator()
        cvrs.forEach {
            val votes = it.votes[rcontest.id]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val startingVotes = vc.makeVoteList()

        rcontest.rassertions.forEach { rassertion ->
            if (rassertion.assertionType == RaireAssertionType.irv_elimination) {
                val voteSeqElim = VoteSequences.eliminate(startingVotes, rassertion.remaining(rcontest.candidates))
                val nenChoices = voteSeqElim.nenFirstChoices(rassertion.winnerId, rassertion.loserId)
                val margin = voteSeqElim.margin(rassertion.winnerId, rassertion.loserId, nenChoices)
                //println("${rassertion.winnerId}, ${rassertion.loserId} == $margin")
                assertTrue(margin > 0)
                rassertion.marginInVotes = margin
            } else {
                val voteSeq = VoteSequences(startingVotes)
                val nebChoices = voteSeq.nebFirstChoices(rassertion.winnerId, rassertion.loserId)
                val margin = voteSeq.margin(rassertion.winnerId, rassertion.loserId, nebChoices)
                assertTrue(margin > 0)
                rassertion.marginInVotes = margin
            }
            // rcontest.addClcaAssertionsFromReportedMargin()
        }
    }
}