package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.margin2mean
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
            contest.makeClcaAssertions(cvrs)
        }
        val cassertions = contestsUA.first().clcaAssertions
        assertTrue(cassertions.isNotEmpty())
        cassertions.forEach { cassertion ->
            assertTrue(0.5 < margin2mean(cassertion.assorter.reportedMargin()))
            assertTrue(0.0 < cassertion.assorter.reportedMargin())
            cvrs.forEach {
                assertTrue(cassertion.cassorter.assorter().assort(it) in 0.0..cassertion.cassorter.assorter().upperBound())
                if (!it.phantom) assertEquals(cassertion.cassorter.noerror(), cassertion.cassorter.bassort(it, it))
                else assertEquals(cassertion.cassorter.noerror()/2, cassertion.cassorter.bassort(it, it))
            }
        }
    }
}

///////////////////////////////////////////////////////////////////////

// TODO seems wrong
fun tabulateRaireVotes(rcontests: List<RaireContestUnderAudit>, cvrs: List<Cvr>): List<ContestUnderAudit> {
    if (rcontests.isEmpty()) return emptyList()

    val allVotes = mutableMapOf<Int, MutableMap<Int, Int>>()
    val ncvrs = mutableMapOf<Int, Int>()
    for (cvr in cvrs) {
        for ((conId, conVotes) in cvr.votes) {
            val accumVotes = allVotes.getOrPut(conId) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
        for (conId in cvr.votes.keys) {
            val accum = ncvrs.getOrPut(conId) { 0 }
            ncvrs[conId] = accum + 1
        }
    }
    return allVotes.keys.map { conId ->
        val rcontestUA = rcontests.find { it.id == conId }
        if (rcontestUA == null) throw RuntimeException("no contest for contest id= $conId")
        val nc = ncvrs[conId]!!
        val accumVotes = allVotes[conId]!!
        // require(checkEquivilentVotes(contestUA.contest.votes, accumVotes))
        rcontestUA
    }
}