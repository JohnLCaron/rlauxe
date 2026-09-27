package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.forEach
import kotlin.math.max

private val logger = KotlinLogging.logger("DhondtBuilderFromWinnerList")
private val showDetails = false

data class DhondtWinner(val candidate: Int, val divisor: Int) {
    var winningSeat: Int? = null

    override fun toString() = buildString {
        append("DhondtScore(candidate=$candidate, divisor=$divisor")
        if (winningSeat != null) append(", winningSeat=$winningSeat")
        append(")")
    }
}

/* side effect is to set party.lastSeatWon,firstSeatLost: could move that to  assignWinners2()
class DhondtBuilderFromWinnerList(
    val name: String,
    val id: Int,
    val parties: List<DhondtParty>,
    val nseats: Int,
    val Nc: Int, // trusted upper limit; // TODO need phantoms also
    val undervotes: Int,
    val minFraction: Double,
    val winnerList: List<DhondtWinner>, // sorted
) {
    val info = ContestInfo(
        name,
        id,
        parties.associate { Pair(it.name, it.id) },
        SocialChoiceFunction.DHONDT,
        nwinners = nseats,
        voteForN = 1,
        minFraction = minFraction,
    )
    val validVotes: Int = parties.sumOf { it.totalVotes } // denominator of minFraction
    // val winnerScores: List<DhondtScore>

    init {
        val totalVotes = validVotes + undervotes
        require (Nc == totalVotes) { "DhondtBuilder2 $Nc != $totalVotes" }

        // val sortedScores = assignWinners(parties, nseats, validVotes, minFraction, thresholdOverride, flip)

        val winners = winnerList.subList(0, nseats)
        val losers = winnerList.subList(nseats, winnerList.size)

        parties.forEach { party ->
            party.lastSeatWon = winners.filter { it.candidate == party.id }.maxOfOrNull { it.divisor }
            party.firstSeatLost = losers.filter { it.candidate == party.id }.minOfOrNull { it.divisor }
        }
    }

    fun build(): DhondtContest {
        val votes = parties.associate { Pair(it.id, it.totalVotes) }

        val contest = DhondtContest.fromVotes(
            info,
            votes,
            this.Nc,
            this.validVotes + this.undervotes,
        )

        // TODO why do we add the assorters after the constructor? probably not needed anymore
        //      or for serialization perhaps?

        contest.assorters.addAll(DhondtAssorter.makeDhondtAssorters(info, Nc, parties))
        val lastWinningScore = winnerList[nseats-1]
        val lastWinner = parties.find { it.id == lastWinningScore.candidate }!!

        // each party gets a Below or Above assertion
        parties.forEach { party ->
            if (party.isBelowMin) {
                // do we need this ??
                val bt = BelowThreshold.makeFromVotes(info, candId = party.id, votes, this.Nc,)
                val useAssorters = chooseBtOrDhs(bt, party, contest.winningCandidates(), lastWinner)
                contest.assorters.addAll(useAssorters)

            } else {
                contest.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc))
            }
        }

        return contest
    }

    fun chooseBtOrDhs(bt: BelowThreshold, partyBelowMin: DhondtParty, winners: List<DhondtParty>, lastWinner: DhondtParty): List<AssorterIF> {

        // is this party's total vote larger than the lastwinner ?
        //val fw = lastWinner.totalVotes / lastWinner.lastSeatWon!!.toDouble() // TODO check
        //if (partyBelowMin.totalVotes > fw) return listOf(bt)

        val partyCopy = DhondtParty(partyBelowMin)
        partyCopy.firstSeatLost = 1 // why ?

        // make assert for each winner
        val dhs = winners.map { winner ->
            DhondtAssorter.makeFrom(info, winner = winner, loser = partyCopy, Nc)
        }

        val minAssert = dhs.minByOrNull { it.noerror(true) }!!
        if (bt.noerror(true) > minAssert.noerror(true) ) {
            val lastWinnerAssert = dhs.find { it.winner == lastWinner.id }!!
            if (bt.noerror(true) < lastWinnerAssert.noerror(true)) {
                logger.debug {
                    "${info.name} Not replacing ${bt.shortName()} (noerror = ${bt.noerror(true)}) with " +
                            "DH assertions (minNoerror = ${minAssert.noerror(true)} for ${minAssert.shortName()})" +
                            " lastWinner = ${lastWinnerAssert.desc()}"
                }
            }
            return listOf(bt)
        }

        logger.debug {
            "${info.name} Replacing ${bt.shortName()} (noerror = ${bt.noerror(true)}) with " +
                    "DH assertions (minNoerror = ${minAssert.noerror(true)} for ${minAssert.shortName()})"
        }
        return dhs
    }
} */