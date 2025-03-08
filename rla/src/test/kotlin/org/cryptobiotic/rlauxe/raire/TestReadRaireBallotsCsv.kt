package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReadRaireBallotsCsv {
    val cvrFile = "/home/stormy/dev/github/rla/rlauxe/rla/src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
    val raireCvrs = readRaireBallotsCsv(cvrFile)
    val cvrs = raireCvrs.cvrs

    // create plausible Nc for each contest
    val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
    val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()

    val rr = readRaireResultsJson("/home/stormy/dev/github/rla/rlauxe/rla/src/test/data/raire/SFDA2019/SF2019Nov8Assertions.json")
    val raireResults = rr.import(ncs, nps)

    @Test
    fun testRaireAssertions() {
        val contestsUA = raireResults.contests // TODO incorporate into reading ??
        tabulateRaireMargins(contestsUA, cvrs)

        val prng = Prng(123456789011L)
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        // val cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) } // + phantomCVRs

        contestsUA.forEach { contestUA ->
            contestUA.makeClcaAssertions(cvrs)
        }
        val cassertions = contestsUA.first().clcaAssertions
        assertTrue(cassertions.isNotEmpty())
        cassertions.forEach { cassertion ->
            assertTrue(0.0 < cassertion.assorter.reportedMargin())
            assertTrue(0.5 < margin2mean(cassertion.assorter.reportedMargin()))
            cvrs.forEach {
                assertTrue(cassertion.cassorter.assorter().assort(it) in 0.0..cassertion.cassorter.assorter().upperBound())
                if (!it.phantom) assertEquals(cassertion.cassorter.noerror(), cassertion.cassorter.bassort(it, it))
                else assertEquals(cassertion.cassorter.noerror()/2, cassertion.cassorter.bassort(it, it))
            }
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
                val nenChoices = voteSeqElim.nenChoices(rassertion.winnerId, rassertion.loserId)
                val margin = voteSeqElim.margin(rassertion.winnerId, rassertion.loserId, nenChoices)
                //println("${rassertion.winnerId}, ${rassertion.loserId} == $margin")
                assertTrue(margin > 0)
                rassertion.marginInVotes = margin
            } else {
                val voteSeq = VoteSequences(startingVotes)
                val nebChoices = voteSeq.nebChoices(rassertion.winnerId, rassertion.loserId)
                val margin = voteSeq.margin(rassertion.winnerId, rassertion.loserId, nebChoices)
                assertTrue(margin > 0)
                rassertion.marginInVotes = margin
            }
            rcontest.makeClcaAssertions(cvrs)
        }
    }
}