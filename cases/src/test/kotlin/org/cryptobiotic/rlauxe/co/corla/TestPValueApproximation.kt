package org.cryptobiotic.rlauxe.co.corla

import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPValueApproximation {

    @Test
    fun checkTable3() {
        var count = 0
        table3s.forEach {
            assertEquals(it.expect, pValue(it), .001, "$count: $it")
            count++
        }
    }

    @Test
    fun problemWithTable() {
        val it = table3s[11]
        assertEquals(it.expect, pValue(it), .001)
    }

    @Test
    fun problem() {
        // Boulder 2026
        // |         field |                                  value |                                               description |
        //| ------------- | -------------------------------------- | --------------------------------------------------------- |
        //|        target |                                    N/A |            if targeted for audit, from round.audit_reason |
        //|     NCounties |                                Boulder |            number of counties, or county name if only one |
        //|          name | Boulder County Court Judge - Malkinson |                                              contest name |
        //| marginInVotes |                                  63745 |                                     from round.min_margin |
        //|      corlaPop |                                 354401 |                                   round.ballot_card_count |
        //|       corlaNc |                                 176996 |                      from round.contest_ballot_card_count |
        //|       recount |                     0.7024474638280054 |                              vote margin / winner's votes |
        //|        margin |                    0.17986687396480258 |                                    vote margin / corlaPop |
        //|    estSamples |                                     41 |                     from round.estimated_samples_to_audit |
        //|    countyMvrs |                                     89 |                              count of mvrs in this county |
        //| statewideMvrs |                                      7 | count of statewide mvrs over all this countest's counties |
        //|     extraMvrs |                                     55 |                   countyMvrs + statewideMvrs - estSamples |
        //|     corlaRisk |                    0.00447294491266804 |                                             risk achieved |
        //|     riskLimit |                                   0.03 |                                     from round.risk_limit |
        //
        //CanonicalContest(this, "Boulder County Court Judge - Malkinson", listOf("Yes","No",)).addCounties(listOf("Boulder",))
        //                   Yes : 90747
        //                    No : 27002
        var n = 89 + 7
        var margin = 0.17986687396480258
        val gamma = 1.03905
        var noerror = pValueApproximation(n, margin, gamma, 0, 0)
        var risk = pValueApproximation(n, margin, gamma, 1, 0)
        println("Boulder risk $risk - $noerror noerror = ${-noerror + risk}")

        // |         field |                                     value |                                               description |
        //| ------------- | ----------------------------------------- | --------------------------------------------------------- |
        //|        target |                                       N/A |            if targeted for audit, from round.audit_reason |
        //|     NCounties |                                San Miguel |            number of counties, or county name if only one |
        //|          name | San Miguel County Commissioner-District 2 |                                              contest name |
        //| marginInVotes |                                       240 |                                     from round.min_margin |
        //|      corlaPop |                                      4219 |                                   round.ballot_card_count |
        //|       corlaNc |                                      4215 |                      from round.contest_ballot_card_count |
        //|       recount |                       0.11406844106463879 |                              vote margin / winner's votes |
        //|        margin |                       0.05688551789523584 |                                    vote margin / corlaPop |
        //|    estSamples |                                       129 |                     from round.estimated_samples_to_audit |
        //|    countyMvrs |                                        88 |                              count of mvrs in this county |
        //| statewideMvrs |                                         0 | count of statewide mvrs over all this countest's counties |
        //|     extraMvrs |                                       -41 |                   countyMvrs + statewideMvrs - estSamples |
        //|     corlaRisk |                                       1.0 |                                             risk achieved |
        //|     riskLimit |                                      0.03 |                                     from round.risk_limit |
        //
        //CanonicalContest(this, "San Miguel County Commissioner-District 2", listOf("Paul Reich","Dylan D. Brooks",)).addCounties(listOf("San Miguel",))
        //       Dylan D. Brooks : 2104
        //            Paul Reich : 1864

        n = 88
        margin = 0.05688551789523584
        noerror = pValueApproximation(n, margin, gamma, 0, 0)
        risk = pValueApproximation(n, margin, gamma, 1, 0)
        println("San Miguel risk $risk - $noerror noerror = ${-noerror + risk}")

        repeat(20) {
            n = 100
            margin = .005 + it * .01
            noerror = pValueApproximation(n, margin, gamma, 0, 0)
            risk = pValueApproximation(n, margin, gamma, 1, 0)
            println("margin=${dfn(margin, 3)} risk $risk - $noerror noerror; extra = ${-noerror + risk}")
        }

    }
}

fun pValue(row: T3row) = pValueApproximation(row.n, row.mu, row.gamma, row.n2, row.n1)

// Table 3 of "Super SImple"
// n is the number of samples. interesting that N isnt needed
// mu is the dilutedMargin
data class T3row(val n: Int, val mu: Double, val gamma: Double, val n1: Int, val n2: Int, val expect: Double)

val table3s = listOf(
    T3row(500, .02, 1.01, 0, 0, .007),
    T3row(500, .02, 1.01, 1, 0, .014),
    T3row(500, .02, 1.01, 2, 0, .027),
    T3row(500, .02, 1.01, 3, 0, .054),

    T3row(500, .02, 1.01, 0, 0, .007),
    T3row(500, .02, 1.01, 0, 1, .698),
    T3row(500, .02, 1.01, 0, 2, 1.0),
    T3row(500, .02, 1.01, 0, 3, 1.0),

    T3row(500, .02, 1.10, 0, 0, .01),
    T3row(500, .02, 1.10, 1, 0, .019),
    T3row(500, .02, 1.10, 2, 0, .035),
    T3row(500, .02, 1.10, 3, 0, .064),

    T3row(500, .02, 1.10, 0, 0, .01),
    T3row(500, .02, 1.10, 0, 1, .114),
    T3row(500, .02, 1.10, 0, 2, 1.0),
    T3row(500, .02, 1.10, 0, 3, 1.0),


    T3row(750, .02, 1.01, 0, 0, .001),
    T3row(750, .02, 1.01, 1, 0, .001),
    T3row(750, .02, 1.01, 2, 0, .002),
    T3row(750, .02, 1.01, 3, 0, .004),
    T3row(750, .02, 1.01, 4, 0, .009),
    T3row(750, .02, 1.01, 5, 0, .017),

    T3row(750, .02, 1.01, 0, 0, .001),
    T3row(750, .02, 1.01, 0, 1, .058),
    T3row(750, .02, 1.01, 0, 2, 1.0),
    T3row(750, .02, 1.01, 0, 3, 1.0),
    T3row(750, .02, 1.01, 0, 4, 1.0),
    T3row(750, .02, 1.01, 0, 5, 1.0),

    T3row(750, .02, 1.10, 0, 0, .001),
    T3row(750, .02, 1.10, 1, 0, .002),
    T3row(750, .02, 1.10, 2, 0, .004),
    T3row(750, .02, 1.10, 3, 0, .007),
    T3row(750, .02, 1.10, 4, 0, .012),
    T3row(750, .02, 1.10, 5, 0, .022),

    T3row(750, .02, 1.10, 0, 0, .001),
    T3row(750, .02, 1.10, 0, 1, .012),
    T3row(750, .02, 1.10, 0, 2, .128),
    T3row(750, .02, 1.10, 0, 3, 1.0),
    T3row(750, .02, 1.10, 0, 4, 1.0),
    T3row(750, .02, 1.10, 0, 5, 1.0),


    T3row(750, .01, 1.01, 0, 0, .024),
    T3row(750, .01, 1.01, 1, 0, .048),
    T3row(750, .01, 1.01, 2, 0, .095),

    T3row(750, .01, 1.01, 0, 0, .024),
    T3row(750, .01, 1.01, 0, 1, 1.0),
    T3row(750, .01, 1.01, 0, 2, 1.0),

    T3row(750, .01, 1.10, 0, 0, .033),
    T3row(750, .01, 1.10, 1, 0, .060),
    T3row(750, .01, 1.10, 2, 0, .11),

    T3row(750, .01, 1.10, 0, 0, .033),
    T3row(750, .01, 1.10, 0, 1, .361),
    T3row(750, .01, 1.10, 0, 2, 1.0),


    T3row(1000, .01, 1.01, 0, 0, .007),
    T3row(1000, .01, 1.01, 1, 0, .014),
    T3row(1000, .01, 1.01, 2, 0, .027),
    T3row(1000, .01, 1.01, 3, 0, .054),

    T3row(1000, .01, 1.01, 0, 0, .007),
    T3row(1000, .01, 1.01, 0, 1, .706),
    T3row(1000, .01, 1.01, 0, 2, 1.0),
    T3row(1000, .01, 1.01, 0, 3, 1.0),

    T3row(1000, .01, 1.10, 0, 0, .011),
    T3row(1000, .01, 1.10, 1, 0, .019),
    T3row(1000, .01, 1.10, 2, 0, .035),
    T3row(1000, .01, 1.10, 3, 0, .065),

    T3row(1000, .01, 1.10, 0, 0, .011),
    T3row(1000, .01, 1.10, 0, 1, .116),
    T3row(1000, .01, 1.10, 0, 2, 1.0),
    T3row(1000, .01, 1.10, 0, 3, 1.0),


    T3row(1000, .005, 1.01, 0, 0, .084),
    T3row(1000, .005, 1.10, 0, 0, .103),


    T3row(1250, .005, 1.01, 0, 0, .045),
    T3row(1250, .005, 1.01, 1, 0, .089),

    T3row(1250, .005, 1.01, 0, 0, .045),
    T3row(1250, .005, 1.01, 0, 1, 1.0),

    T3row(1250, .005, 1.10, 0, 0, .058),
    T3row(1250, .005, 1.10, 1, 0, .107),

    T3row(1250, .005, 1.10, 0, 0, .058),
    T3row(1250, .005, 1.10, 0, 1, .64),


    T3row(1500, .005, 1.01, 0, 0, .024),
    T3row(1500, .005, 1.01, 1, 0, .048),
    T3row(1500, .005, 1.01, 2, 0, .095),

    T3row(1500, .005, 1.01, 0, 0, .024),
    T3row(1500, .005, 1.01, 0, 1, 1.0),
    T3row(1500, .005, 1.01, 0, 2, 1.0),

    T3row(1500, .005, 1.10, 0, 0, .033),
    T3row(1500, .005, 1.10, 1, 0, .060),
    T3row(1500, .005, 1.10, 2, 0, .111),

    T3row(1500, .005, 1.10, 0, 0, .033),
    T3row(1500, .005, 1.10, 0, 1, .362),
    T3row(1500, .005, 1.10, 0, 2, 1.0),


    T3row(2000, .005, 1.01, 0, 0, .007),
    T3row(2000, .005, 1.01, 1, 0, .014),
    T3row(2000, .005, 1.01, 2, 0, .028),
    T3row(2000, .005, 1.01, 3, 0, .055),

    T3row(2000, .005, 1.01, 0, 0, .007),
    T3row(2000, .005, 1.01, 0, 1, .711),
    T3row(2000, .005, 1.01, 0, 2, 1.0),
    T3row(2000, .005, 1.01, 0, 3, 1.0),

    T3row(2000, .005, 1.10, 0, 0, .011),
    T3row(2000, .005, 1.10, 1, 0, .019),
    T3row(2000, .005, 1.10, 2, 0, .035),
    T3row(2000, .005, 1.10, 3, 0, .065),

    T3row(2000, .005, 1.10, 0, 0, .011),
    T3row(2000, .005, 1.10, 0, 1, .116),
    T3row(2000, .005, 1.10, 0, 2, 1.0),
    T3row(2000, .005, 1.10, 0, 3, 1.0),
)

