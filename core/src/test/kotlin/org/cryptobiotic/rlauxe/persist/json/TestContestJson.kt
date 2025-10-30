package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class TestContestJson {

    @Test
    fun testReadContestsJsonFile() {
        val filename = "src/test/data/contests.json"
        val contestsResults = readContestsJsonFile(filename)
        val contests = if (contestsResults is Ok) contestsResults.unwrap()
            else throw RuntimeException("Cannot read contests from ${filename} err = $contestsResults")
        contests.forEach{
            println(" $it")
        }
    }

    @Test
    fun testRoundtrip() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2), SocialChoiceFunction.PLURALITY)
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Ncast=211, )

        val contestUAc = ContestUnderAudit(contest, isComparison = true).addClcaAssertionsFromReportedMargin()
        contestUAc.preAuditStatus = TestH0Status.ContestMisformed
        assertTrue(contestUAc.pollingAssertions.isNotEmpty())
        assertTrue(contestUAc.clcaAssertions.isNotEmpty())

        val json = contestUAc.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, contestUAc)
        assertTrue(roundtrip.equals(contestUAc))
    }
}