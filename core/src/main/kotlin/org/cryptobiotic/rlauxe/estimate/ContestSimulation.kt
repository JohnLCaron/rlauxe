package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.audit.CardLocation
import org.cryptobiotic.rlauxe.audit.CardLocationManifest
import org.cryptobiotic.rlauxe.audit.CardStyle
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

/**
 * Simulation of multicandidate Contest that reflects the exact votes and Nc, along with undervotes and phantoms,
 * as specified in Contest.
 */
class ContestSimulation(val contest: Contest) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val voteCount = contest.votes.map { it.value }.sum() // V_c
    val phantomCount = contest.Np //  - underCount - voteCount // Np_c
    val underCount = contest.Nc * info.voteForN - contest.Np - voteCount // U_c

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

    // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    // cvrs only contain this contest
    // ncvrs = voteCount + underCount + phantomCount = Nc
    fun makeCvrs(poolId: Int?=null): List<Cvr> {
        resetTracker()
        val cvrbs = CvrBuilders().addContests(listOf(contest.info))
        val result = mutableListOf<Cvr>()
        repeat(this.voteCount) {
            val cvrb = cvrbs.addCvr()
            cvrb.addContest(info.name, chooseCandidate(Random.nextInt(votesLeft))).done()
            result.add(cvrb.build(poolId))
        }
        // add empty undervotes
        repeat(underCount) {
            val cvrb = cvrbs.addCvr()
            cvrb.addContest(info.name).done()
            result.add(cvrb.build(poolId))
        }
        // add phantoms
        repeat(phantomCount) {
            val cvrb = cvrbs.addPhantomCvr()
            cvrb.addContest(info.name).done()
            result.add(cvrb.build(poolId))
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

    fun makeBallotManifest(hasStyle: Boolean): CardLocationManifest {
        val ncards: Int = contest.Nc - contest.Np
        val contests = listOf("contest0")
        val contestIds = listOf(0)
        val bs = CardStyle.make(0, contests, listOf(0), ncards)

        val cardLocations = mutableListOf<CardLocation>()
        repeat(ncards) {
            cardLocations.add(CardLocation("ballot$it", false, if (hasStyle) bs else null))
        }
        // add phantoms
        repeat(contest.Np) {
            cardLocations.add(CardLocation("phantom$it", true, null, contestIds))
        }
        return CardLocationManifest(cardLocations, listOf(bs))
    }

    companion object {
        /** Make a 2 candidate plurality Contest with given margin etc. */
        fun make2wayTestContest(Nc: Int,
                                margin: Double, // margin of top highest vote getters, not counting undervotePct, phantomPct
                                undervotePct: Double, // needed to set Nc
                                phantomPct: Double): ContestSimulation {
            val nvotes = round(Nc * (1.0 - undervotePct - phantomPct))
            val winner = roundToInt((margin * Nc + nvotes) / 2)
            val loser = roundToInt(nvotes - winner)
            val Np = roundToInt(Nc * phantomPct)
            val contest = Contest(
                ContestInfo("standard", 0, mapOf("A" to 0,"B" to 1), choiceFunction = SocialChoiceFunction.PLURALITY),
                mapOf(0 to winner, 1 to loser),
                iNc = Nc,
                Np=Np,
            )
            return ContestSimulation(contest)
        }

        fun makeContestWithLimits(contest: Contest, sampleLimit: Int): ContestSimulation {
            if (sampleLimit < 0 || contest.Nc <= sampleLimit) return ContestSimulation(contest)

            // otherwise scale everything
            val sNc = sampleLimit / contest.Nc.toDouble()
            val sNp = roundToInt(sNc * contest.Np)
            val orgVoteCount = contest.votes.map { it.value }.sum() // V_c
            val svotes = contest.votes.map { (id, nvotes) -> id to roundToInt(sNc * nvotes) }.toMap()
            val voteCount = svotes.map { it.value }.sum() // V_c

            if (voteCount > sampleLimit) {
                println("*** org = ${orgVoteCount} voteCount = ${voteCount}")
            }

            val contest = Contest(
                contest.info,
                svotes,
                iNc=sampleLimit,
                Np=sNp,
            )
            return ContestSimulation(contest)
        }
    }

}