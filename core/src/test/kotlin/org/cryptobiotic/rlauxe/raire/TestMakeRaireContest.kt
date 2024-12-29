package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.util.df
import kotlin.collections.forEach
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class TestMakeRaireContest {

    @Test
    fun testMakeRaireContest() {
        val (rcontest, cvrs) = makeRaireContest(N=20000, margin=.05)
    }
}
