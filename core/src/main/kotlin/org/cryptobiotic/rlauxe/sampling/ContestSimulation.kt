package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.listToMap
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

/** Simulation of multicandidate Contest that reflects the exact votes and Nc, along with undervotes and phantoms. */
class ContestSimulation(val contest: Contest, underVotePct: Double) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val voteCount = contest.votes.map { it.value }.sum() // V_c
    val underCount = (contest.Nc * underVotePct).toInt() // U_c
    val phantomCount = contest.Nc - underCount - voteCount // Np_c

    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    init {
        resetTracker()
    }

    fun show() = "ContestSimulation ${contest.id} voteCount=$voteCount underCount=$underCount phantomCount=$phantomCount "

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(contest.votes.toList())
        votesLeft = voteCount
    }

    // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    // cvrs only contain this contest
    // ncvrs = voteCount + underCount + phantomCount = Nc
    fun makeCvrs(): List<Cvr> {
        resetTracker()
        val cvrbs = CvrBuilders().addContests(listOf(contest.info))
        val result = mutableListOf<Cvr>()
        repeat(this.voteCount) {
            val cvrb = cvrbs.addCvr()
            cvrb.addContest(info.name, chooseCandidate(Random.nextInt(votesLeft))).done()
            result.add(cvrb.build())
        }
        // add empty undervotes
        repeat(underCount) {
            val cvrb = cvrbs.addCvr()
            cvrb.addContest(info.name).done()
            result.add(cvrb.build())
        }
        // add phantoms
        repeat(phantomCount) {
            val cvrb = cvrbs.addPhantomCvr()
            cvrb.addContest(info.name).done()
            result.add(cvrb.build())
        }
        return result.toList()
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    fun chooseCandidate(choice: Int): Int {
        val check = trackVotesRemaining.map { it.second }.sum()
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
        /** Make a 2 candidate plurality Contest with given margin etc. */
        fun make2wayTestContest(reportedMargin: Double, underVotePct: Double, phantomPct: Double, Nc: Int): ContestSimulation {
            val info = ContestInfo(
                name = "AvB",
                id = 0,
                choiceFunction = SocialChoiceFunction.PLURALITY,
                candidateNames = listToMap( "A", "B"),
            )
            val underCount = (Nc * underVotePct).toInt()
            val phantomCount = (Nc * phantomPct).toInt()
            val voteCount = Nc - underCount - phantomCount

            val winnerCount = ((reportedMargin * Nc + voteCount) / 2.0) .toInt()
            val loserCount = ((voteCount - reportedMargin * Nc) / 2.0) .toInt()

            val contest = Contest(info, mapOf(0 to winnerCount, 1 to loserCount), Nc=Nc)
            return ContestSimulation(contest, underVotePct)
        }
    }

}