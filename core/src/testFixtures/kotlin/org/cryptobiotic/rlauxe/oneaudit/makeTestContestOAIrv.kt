package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssertionType
import org.cryptobiotic.rlauxe.raire.RaireContest

fun makeTestContestOAIrv(): OAIrvContestUA {

        val info = ContestInfo(
            "TestOneAuditIrvContest",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV,
            voteForN = 6,
        )
        val Nc = 2120
        val Np = 2
        val rcontest = RaireContest(info, winners = listOf(1), Nc = Nc, Ncast = Nc - Np, undervotes=0)

        // where did these come from ??
        val assert1 = RaireAssertion(1, 0, 0.0, 42, RaireAssertionType.winner_only)
        val assert2 = RaireAssertion(1, 2, 0.0,422, RaireAssertionType.irv_elimination,
            listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3))

        val oaIrv =  OAIrvContestUA(rcontest, true, listOf(assert1, assert2))

        // add pools

        // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
        //val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
        //val cvrNc = 200
        // val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
        val pools = emptyList<CardPoolIF>()

    // TODO
        val cardPool = CardPoolFromCvrs(
            "noCvr",
            42, // poolId
            infos = mapOf(info.id to info),
        )

    val votesNoCvr = mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0)

        //voteTotals = mapOf(info.id to votesNoCvr)
        val cardPools = listOf(cardPool)

        val clcaAssertions = oaIrv.pollingAssertions.map { assertion ->
            val passort = assertion.assorter
            val pairs = pools.map { pool ->
                Pair(pool.poolId, 0.55)
            }
            val poolAvgs = AssortAvgsInPools(pairs.toMap())
            val clcaAssertion = OneAuditClcaAssorter(assertion.info, passort, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssertion)
        }
        oaIrv.clcaAssertions = clcaAssertions

        return oaIrv
    }