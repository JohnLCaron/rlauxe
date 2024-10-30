package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.raire.import
import org.cryptobiotic.rlauxe.core.raire.addAssorters
import org.cryptobiotic.rlauxe.core.raire.readRaireResults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

//     def test_rcv_assorter(self):
//        import json
//        with open('./tests/core/data/334_361_vbm.json') as fid:
//            data = json.load(fid)
//            AvB = Contest.from_dict({'id': 'AvB',
//                     'name': 'AvB',
//                     'risk_limit': 0.05,
//                     'cards': 10**4,
//                     'choice_function': Contest.SOCIAL_CHOICE_FUNCTION.IRV,
//                     'n_winners': 1,
//                     'test': NonnegMean.alpha_mart,
//                     'use_style': True
//                })
//            assertions = {}
//            for audit in data['audits']:
//                cands = [audit['winner']]
//                for elim in audit['eliminated']:
//                    cands.append(elim)
//                all_assertions = Assertion.make_assertions_from_json(contest=AvB, candidates=cands,
//                                                                     json_assertions=audit['assertions'])
//                assertions[audit['contest']] = all_assertions
//
//            # winner only assertion
//            assorter = assertions['334']['5 v 47'].assorter
//
//            votes = CVR.from_vote({'5': 1, '47': 2})
//            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
//
//             votes = CVR.from_vote({'47': 1, '5': 2})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'3': 1, '6': 2})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'3': 1, '47': 2})
//            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
//
//            votes = CVR.from_vote({'3': 1, '5': 2})
//            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
//

class TestRcvAssorter {
    @Test
    fun testRaireAssorter() {
        val rr =
            readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/334_361_vbm.json")
        val raireResults = rr.import()
        // println(raireResults.show())
        val contest = 334
        val rrContest = raireResults.contests.find { it.name == "334"}!!
        rrContest.addAssorters() // adds assorts to the assertion

        // winner only assertion
        val wassertion = rrContest.assertions.find { it.match(5, 47, true) }!!
        println(wassertion.assorter)

        assertEquals(1.0, wassertion.assorter!!.assort(RaireCvr(contest, listOf(5, 47))))
        assertEquals(0.0, wassertion.assorter!!.assort(RaireCvr(contest, listOf(47, 5))))
        assertEquals(0.5, wassertion.assorter!!.assort(RaireCvr(contest, listOf(3, 6))))
        assertEquals(0.0, wassertion.assorter!!.assort(RaireCvr(contest, listOf(3, 47))))
        assertEquals(0.5, wassertion.assorter!!.assort(RaireCvr(contest, listOf(3, 5))))

        // elimination assertion
        //            assorter = assertions['334']['5 v 3 elim 1 6 47'].assorter
        val eassertion = rrContest.assertions.find { it.match(5, 3, false, listOf(1, 6, 47)) }!!
        println(eassertion.assorter)

        assertEquals(1.0, eassertion.assorter!!.assort(RaireCvr(contest, listOf(5, 47))))
        assertEquals(1.0, eassertion.assorter!!.assort(RaireCvr(contest, listOf(47, 5))))
        assertEquals(0.0, eassertion.assorter!!.assort(RaireCvr(contest, listOf(6, 1, 3, 5))))
        assertEquals(0.0, eassertion.assorter!!.assort(RaireCvr(contest, listOf(3, 47))))
        assertEquals(0.5, eassertion.assorter!!.assort(RaireCvr(contest, listOf())))
        assertEquals(0.5, eassertion.assorter!!.assort(RaireCvr(contest, listOf(6, 47))))
        assertEquals(1.0, eassertion.assorter!!.assort(RaireCvr(contest, listOf(6, 47, 5))))

        //
        //            votes = CVR.from_vote({'5': 1, '47': 2})
        //            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({'47': 1, '5': 2})
        //            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({'6': 1, '1': 2, '3': 3, '5': 4})
        //            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({'3': 1, '47': 2})
        //            assert assorter.assort(votes) == 0, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({})
        //            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({'6': 1, '47': 2})
        //            assert assorter.assort(votes) == 0.5, f'{assorter.assort(votes)=}'
        //
        //            votes = CVR.from_vote({'6': 1, '47': 2, '5': 3})
        //            assert assorter.assort(votes) == 1, f'{assorter.assort(votes)=}'
    }
}