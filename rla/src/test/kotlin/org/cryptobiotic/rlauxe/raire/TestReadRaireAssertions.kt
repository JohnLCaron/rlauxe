package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.workflow.tabulateRaireVotes
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReadRaireAssertions {

    val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
    val raireCvrs = readRaireBallots(cvrFile)
    val cvrs = raireCvrs.cvrs

    // create plausible Nc for each contest
    val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
    val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()

    val rr = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json")
    val raireResults = rr.import(ncs, nps)

    @Test
    fun testRaireAssertions() {
        val contestsUA = tabulateRaireVotes(raireResults.contests, cvrs) // in styleish workflow
        // contestsUA.forEach { it.Nc = it.ncvrs + 2 }

        val prng = Prng(123456789011L)
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        // val cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) } // + phantomCVRs

        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrs)
        }
        val cassertions = contestsUA.first().comparisonAssertions
        assertTrue(cassertions.isNotEmpty())
        cassertions.forEach { cassertion ->
            assertTrue(0.5 < cassertion.avgCvrAssortValue)
            assertTrue(0.0 < cassertion.cmargin)
            cvrs.forEach {
                assertTrue(cassertion.cassorter.assorter.assort(it) in 0.0..cassertion.cassorter.assorter.upperBound())
                if (!it.phantom) assertEquals(cassertion.cassorter.noerror, cassertion.cassorter.bassort(it, it))
                else assertEquals(cassertion.cassorter.noerror/2, cassertion.cassorter.bassort(it, it))
            }
        }
    }
}