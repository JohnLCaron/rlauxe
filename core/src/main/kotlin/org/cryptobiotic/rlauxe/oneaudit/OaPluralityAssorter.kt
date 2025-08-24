package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.util.margin2mean

// not used but for testing

// OneAuditComparisonAssorter for contest St. Vrain and Left Hand Water Conservancy District Ballot Issue 7C (64)
//  assorter= winner=0 loser=1 reportedMargin=0.6556 reportedMean=0.8278
//  cvrAssortMargin=0.6555896614618087 noerror=0.7438205221534695 upperBound=1.487641044306939 assortValueFromCvrs=null
//  mvrVotes = {0=51846, 1=8841, 2=6750} NC=67437
//     pAssorter reportedMargin=0.6555896614618087 reportedAvg=0.8277948307309044 assortAvg = 0.8188531518305975 ******
//     oaAssorter reportedMargin=0.6555896614618087 reportedAvg=0.8277948307309044 assortAvg = 0.827794830730896
// ****** passortAvg != oassortAvg
//
// OneAuditClcaAssorter.assorter = pAssorter reportedMargin agrees with oAassortAvg
// OaPluralityAssorter = oaAssorter reportedMargin agrees with OaPluralityAssorter.assort(cvrs).mean
// pAssorter reportedMargin does not agree with pAssortAvg, because pAssorter doesnt use the average pool values. ****
//
// why are we using reqular pAssort instead of OaPluralityAssorter in the OneAuditClcaAssorter?
// because its the assorter you have to use for the cvrs

// This is the primitive assorter whose average assort values agrees with the reportedMargin.
// TODO check against SHANGRLA
// TODO not really used
class OaPluralityAssorter(val contestOA: OneAuditContest1, winner: Int, loser: Int, reportedMargin: Double):
    PluralityAssorter(contestOA.info, winner, loser, reportedMargin) {

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (mvr.poolId == null) {
            return super.assort(mvr, usePhantoms = usePhantoms)
        }

        // TODO not sure of this
        //     if (hasStyle and !cvr.hasContest(contestOA.id)) {
        //            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        //        }
        //        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
        //                         else this.assorter.assort(mvr, usePhantoms = false)

        val pool = contestOA.pools[mvr.poolId]
            ?: throw IllegalStateException("Dont have pool ${mvr.poolId} in contest ${contestOA.id}")
        val avgBatchAssortValue = margin2mean(pool.calcReportedMargin(winner, loser))

        return if (mvr.phantom) .5 else avgBatchAssortValue
    }

    companion object {
        fun makeFromContestVotes(contestOA: OneAuditContest1, winner: Int, loser: Int): OaPluralityAssorter {
            val contest = contestOA.contest as Contest
            val winnerVotes = contest.votes[winner] ?: 0
            val loserVotes = contest.votes[loser] ?: 0
            val reportedMargin = (winnerVotes - loserVotes) / contest.Nc.toDouble()
            return OaPluralityAssorter(contestOA, winner, loser, reportedMargin)
        }

        fun makeFromClcaAssorter(oaClcaAssorter: OneAuditClcaAssorter1) =
            makeFromContestVotes(
                oaClcaAssorter.contestOA,
                oaClcaAssorter.assorter.winner(),
                oaClcaAssorter.assorter.loser())
    }
}