package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import kotlin.math.round
import kotlin.random.Random

//    Let N_c = upper bound on ballots for contest C.
//    Let Nb = N (physical ballots) = ncvrs (comparison) or nballots in manifest (polling).
//
//    When we have styles, we can calculate Nb_c = physical ballots for contest C
//    Let V_c = votes for contest C, V_c <= Nb_c <= N_c.
//    Let U_c = undervotes for contest C = Nb_c - V_c >= 0.
//    Let Np_c = nphantoms for contest C = N_c - Nb_c, and are added to the ballots before sampling or sample size estimation.
//    Then V_c + U_c + Np_c = N_c.
//
//    Comparison, no styles: we have cvrs, but the cvr doesnt record undervotes.
//    We know V_c and N_c. Cant distinguish an undervote from a phantom, so we dont know U_c, or Nb_c or Np_c.
//    For estimating, we can use some guess for U_c.
//    For auditing, I think we need to assume U_c is 0? So Np_c = N_c - V_c??
//    I think we must have a ballot manifest, which means we have Nb, and ...

// Simulation of single Contest that reflects the exact votes and Nb (diluted), along with undervotes and phantoms as specified in Contest.
// resurrected 12/4/25; used for polling estimation; use ClcaFuzzSamplerTracker for CLCA

class ContestSimulation(val contest: Contest, val Npop: Int) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val voteCount = contest.votes.map { it.value }.sum() // V_c
    val phantomCount = contest.Nphantoms() //  - underCount - voteCount // Np_c
    val underCount = Npop * info.voteForN - contest.Nphantoms() - voteCount // U_c

    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    init {
        resetTracker()
    }

    fun show(): String {
        return "ContestSimulation ${contest.id} voteCount=$voteCount underCount=$underCount phantomCount=$phantomCount "
    }

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(contest.votes.toList())
        votesLeft = voteCount
    }

    // TODO replace with replace with CvrSimulation.simulateCvrsWithDilutedMargin
    fun makeCvrs(prefix: String = "card", poolId: Int?=null): List<Cvr> {
        resetTracker()
        val contestId = info.id

        var count = 0
        val result = mutableListOf<Cvr>()
        repeat(this.voteCount) {
            val cvrb = CvrBuilder2("$prefix-${count++}", poolId=poolId)
            val voteFor = chooseCandidate(Random.nextInt(votesLeft))
            cvrb.addContest(contestId, intArrayOf(voteFor))
            result.add(cvrb.build())
        }
        // add empty undervotes
        repeat(underCount) {
            val cvrb = CvrBuilder2("$prefix-${count++}", poolId=poolId)
            cvrb.addContest(contestId, intArrayOf())
            result.add(cvrb.build())
        }
        // add phantoms
        repeat(phantomCount) {
            val cvrb = CvrBuilder2("$prefix-${count++}", phantom=true)
            cvrb.addContest(contestId, intArrayOf())
            result.add(cvrb.build())
        }
        return result.toList()
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    fun chooseCandidate(choice: Int): Int {
        val check = trackVotesRemaining.sumOf { it.second }
        require(check == votesLeft)

        var sum = 0
        var nvotes = 0
        var idx = 0
        while (idx < ncands) {
            nvotes = trackVotesRemaining[idx].second
            sum += nvotes
            if (choice < sum) break
            idx++
        }
        val candidateId = trackVotesRemaining[idx].first
        require(nvotes > 0)
        trackVotesRemaining[idx] = Pair(candidateId, nvotes-1)
        votesLeft--
        return candidateId
    }

    companion object {
        private val logger = KotlinLogging.logger("ContestSimulation")

        /** Make a 2 candidate plurality Contest with given margin etc. */
        // used by ClcaSingleRoundAuditTaskGenerator amd othe rplaces for CLCA
        fun make2wayTestContest(Nc: Int,
                                margin: Double, // margin of top highest vote getters, not counting undervotePct, phantomPct
                                undervotePct: Double, // needed to set Nc
                                phantomPct: Double): ContestSimulation {
            val nvotes = round(Nc * (1.0 - undervotePct - phantomPct))
            val winner = roundToClosest((margin * Nc + nvotes) / 2)
            val loser = roundToClosest(nvotes - winner)
            val Np = roundToClosest(Nc * phantomPct)
            val Nu = roundToClosest(Nc * undervotePct)
            val contest = Contest(
                ContestInfo("standard", 0, mapOf("A" to 0,"B" to 1), choiceFunction = SocialChoiceFunction.PLURALITY),
                mapOf(0 to winner, 1 to loser),
                Nc = Nc,
                Ncast = roundToClosest(nvotes + Nu)
            )
            return ContestSimulation(contest, Nc)
        }

        // TODO replace with CvrSimulation.simulateCvrsWithDilutedMargin
        // class PollingCardFuzzSampler(
        //    val fuzzPct: Double,
        //    val cards: List<AuditableCard>,
        //    val contest: Contest,
        //    val assorter: AssorterIF
        //): Sampling, Iterator<Double>
        // Needed for Polling estimation
        fun simulateCvrsWithDilutedMargin(contestUA: ContestWithAssertions, config: AuditConfig): List<Cvr> {
            val limit = config.contestSampleCutoff
            val contestOrg = contestUA.contest as Contest // TODO IRV
            if (limit == null || contestOrg.Nc <= limit) return ContestSimulation(contestOrg, contestUA.Npop).makeCvrs()

            // otherwise scale everything
            val sNc = limit / contestOrg.Nc.toDouble()
            val sNb = roundToClosest(sNc * contestUA.Npop)
            val sNp = roundToClosest(sNc * contestOrg.Nphantoms())
            val sNu = roundToClosest(sNc * contestOrg.Nundervotes())
            val orgVoteCount = contestOrg.votes.map { it.value }.sum() // V_c
            val svotes = contestOrg.votes.map { (id, nvotes) -> id to roundToClosest(sNc * nvotes) }.toMap()
            val voteCount = svotes.map { it.value }.sum() // V_c

            //if (abs(voteCount - limit) > 10) {
            //    logger.warn {"simulateContestCvrsWithLimits limit wanted = ${limit} scaled = ${voteCount}"}
            //}

            val contestScaled = Contest(
                contestOrg.info,
                svotes,
                Nc = voteCount + sNu + sNp,
                Ncast = voteCount + sNu,
            )

            val sim = ContestSimulation(contestScaled, sNb)
            return sim.makeCvrs()
        }
    }
}