package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.margin2mean

interface OneAuditContestBuilderIF {
    val contestId: Int
    fun poolTotalCards(): Int // total cards in all pools for this contest
    fun expectedPoolNCards(): Int // expected total pool cards for this contest, making assumptions about missing undervotes
    fun adjustPoolInfo(cardPools: List<OneAuditPoolIF>)
}

private const val debug = false

// the contests share the audit pools, so its convenient to process them all at once?
fun makeOneAuditContests(
    wantContests: List<ContestIF>, // the contests you want to audit
    npopMap: Map<Int,Int>,  // contestId -> Npop
    cardPools: List<OneAuditPoolIF>,
): List<ContestWithAssertions> {

    val contestsUA = wantContests.filter{ !it.isIrv() }.map { contest ->
        val cua = ContestWithAssertions(contest, true, NpopIn=npopMap[contest.id]).addStandardAssertions()
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
// this also repalces the clcaAssertions with ones that use ClcaAssorterOneAudit which contain the pool assorter averages
fun setPoolAssorterAverages(
    oaContests: List<ContestWithAssertions>,
    pools: List<OneAuditPoolIF>, // poolId -> pool
) {
    val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)

    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.filter { !it.isIrv}. forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.assertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            pools.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val regVotes = cardPool.regVotes()[oaContest.id]!! // TODO assumes not IRV
                    if (cardPool.ncards() > 0) {
                        // note: using cardPool.ncards(), this is the diluted count
                        val poolMargin = assertion.assorter.calcMarginFromRegVotes(regVotes.votes, cardPool.ncards())
                        assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                    }
                }
            }
            val oaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter,
                dilutedMargin = assertion.assorter.dilutedMargin(),
                poolAverages = AssortAvgsInPools(assortAverages))

            oaAssorter.oaAssortRates = oneAuditErrorsFromPools.oaErrorRates(oaContest, oaAssorter)

            ClcaAssertion(assertion.info, oaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}
