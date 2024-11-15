package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlaux.core.raire.readRaireCvrs
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.makePhantomCvrs
import org.cryptobiotic.rlauxe.sampling.tabulateRaireVotes
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRaireAssertions {
    val rr = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json")
    val raireResults = rr.import()

    val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
    val raireCvrs = readRaireCvrs(cvrFile)
    val cvrs = raireCvrs.contests.first().cvrs

    @Test
    fun testRaireAssertions() {
        val contestsUA = tabulateRaireVotes(raireResults.contests, cvrs) // in styleish workflow
        contestsUA.forEach { it.upperBound = it.ncvrs + 2 }

        val prng = Prng(123456789011L)
        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val cvrsUA = cvrs.map { CvrUnderAudit(it, false, prng.next()) } + phantomCVRs

        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrsUA)
        }
        val cassertions = contestsUA.first().comparisonAssertions
        assertTrue(cassertions.isNotEmpty())
        cassertions.forEach { cassertion ->
            assertTrue(0.5 < cassertion.avgCvrAssortValue)
            assertTrue(0.0 < cassertion.margin)
            cvrsUA.forEach {
                assertTrue(cassertion.assorter.assorter.assort(it) in 0.0..cassertion.assorter.assorter.upperBound())
                if (!it.phantom) assertEquals(cassertion.assorter.noerror, cassertion.assorter.bassort(it, it))
                else assertEquals(cassertion.assorter.noerror/2, cassertion.assorter.bassort(it, it))
            }
        }
    }
}