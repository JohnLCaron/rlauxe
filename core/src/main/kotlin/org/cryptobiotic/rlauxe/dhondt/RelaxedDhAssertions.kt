package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.dhondt.DhondtBuilder.Companion.makeDhAssorterFromDivisors
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger("RelaxedDhAssertions")

class RelaxedDhAssertions(val orgContest: DhondtContest,
                          val orgAssorters: List<AssorterIF>,
                          override val Npop: Int,
                          override val nsamples: Int,
                          override val alpha: Double,
                          failuresIn: List<DhondtFailure>,
                          version: String? = null
): RelaxedAssertionsIF {
    val orgInfo = orgContest.info
    val votes = orgContest.votes

    val failures: MutableList<DhondtFailure>
    val altContest: DhondtContest
    val contestRange: ContestRange
    val assortersForProof: List<AssorterIF>

    init {
        logger.debug { "Contest ${orgInfo.name} haveSampleSize=${nsamples}" }
        failures = failuresIn.toMutableList()

        if (version == "tooRelaxed") {
            val builder = TooRelaxedDhondtBuilder(orgContest, failures)
            altContest = builder.build()
            assortersForProof = altContest.assorters
            contestRange = ContestRange.fromFailures(altContest, failures)

        } else {
            val algo = DHrelaxingAlgorithm(orgContest, orgAssorters, failuresIn)
            altContest = DhondtBuilderFromAssorters(orgContest, algo.assortersOut).build() // TODO
            assortersForProof = algo.assortersOut
            contestRange = algo.contestRange

        }
    }

    override fun altContest() = altContest
    override fun assortersForProof() = assortersForProof
    override fun contestRange() = contestRange
    override fun failures() = failures

    // push to interface ??
    override fun show() = buildString {
        appendLine("Original Seat Ordering")
        append(showCandidateSeatOrder(orgContest, orgAssorters, nsamples, alpha))

        appendLine("Failures")
        appendLine(DhondtFailure.header())
        failures.forEach { appendLine(it) }
        appendLine()

        //appendLine("Relaxed Assertions Seat Ordering")
        //append(showCandidateSeatOrder(altContest, assortersForProof, nsamples, alpha))
        appendLine()
        append(showTable5(altContest, failures))
        appendLine()
        appendLine("Party Seat Ranges (${orgContest.name})")
        append(contestRange.showSeatRanges())
    }

    // 1. Generate the complete exact set as described in my paper Proposition 1 (you're doing this correctly already).
    // 2. If there's a DH assertion DH(P_w, P_l, w_lowest_winner, l_highest_loser) that's too hard to audit, remove it and
    //    add DH(P_w, P_l, w_lowest_winner-1, l_highest_loser) and DH(P_w, P_l, w_lowest_winner, l_highest_loser+1) instead.
    //    This moves P_w's lowest winner and P_l's highest loser into your 'yellow zone' - update the claimed bounds accordingly.
    //      I initially thought we needed to add a bunch of other junk, but you convinced me yesterday that that was not necessary.
    // 3. I _believe_ that step 2 can be repeated, including on the DH assertions you've just added, if necessary.
    //   Just keep moving both ends of the assertion into the yellow zone, and updating the bounds accordingly.
    inner class DHrelaxingAlgorithm(val dcontest: DhondtContest, val orgAssorters: List<AssorterIF>, val orgFailures: List<DhondtFailure>) {
        // val partyCounts = mutableMapOf<Int, PartyCount>() // TODO use PartyRange
        // val yellowWinners = mutableListOf<DhondtCandidateScore>()
        // val yellowLosers = mutableListOf<DhondtCandidateScore>()

        var round = 0
        val party = dcontest.parties.associateBy { it.id }
        var assortersOut = mutableListOf<AssorterIF>()
        val contestRange = ContestRange(dcontest)

        init {
            //val parties = dcontest.parties
            //parties.forEach {
            //    partyCounts[it.id] = PartyCount(it, dcontest.winnerSeatCount[it.id]!!)
            //}
            // println("${show()}")
            contestRange.addFailures(orgFailures)

            var failures = orgFailures
            var assortersIn = orgAssorters

            var accept = false
            while (!accept) {
                val assortersAdded = step2(assortersIn, assortersOut)
                if (assortersAdded.isEmpty()) accept = true
                else {
                    assortersIn = assortersAdded
                    // see if the new failures: if you run again and dont fail, they get added
                    failures =  findDhondtFailures(dcontest, assortersAdded, Npop, nsamples, alpha)
                    contestRange.addFailures(failures)
                }
            }

            // have to wait till you have all failures before computing the ranges
            contestRange.computePartyRanges()
        }

        // return assortersAdded
        fun step2(assortersIn: List<AssorterIF>, assortersKeep: MutableList<AssorterIF>): List<DhondtAssorter> {
            val assortersAdded = mutableListOf<DhondtAssorter>()

            assortersIn.forEach { assorter ->
                // 2. If there's a DH assertion DH(P_w, P_l, w_lowest_winner, l_highest_loser) that's too hard to audit, remove it and
                //    add DH(P_w, P_l, w_lowest_winner-1, l_highest_loser) and DH(P_w, P_l, w_lowest_winner, l_highest_loser+1) instead.
                //    This moves P_w's lowest winner and P_l's highest loser into your 'yellow zone' - update the claimed bounds accordingly.

                val failure = failures.find { it.assorter == assorter }
                if (failure != null) {
                    val dh = assorter as DhondtAssorter
                    val winningParty = party[dh.winner()]!!
                    val losingParty = party[dh.loser()]!!
                    // If w > 1, add DHA,B (w − 1, l); (If w was already uncertain, do nothing.)
                    if (dh.winnerDivisor > 1)
                        assortersAdded.add(makeDhAssorterFromDivisors(dcontest.info,
                            winningParty, dh.winnerDivisor - 1,
                            losingParty, dh.loserDivisor,
                            dcontest.Nc))
                    // If l < |B| add DHA,B (w, l + 1); if l was already uncertain, do nothing.
                    if (dh.loserDivisor < losingParty.nCandidates)
                        assortersAdded.add(makeDhAssorterFromDivisors(dcontest.info,
                            winningParty, dh.winnerDivisor,
                            losingParty, dh.loserDivisor+1,
                            dcontest.Nc))

                } else {
                    assortersKeep.add(assorter)
                }
            }
            return assortersAdded
        }
    }
}

open class DhondtBuilderFromAssorters(
    val from: DhondtContest,
    val assorters: List<AssorterIF>,
) : DhondtBuilder(from) {

    override fun build(): DhondtContest {
        val votes = partyBs.associate { Pair(it.id, it.totalVotes) }
        val parties = partyBs.map { it.build() }

        return DhondtContest(
            info,
            votes,
            this.Nc,
            Ncast = this.validVotes + this.undervotes,
            parties,
            sortedScores,
            assorters,
            thresholdOverride,
        )
    }
}

// Doesnt work because For each clear winner  (call it party P quotient s), we need to keep enough assertions to prove that
// P's s-th quotient beats 'seats - (s-1)' other candidates. Likewise, for the clear losers, we need to keep
// enough assertions to prove that they lost against at least 'seats' others.
class TooRelaxedDhondtBuilder(
    from: DhondtContest,
    val failures: List<DhondtFailure>,
): DhondtBuilder(from) {

    override fun build(): DhondtContest {
        // the partybs.lastSeatWon/firstSeatLost have been set - we need to modify them
        val partybsMap = partyBs.associateBy { it.id }

        // put them in a set so we dont do it more than once for each party
        val yellowWinners = failures.map { it.assorter.winnerId }.toSet()
        val yellowLosers = failures.map { it.assorter.loserId }.toSet()

        // For each failed assertion, modify partyBs lastWinner and firstLoser
        yellowWinners.forEach {
            val winningParty = partybsMap[it]!!
            winningParty.lastSeatWon = max(0, winningParty.lastSeatWon - 1)
        }
        yellowLosers.forEach {
            val losingParty = partybsMap[it]!!
            losingParty.firstSeatLost = min(losingParty.firstSeatLost + 1, losingParty.nCandidates ?: Int.MAX_VALUE)
        }

        // now make standard assertions etc
        return super.build()
    }
}

/*
open class RelaxedDhondtBuilder(
    from: DhondtContest,
    val failures: List<DhondtFailure>,
): DhondtBuilder(from) {

    override fun build(): DhondtContest {
        // the partybs.lastSeatWon/firstSeatLost have been set - we need to modify them
        val partybsMap = partyBs.associateBy { it.id }

        // put them in a set so we dont do it more than once for each party
        val yellowWinners = failures.map { it.assorter.winnerId }.toSet()
        val yellowLosers = failures.map { it.assorter.loserId }.toSet()

        // For each failed assertion, modify partyBs lastWinner and firstLoser
        yellowWinners.forEach {
            val winningParty = partybsMap[it]!!
            winningParty.lastSeatWon = max(0, winningParty.lastSeatWon - 1)
        }
        yellowLosers.forEach {
            val losingParty = partybsMap[it]!!
            losingParty.firstSeatLost = min(losingParty.firstSeatLost + 1, losingParty.nCandidates ?: Int.MAX_VALUE)
        }

        // now make standard assertions etc
        return super.build()
    }
} */

/* So when multiple seats are contested, go to a "second round", adding DH assertions between the contested winning candidates.
fun round2(contestedWinningSeats: Set<Int>, contestedWinningCandidates: List<DhondtCandidateScore>) {
    val candidateMap = dcontest.parties.associateBy { it.id }

    // add DH between contestedWinningCandidates
    // new assorters
    val assorters = mutableListOf<DhondtAssorter>()
    for (winidx in 0 until contestedWinningCandidates.size) {
        for (loseidx in winidx+1 until contestedWinningCandidates.size) {
            val winScore = contestedWinningCandidates[winidx]
            val loseScore = contestedWinningCandidates[loseidx]
            val winParty = candidateMap[winScore.partyId]!!
            val loseParty = candidateMap[loseScore.partyId]!!
            //         fun makeFrom(info: ContestInfo, winner: DhondtParty, loser: DhondtParty, Nc: Int, Npop: Int?=null): DhondtAssorter {
            val assorter = DhondtBuilder.makeDhAssorterFromDivisors(dcontest.info, winParty, winScore.divisor, loseParty, loseScore.divisor, dcontest.Nc)
            assorters.add(assorter)
        }
    }

    /* println("\nRound2 assorters added")
    // println(DhondtFailure.header())
    assorters.forEach { println("  $it ${estRiskStandardBet(Npop, it.noerror(true), nsamples)}") }
    println() */

    assorters.forEach { dassorter ->
        val risk = estRiskStandardBet(Npop, dassorter.noerror(true), nsamples)
        if (risk > alpha) {
            val winnerId = dassorter.winner()
            val loserId = dassorter.loser()
            val winnerScore =
                dcontest.sortedScores.find { it.divisor == dassorter.winnerDivisor && it.partyId == winnerId }!!
            val loserScore =
                dcontest.sortedScores.find { it.divisor == dassorter.loserDivisor && it.partyId == loserId }!!
            val failure = DhondtFailure(Npop, dassorter, winnerScore, loserScore, risk, nsamples, alpha, round2=true)
            failures.add(failure)
        }
    }
} */