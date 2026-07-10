package org.cryptobiotic.rlauxe.oneaudit

import au.org.democracydevelopers.raire.irv.Votes
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.irv.RaireAssorter
import org.cryptobiotic.rlauxe.util.margin2mean

//// common code for building OneAudit contests

private const val debug = false

// the contests share the card pools, so its convenient to process them all at once
fun makeOneAuditContests(
    wantContests: List<ContestIF>, // the contests you want to audit
    npopMap: Map<Int,Int>,  // contestId -> Npop
    cardPools: List<CardPoolIF>,
    hasStyle: Boolean,
): List<ContestWithAssertions> {

    val contestsUA = wantContests.filter{ !it.isIrv() }.map { contest ->
        val cua = ContestWithAssertions(contest, true, NpopIn=npopMap[contest.id], hasStyle = hasStyle).addStandardAssertions()
        if (contest is DHondtContest) {
            cua.addAssertionsFromAssorters(contest.assorters)
        } else {
            cua.addStandardAssertions()
        }
        cua
    }

    // Its the OA assorters that make this a OneAudit contest
    setPoolAssorterAverages(contestsUA, cardPools)
    return contestsUA
}

// use dilutedMargin to set the pool assorter averages. can only use for non-IRV contests because calcMargin(regVotes)
// this also repalaces the clcaAssertions with ones that use ClcaAssorterOneAudit which contain the pool assorter averages
fun setPoolAssorterAverages(
    oaContests: List<ContestWithAssertions>,
    pools: List<CardPoolIF>, // poolId -> pool
) {
    val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)

    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.assertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            pools.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val tab = cardPool.contestTab(oaContest.id)!! // Irv not done here
                    if (cardPool.ncards() > 0) {
                        val poolMargin = if (oaContest.isIrv) {
                            val tab = cardPool.contestTab(oaContest.id)!! // assumes that the cardPool has the irvVotes
                            val irvVotes: Votes = tab.irvVotes.makeVotes(oaContest.ncandidates)
                            val raireAssorter = assertion.assorter as RaireAssorter
                            raireAssorter.calcMarginFromVotes(irvVotes, cardPool.ncards())
                        } else {
                            assertion.assorter.calcMarginFromRegVotes(tab.votes, cardPool.ncards())
                        }
                        assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                    }
                }
            }
            val oaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, poolAverages = AssortAvgsInPools(assortAverages))
            oaAssorter.oaAssortRates = oneAuditErrorsFromPools.oaErrorRates(oaContest, oaAssorter)
            ClcaAssertion(assertion.info, oaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}
