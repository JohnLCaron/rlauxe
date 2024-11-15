package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestRcvAssorter {
    val rr = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/334_361_vbm.json")
    val raireResults = rr.import()

    // testing
    fun RaireAssorter.match(winner: Int, loser: Int, winnerType: Boolean, already: List<Int> = emptyList()): Boolean {
        if (this.winner() != winner || this.loser() != loser) return false
        if (winnerType && (this.assertion.assertionType != "WINNER_ONLY")) return false
        if (!winnerType && (this.assertion.assertionType == "WINNER_ONLY")) return false
        if (winnerType) return true
        return already == this.assertion.alreadyEliminated
    }
    
    // SHANGRLA TestAssertion.test_rcv_assorter()
    @Test
    fun testRaireAssorter334() {
        val contest = 334
        val rrContest = raireResults.contests.find { it.name == "334" }!!
        val assorters = rrContest.makeAssorters() // adds assorts to the assertion

        ////            # winner only assertion
        ////            assorter = assertions['334']['5 v 47'].assorter
        ////
        ////            votes = CVR.from_vote({'5': 1, '47': 2})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        ////
        ////             votes = CVR.from_vote({'47': 1, '5': 2})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'3': 1, '6': 2})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'3': 1, '47': 2})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'3': 1, '5': 2})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        val wassorter = assorters.find { it.match(5, 47, true) }
        assertNotNull(wassorter)

        assertEquals(1.0, wassorter.assort(RaireCvr(contest, listOf(5, 47))))
        assertEquals(0.0, wassorter.assort(RaireCvr(contest, listOf(47, 5))))
        assertEquals(0.5, wassorter.assort(RaireCvr(contest, listOf(3, 6))))
        assertEquals(0.0, wassorter.assort(RaireCvr(contest, listOf(3, 47))))
        assertEquals(0.5, wassorter.assort(RaireCvr(contest, listOf(3, 5))))

        ////             # elimination assertion
        ////            assorter = assertions['334']['5 v 3 elim 1 6 47'].assorter
        ////
        ////            votes = CVR.from_vote({'5': 1, '47': 2})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'47': 1, '5': 2})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'6': 1, '1': 2, '3': 3, '5': 4})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'3': 1, '47': 2})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'6': 1, '47': 2})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'6': 1, '47': 2, '5': 3})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        val eassorter = assorters.find { it.match(5, 3, false, listOf(1, 6, 47)) }!!
        assertNotNull(eassorter)

        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(5, 47))))
        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(47, 5))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(6, 1, 3, 5))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(3, 47))))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf())))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf(6, 47))))
        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(6, 47, 5))))
    }

    @Test
    fun testRaireAssorter361() {
        val contest = 361
        val rrContest = raireResults.contests.find { it.name == "361"}!!
        val assorters = rrContest.makeAssorters() // adds assorts to the assertion
        val wassorter = assorters.find { it.match(28, 50, true) }
        assertNotNull(wassorter)
        
        ////            # winner-only assertion
        ////            assorter = assertions['361']['28 v 50'].assorter
        ////
        ////            votes = CVR.from_vote({'28': 1, '50': 2})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'28': 1})
        ////            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'50': 1})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'27': 1, '28': 2})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'50': 1, '28': 2})
        ////            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({'27': 1, '26': 2})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        ////
        ////            votes = CVR.from_vote({})
        ////            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'

        assertEquals(1.0, wassorter.assort(RaireCvr(contest, listOf(28, 50))))
        assertEquals(1.0, wassorter.assort(RaireCvr(contest, listOf(28))))
        assertEquals(0.0, wassorter.assort(RaireCvr(contest, listOf(50))))
        assertEquals(0.5, wassorter.assort(RaireCvr(contest, listOf(27, 28))))
        assertEquals(0.0, wassorter.assort(RaireCvr(contest, listOf(50, 28))))
        assertEquals(0.5, wassorter.assort(RaireCvr(contest, listOf(27, 26))))
        assertEquals(0.5, wassorter.assort(RaireCvr(contest, listOf())))

        //            # elimination assertion
//            assorter = assertions['361']['27 v 26 elim 28 50'].assorter
//
//            votes = CVR.from_vote({'27': 1})
//            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'50': 1, '27': 2})
//            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'28': 1, '50': 2, '27': 3})
//            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'28': 1, '27': 2, '50': 3})
//            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'26': 1})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'50': 1, '26': 2})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'28': 1, '50': 2, '26': 3})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'28': 1, '26': 2, '50': 3})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'50': 1})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'50': 1, '28': 2})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'28': 1, '50': 2})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'

        val eassorter = assorters.find { it.match(27, 26, false, listOf(28, 50)) }
        assertNotNull(eassorter)

        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(27))))
        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(50, 27))))
        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(28, 50, 27))))
        assertEquals(1.0, eassorter.assort(RaireCvr(contest, listOf(28, 27, 50))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(26))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(50, 26))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(28, 50, 26))))
        assertEquals(0.0, eassorter.assort(RaireCvr(contest, listOf(28, 26, 50))))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf(50))))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf())))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf(50, 28))))
        assertEquals(0.5, eassorter.assort(RaireCvr(contest, listOf(28, 50))))
    }
}