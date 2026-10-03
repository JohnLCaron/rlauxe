package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.dhondt.DhondtBuilder.Companion.makeDhAssorterFromDivisors

private val logger = KotlinLogging.logger("RelaxedDhAssertions")

// current version: "Ideas for bounding sample sizes for Belgian RLAs", October 1, 2026
class RelaxedDhAssertions(override val orgContest: DhondtContest,
                          val orgAssorters: List<AssorterIF>,
                          override val Npop: Int,
                          override val nsamples: Int,
                          override val alpha: Double,
                          failureFinder: FailureFinder,
                          version: String? = null
): RelaxedAssertionsIF {
    val orgInfo = orgContest.info
    val votes = orgContest.votes

    val failuresIn = failureFinder.findDhondtFailures(orgContest, orgAssorters)
    val tfailuresIn = failureFinder.findThresholdFailures(orgContest, orgAssorters)
    val altContests: List<AltContest>
    val totalContestRange: ContestRange
    val assortersForProof: List<AssorterIF>

    init {
        logger.debug { "Contest ${orgInfo.name} haveSampleSize=${nsamples}" }

        if (tfailuresIn.isNotEmpty() && failuresIn.isNotEmpty()) {
            val algo = BothAlgorithm44(orgContest, failureFinder, tfailuresIn)
            totalContestRange = algo.contestRange
            altContests = listOf(AltContest("Original", orgContest, ContestRange.fromFailures(orgContest, failuresIn), failuresIn.size, tfailuresIn.size)) +
                algo.altContests
            assortersForProof = emptyList() // TODO maybe you need all alts, and validate all ?? doesnt affect contestRange I think

        } else if (tfailuresIn.isNotEmpty()) {
            val algo = ThresholdOnlyAlgorithm422(orgContest, tfailuresIn)
            totalContestRange = algo.contestRange
            altContests = listOf(
                AltContest("Original", orgContest, ContestRange.fromFailures(orgContest, failuresIn), 0, tfailuresIn.size),
                AltContest("ThresholdOnly", algo.altContest, ContestRange.fromFailures(algo.altContest, failuresIn), 0, 0)
            )
            assortersForProof = algo.assorters

        /* } else if (version == "tooRelaxed") { // bogus
            val builder = TooRelaxedDhondtBuilder(orgContest, failuresIn)
            val altContest = builder.build()
            assortersForProof = altContest.assorters
            altContests = listOf(
                AltContest("Original", orgContest, ContestRange.fromFailures(orgContest, failuresIn), failuresIn.size, tfailuresIn.size),
                AltContest("tooRelaxed", altContest, ContestRange.fromFailures(altContest, failuresIn), failuresIn.size, tfailuresIn.size)
            )
            totalContestRange = ContestRange.fromFailures(altContest, failuresIn) */

        } else {
            val algo = DhOnlyAlgorithm431(orgContest, orgAssorters, failureFinder)
            val altContest = DhondtBuilderFromAssorters(orgContest, algo.assortersOut).build() // TODO
            assortersForProof = algo.assortersOut
            altContests = listOf(
                AltContest("Original", orgContest, ContestRange.fromFailures(orgContest, failuresIn), failuresIn.size, 0),
                AltContest("DhOnly", altContest, algo.contestRange, algo.contestRange.dhFail(), 0)
            )
            totalContestRange = algo.contestRange
        }
    }

    override fun altContests() = altContests
    override fun assortersForProof() = assortersForProof
    override fun totalContestRange() = totalContestRange
    override fun failures() = failuresIn
    override fun tfailures() = tfailuresIn

    // push to interface ??
    override fun show() = buildString {
        appendLine("Original Seat Ordering")
        append(showCandidateSeatOrder(orgContest, orgAssorters, nsamples, alpha))

        appendLine("Failures")
        appendLine(DhondtFailure.header())
        failuresIn.forEach { appendLine(it) }
        tfailuresIn.forEach { appendLine(it) }
        appendLine()

        appendLine("Party Seat Ranges (${orgContest.name})")
        append(totalContestRange.showSeatRanges())

        appendLine("Alternate Contests")
        altContests.forEach { altContest ->
            appendLine("--------------------------------------")
            appendLine("Seat Ordering")
            append(showCandidateSeatOrder(altContest.altContest, altContest.altContest.assorters, nsamples, alpha))
            appendLine()
        }
        append(showTable5(orgContest, failuresIn))
        appendLine()
    }
}

// 4.3. Relaxing in the case that a DH assertion is infeasible
// 4.3.1-2, p 11
// 1. Generate the complete exact set as described in my paper Proposition 1 (you're doing this correctly already).
// 2. If there's a DH assertion DH(P_w, P_l, w_lowest_winner, l_highest_loser) that's too hard to audit, remove it and
//    add DH(P_w, P_l, w_lowest_winner-1, l_highest_loser) and DH(P_w, P_l, w_lowest_winner, l_highest_loser+1) instead.
//    This moves P_w's lowest winner and P_l's highest loser into your 'yellow zone' - update the claimed bounds accordingly.
//      I initially thought we needed to add a bunch of other junk, but you convinced me yesterday that that was not necessary.
// 3. I _believe_ that step 2 can be repeated, including on the DH assertions you've just added, if necessary.
//   Just keep moving both ends of the assertion into the yellow zone, and updating the bounds accordingly.

class DhOnlyAlgorithm431(val dcontest: DhondtContest, assorters: List<AssorterIF>, failureFinder: FailureFinder) {
    var round = 0
    val party = dcontest.parties.associateBy { it.id }
    var assortersOut = mutableListOf<AssorterIF>()
    val contestRange = ContestRange(dcontest)
    val dhFail: Int

    init {
        var failures = failureFinder.findDhondtFailures(dcontest, assorters)
        contestRange.addFailures(failures)

        var assortersIn = assorters

        var accept = false
        while (!accept) {
            val assortersAdded = step2(assortersIn, assortersOut, failures)
            if (assortersAdded.isEmpty()) accept = true
            else {
                assortersIn = assortersAdded
                // see if there are new failures: on the next round they get added if they dont fail
                failures = failureFinder.findDhondtFailures(dcontest, assortersAdded) // just the new failure
                contestRange.addFailures(failures)
            }
        }

        // have to wait till you have all failures before computing the ranges
        contestRange.computeRangesFromFailures()
        dhFail = contestRange.dhFail()
    }

    // return assortersAdded
    // the failures are just the new failures
    fun step2(assortersIn: List<AssorterIF>, assortersKeep: MutableList<AssorterIF>, failures: List<DhondtFailure>): List<DhondtAssorter> {
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
                    assortersAdded.add(
                        makeDhAssorterFromDivisors(
                            dcontest.info,
                            winningParty, dh.winnerDivisor - 1,
                            losingParty, dh.loserDivisor,
                            dcontest.Nc
                        )
                    )
                // If l < |B| add DHA,B (w, l + 1); if l was already uncertain, do nothing.
                if (dh.loserDivisor < losingParty.nCandidates)
                    assortersAdded.add(
                        makeDhAssorterFromDivisors(
                            dcontest.info,
                            winningParty, dh.winnerDivisor,
                            losingParty, dh.loserDivisor + 1,
                            dcontest.Nc
                        )
                    )

            } else {
                assortersKeep.add(assorter)
            }
        }
        return assortersAdded
    }
}

class DhondtBuilderFromAssorters(
    from: DhondtContest,
    val assorters: List<AssorterIF>,
    belowThresholdOverride: Set<Int>? = null, // set when non standard: partyIds below threshold
) : DhondtBuilder(from, belowThresholdOverride) {

    override fun makeAssorters() = assorters
}

// 4.4. Algorithm for threshold uncertainty and DH failures
// The following algorithm combines a shortcut version of 4.2.2 with the DH-relaxing
// algorithm in 4.3.2.
class BothAlgorithm44(val from: DhondtContest,
                      val failureFinder: FailureFinder,
                      val tfailures: List<ThresholdFailure>,
) {
    val info = from.info
    val contestRange = ContestRange(from)
    val altContests = mutableListOf<AltContest>()

    init {
        val ET = tfailures.map { it.assorter.winner() }
        val AT = from.parties.filter{ !ET.contains(it.id) && !it.isBelowMin }
        val BT = from.parties.filter{ !ET.contains(it.id) && it.isBelowMin }
        val BTids = BT.map { it.id }.toSet()

        // 1. Include AT (P) assertions for all P ∈ AT and, similarly, BT (P ) for all P ∈ BT.
        val tassorters = mutableListOf<AssorterIF>()
        AT.forEach { party -> tassorters.add(AboveThreshold.makeFromVotes(info, party.id, from.votes, info.minFraction!!, from.Nc)) }
        BT.forEach { party -> tassorters.add(BelowThreshold.makeFromVotes(info, party.id, from.votes, from.Nc)) }

        // 2. Assume all P ∈ ET are above the threshold (but do not add AT assertions for them).
        //    a) Run the algorithm from subsubsection 4.3.1 to derive relaxed DH assertions
        //       and the corresponding relaxed-DH values—call them DHlowW, DHlowU, DHlowL
        //    Call the corresponding assertion set the Low Assertion Set
        val lowBuilder = DhondtBuilderDontAddThresholds(from, BTids, tassorters)
        val lowContest = lowBuilder.build()
        val subalgoLow = DhOnlyAlgorithm431(lowContest, lowContest.assorters, failureFinder)
        contestRange.mergeAltContestRange(subalgoLow.contestRange)
        altContests.add(AltContest("Low Assertions", lowContest, subalgoLow.contestRange, subalgoLow.contestRange.dhFail(), 0))

        // 3. Assume all P ∈ ET are below the threshold (but do not add BT assertions for them).
        //    a) Run the algorithm from subsubsection 4.3.1 to derive relaxed DH assertions
        //       and the corresponding relaxed-DH values—call them DHhighW, DHhighU, DHhighL
        //    Call the corresponding assertion set the High Assertion Set
        val belowThreshold = BTids + ET.toSet()
        val highBuilder = DhondtBuilderDontAddThresholds(from, belowThreshold, tassorters)
        val highContest = highBuilder.build()
        val subalgoHigh = DhOnlyAlgorithm431(highContest, highContest.assorters, failureFinder)
        contestRange.mergeAltContestRange(subalgoHigh.contestRange)
        altContests.add(AltContest("High Assertions", highContest, subalgoHigh.contestRange, subalgoHigh.contestRange.dhFail(), ET.size))


        // 4. For each P ∈ ET, assume that P is above the threshold and all other parties in
        //      ET are below it (but do not add any AT or BT assertions).
        //    a) Run the algorithm from subsubsection 4.3.1 to derive relaxed DH assertions
        //       and the corresponding relaxed DH values DHsoloW, DHsoloU, DHsoloL
        //    Call the corresponding assertion set the P-solo Assertion Set

        // if there only one ET, this will be the same as high assertions
        if (ET.size > 1) {
            ET.forEach { partyId ->
                val belowThreshold = BTids + ET.filter { it == partyId }.toSet()
                val soloBuilder = DhondtBuilderDontAddThresholds(from, belowThreshold, tassorters)
                val soloContest = soloBuilder.build()
                val subalgoSolo = DhOnlyAlgorithm431(soloContest, soloContest.assorters, failureFinder)
                contestRange.mergeAltContestRange(subalgoSolo.contestRange)
                altContests.add(
                    AltContest(
                        "${info.candidateIdToName[partyId]} Solo Assertions", soloContest, subalgoSolo.contestRange,
                        subalgoSolo.contestRange.dhFail(), belowThreshold.size
                    )
                )
            }
        }

        // 5. If for any P ∈ BT, it is harder to audit BT(P) than to prove that P is a DH
        //  loser to at least s other AT quotients, remove BT(P) and add the necessary DH assertions instead.
        // TODO

        // Slight updates, of which the only important one is that in Step 5 of the "Complete Algorithm," you can only use AT
       // parties to prove that P's 1st candidate doesn't win. You probably knew that already, but at least now it is written properly.

        // If for any P ∈ BT , it is harder to audit BT (P ) than to prove that P is a DH
        // loser to at least s other AT quotients, remove BT (P ) and add the necessary DH
        // assertions instead.

        // 3. For each party P , its maximum and minimum seat counts are the maximum and
        //   minimum values encountered at any point in Step 2.
        // this is contestRange.mergeAltContestRange(ContestRange(altContest))
    }
}

//////////////////////////////////////////////////////////////////////////
// TODO can we use the general case ??
// 4.2.2. Algorithm for threshold uncertainty, page 9.
// For all 2^|ET| possible assignments of AT or BT to the parties in ET
class ThresholdOnlyAlgorithm422(val from: DhondtContest, tfailures: List<ThresholdFailure>) {

    val info = from.info
    val contestRange = ContestRange(from)
    val altContest: DhondtContest
    val assorters: List<AssorterIF>

    init {

        // S is the number of available seats.
        // d(i) is the divisor for the i-th index. For D’Hondt, d(i) = i.
        // P is the set of parties.
        // If P ∈ P is a party, ∥P ∥ is the number of candidates fielded by P .
        // s :: P → N ∪ {0} is an allocation of seats to parties.
        // s(P ) is both the number of seats allocated to party P and (if non-zero) the index of the candidate in party P
        //    who is reported to get seated with the lowest quotient. This must satisfy s(P ) ≤ ∥P ∥.
        // The tally T (P ) is the total number of valid votes for P .
        // The quotient of the i-th ranked candidate of a party P , denoted Q(L, i), is the list’s tally divided by d(i).

        // Define the following partition of P:
        //  AT are those (clearly, verifiably) above the threshold—we include an AT assertion for each party in this set;
        //  ET are those too close to the threshold to verify—we include neither an AT nor a BT assertion for parties in this set;
        //  BT are those (clearly, verifiably) below the threshold—we include a BT assertion for each party in this set.

        val ET = tfailures.map { it.assorter.winner() }
        val AT = from.parties.filter { !ET.contains(it.id) && !it.isBelowMin }
        val BT = from.parties.filter { !ET.contains(it.id) && it.isBelowMin }

        // 1. Initialise the assertion set with AT (P ) assertions for all P ∈ AT and, similarly, BT (P) for all P ∈ BT.
        val tassorters = mutableListOf<AssorterIF>()
        AT.forEach { party -> tassorters.add(AboveThreshold.makeFromVotes(info, party.id, from.votes, info.minFraction!!, from.Nc)) }
        BT.forEach { party -> tassorters.add(BelowThreshold.makeFromVotes(info, party.id, from.votes, from.Nc)) }

        // 2. For all 2^|ET| possible assignments of AT or BT to the parties in ET,
        // calculate the DH assertions from Proposition 1 and add the DH(, ) assertions to the assertion set.
        //   (Do not include AT or BT assertions for parties in ET .)

        // it is not necessary to check all 2|ET | possible assignments, but only the cases in which
        // 1. all P ∈ ET are above the threshold,
        // 2. all P ∈ ET are below the threshold,
        // 3. for each P ∈ ET, P is above the threshold and all other parties in ET are below.

        // for now, just assume one threshold failure

        val altBuilder = DhondtBuilderDontAddThresholds(from, emptySet(), tassorters)
        altContest = altBuilder.build()
        assorters = altContest.assorters

        // 3. For each party P , its maximum and minimum seat counts are the maximum and
        //   minimum values encountered at any point in Step 2.

        contestRange.mergeAltContestRange(ContestRange(altContest))
    }
}

// the threshold assertions (if any) are passed in, do not create new ones
// just add the usual dh assertions
class DhondtBuilderDontAddThresholds (
    from: DhondtContest,
    belowThreshold: Set<Int>? = null, // set when non standard: partyIds below threshold
    val tassorters: List<AssorterIF>
) : DhondtBuilder(from, belowThreshold) {

    override fun makeAssorters(): List<AssorterIF> {
        val assorters = mutableListOf<AssorterIF>()
        val parties = partyBs.map { it.build() }

        // define W the set of parties that have at least one reported winner
        // define L the set of parties that have at least one reported loser
        val winningParties = parties.filter { it.lastSeatWon > 0 }.toSet()
        val losingParties = parties.filter { it.firstSeatLost > 0 }.toSet()

        losingParties.forEach { loser ->
            // make DH assertions for this loser and all winners
            winningParties.filter { it.id != loser.id }.forEach { winner ->
                assorters.add( makeDhAssorterFromParty(info, winner = winner, loser = loser, Nc))
            }
        }

        return assorters + tassorters
    }
}


/* Doesnt work because For each clear winner  (call it party P quotient s), we need to keep enough assertions to prove that
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
} */

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