package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.Int
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("DHondtBuilder")
private val showDetails = false

// f_e,s = Te /d(s)
// e = partyId, s = seatno, score = Te /d(s)
data class DhondtCandidateScore(val partyId: Int, val totalVotes: Int, val divisor: Int) {
    val score = totalVotes/divisor.toDouble() // so its not in the hash

    var winningSeat: Int? = null  // TODO make immutable ??
    fun setWinningSeat(ws: Int?): DhondtCandidateScore {
        this.winningSeat = ws
        return this
    }

    override fun toString() = buildString {
        append("DhondtCandidateScore(partyId=$partyId, divisor=$divisor, score=${df(score)}")
        if (winningSeat != null) append(", winningSeat=$winningSeat")
        append(")")
    }

    fun showLoser(name: String, votes: Int) = buildString {
        append(" ${name}")
        append("/${nfn(divisor, 2)}, ")
        append(" ${nfn(votes, 6)}, ${nfn(score.toInt(), 6)}, ")
    }
}

// mutable state
class DhondtPartyBuilder(val partyName: String, val id: Int, val totalVotes: Int) {
    var lastSeatWon: Int? = null // We
    var firstSeatLost: Int? = null // Le
    var isBelowMin = false

    constructor(id: Int, votes: Int) : this("party-$id", id, votes)
    constructor(from: DhondtParty) : this(from.partyName, from.id, from.totalVotes) {
        lastSeatWon = from.lastSeatWon
        firstSeatLost = from.firstSeatLost
        isBelowMin = from.isBelowMin
    }

    override fun toString() ="DhondtPartyBuilder('$partyName' ($id) votes=$totalVotes below=$isBelowMin last/first=$lastSeatWon/$firstSeatLost)"

    fun build() = DhondtParty(partyName, id, totalVotes, lastSeatWon, firstSeatLost, isBelowMin)
}

// building the inital contest in CreateBelgiumElection
// TODO get rid of
fun makeDhondtContest(
    name: String,
    id: Int,
    parties: List<DhondtPartyBuilder>,
    nseats: Int,
    Nc: Int,
    undervotes: Int,
    minFraction: Double,
): DhondtContest {
    val builder = DhondtBuilder(name, id, parties, nseats, Nc, undervotes, minFraction)
    return builder.build()
}

// always go through DhondtBuilder
open class DhondtBuilder(  // TODO ok to not be data class ??
    val name: String,
    val id: Int,
    val partyBs: List<DhondtPartyBuilder>,
    val nseats: Int,
    val Nc: Int, // trusted upper limit; // TODO need phantoms also
    val undervotes: Int,
    val minFraction: Double,
    val thresholdOverride: Set<Int>? = null,
) {

    constructor(info: ContestInfo, partyBs: List<DhondtPartyBuilder>, Nc: Int, Ncast: Int, undervotes: Int)
        : this(info.name, info.id, partyBs, nseats=info.nwinners, Nc=Nc, undervotes=undervotes, minFraction = info.minFraction!!)

    constructor(dcontest: DhondtContest) : this(dcontest.name, dcontest.id, dcontest.parties.map { DhondtPartyBuilder(it) },
        dcontest.nseats, dcontest.Nc, dcontest.undervotes, dcontest.info.minFraction!!)

    val info = ContestInfo(
        name,
        id,
        partyBs.associate { Pair(it.partyName, it.id) },
        SocialChoiceFunction.DHONDT,
        nwinners = nseats,
        voteForN = 1,
        minFraction = minFraction,
    )
    val validVotes: Int = partyBs.sumOf { it.totalVotes } // denominator of minFraction
    val winnerScores: List<DhondtCandidateScore>
    val winnerSeatCount: Map<Int, Int> // partyId -> nseats
    val sortedScores: List<DhondtCandidateScore>

    init {
        val totalVotes = validVotes + undervotes
        require (Nc == totalVotes) { "DhondtBuilder2 $Nc != $totalVotes" }

        // use validVotes, not Nc
        sortedScores = createCandidateScores(partyBs, nseats, validVotes, minFraction, thresholdOverride)

        winnerScores = sortedScores.subList(0, nseats)

        /* old way
        val loserScores = sortedScores.subList(nseats, sortedScores.size)
        partyBs.forEach { party ->
            party.lastSeatWon = winnerScores.filter { it.partyId == party.id }.maxOfOrNull { it.divisor } // may be null
            party.firstSeatLost = loserScores.filter { it.partyId == party.id }.minOfOrNull { it.divisor } // may be null
        } */

        // could also do vanessa's way; 0 = no seats won
        winnerSeatCount = winnerSeatCount(sortedScores, partyBs.map { it.id })
        partyBs.forEach { partybs ->
            partybs.lastSeatWon = winnerSeatCount[partybs.id] // last seat won = number of seats won; now 0 mean "none" instead of null
            partybs.firstSeatLost = winnerSeatCount[partybs.id]!! + 1 // first seat lost
        }
    }

    // allow build to be overridden

    open fun build(): DhondtContest {
        val votes = partyBs.associate { Pair(it.id, it.totalVotes) }
        val parties = partyBs.map { it.build() }

        // define W the set of parties that have at least one reported winner
        // define L the set of parties that have at least one reported loser
        val winningParties = parties.filter { it.lastSeatWon!! > 0 }.toSet()
        val losingParties = parties // TODO allow setting |P|

        /* old way  This is O(n^2)
        val assortersOld = mutableListOf<DhondtAssorter>()
        parties.forEach { winner ->
            if (winner.lastSeatWon!! > 0) {
                parties.filter { it.id != winner.id }.forEach { loser ->
                    if (loser.firstSeatLost != null) {
                        val passorter = makeFrom(info, winner, loser, Nc)
                        assortersOld.add(passorter)
                    }
                }
            }
        } */

        val assorters = mutableListOf<AssorterIF>()

        // AT(A) for all A in Winners (2.5 prop 1 (1))
        winningParties.forEach { winner ->
            assorters.add(AboveThreshold.makeFromVotes(info, partyId = winner.id, votes, minFraction, this.Nc))
        }

        // BT(B) OR ( AND(DH_AB) for all A in Winners) for all B in Losers (2.5 prop 1 (2))
        losingParties.forEach { loser ->
            assorters.addAll(btOrBhs(winningParties, loser, votes))
        }

        return DhondtContest(info,
            votes,
            this.Nc,
            Ncast = this.validVotes + this.undervotes,
            parties,
            sortedScores,
            assorters,
            thresholdOverride,
        )
    }

    // BT(B) OR ( AND(DH_AB) for all A in Winners) for loser = B
    fun btOrBhs(winningParties: Set<DhondtParty>, loser: DhondtParty, votes: Map<Int, Int>): List<AssorterIF> {
        // make DH assertions for this loser and all winners
        val dhs = winningParties.filter{it.id != loser.id}.map { winner ->
            makeDhAssorterFromParty(info, winner = winner, loser = loser, Nc)
        }

        if (!loser.isBelowMin) {
            return dhs
        } else {
            val bt = BelowThreshold.makeFromVotes(info, candId = loser.id, votes, this.Nc,)
            // use dhs if their minimum error is larger than BT
            val minAssert = dhs.minByOrNull { it.noerror(true) }!!
            if (bt.noerror(true) < minAssert.noerror(true) ) {
                logger.debug {
                    "${info.name} Replacing ${bt.shortName()} (noerror = ${bt.noerror(true)}) with " +
                            "DH assertions (minNoerror = ${minAssert.noerror(true)} for ${minAssert.shortName()})"
                }
                return dhs
            } else {
                return listOf(bt)
            }
        }
    }

    fun btOrBh(winner: DhondtParty, winnerDivisor: Int, loser: DhondtParty, loserDivisor: Int, votes: Map<Int, Int>): AssorterIF {
        // make DH assertions for this loser and all winners
        val dh = makeDhAssorterFromDivisors(info, winner, winnerDivisor, loser, loserDivisor, Nc)

        if (!loser.isBelowMin) {
            return dh
        } else {
            val bt = BelowThreshold.makeFromVotes(info, candId = loser.id, votes, this.Nc,)
            if (bt.noerror(true) < dh.noerror(true) ) {
                return dh
            } else {
                return bt
            }
        }
    }

    companion object {

        // use preset winner.lastSeatWon / loser.firstSeatLost
        fun makeDhAssorterFromParty(info: ContestInfo, winner: DhondtParty, loser: DhondtParty, Nc: Int, Npop: Int?=null): DhondtAssorter {
            // Let f_e,s = Te/d(s) for entity e and seat s
            // f_A,WA > f_B,LB, so e = A and s = Wa

            return makeDhAssorterFromDivisors(info, winner, winner.lastSeatWon!!, loser, loser.firstSeatLost!!, Nc, Npop)
        }

        // uexplicitly set the divisors
        fun makeDhAssorterFromDivisors(info: ContestInfo, winner: DhondtParty, winnerDivisor: Int, loser: DhondtParty, loserDivisor: Int, Nc: Int, Npop: Int?=null):
                DhondtAssorter {
            val fw = winner.totalVotes / winnerDivisor.toDouble()
            val fl = loser.totalVotes / loserDivisor.toDouble()
            val voteDiff = (fw - fl)

            val lower = -1.0 / loserDivisor  // lower bound of g
            val upper = 1.0 / winnerDivisor  // upper bound of g
            val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
            val hmeanReported = c * voteDiff/Nc + 0.5
            val hmeanDiluted = c * voteDiff/(Npop ?: Nc) + 0.5

            if (winner.id == loser.id)
                print("")

            return DhondtAssorter(
                info,
                winner.id,
                loser.id,
                winnerDivisor = winnerDivisor,
                loserDivisor = loserDivisor
            ).setMeans(hmeanReported, hmeanDiluted)
        }

        // called by ContestJson to deserialize
        fun fromVotes(info: ContestInfo, votes: Map<Int, Int>, Nc: Int, Ncast: Int, undervotes: Int): DhondtBuilder {
            // recreate the parties from the votes. hmmmm what could go wrong ??
            val parties = info.candidateIds.map { id ->
                DhondtPartyBuilder(info.candidateIdToName[id]!!, id, votes[id]!!)
            }
            return DhondtBuilder(info, parties, Nc, Ncast, undervotes)
        }
    }
}

// create enough candidate scores to include all losing parties. exclude parties below threshold. sort by score.
// set isBelowMin for each party
fun createCandidateScores(
    parties: List<DhondtPartyBuilder>,
    nseats: Int,
    validVotes: Int,        // denominator for calculation of theshold is validVotes, excluding undervotes
    minFraction: Double,
    thresholdOverride: Set<Int>? = null,
    flip: Boolean = false, // used ??
): List<DhondtCandidateScore> {

    val sortedScores = mutableListOf<DhondtCandidateScore>()

    // calculate below Threshold unless overridden
    parties.forEach { it.isBelowMin = false }
    val belowMinPct = thresholdOverride ?: parties.filter { it.totalVotes / validVotes.toDouble() < minFraction }.map { it.id }.toSet()
    parties.forEach { it.isBelowMin = belowMinPct.contains(it.id) }

    // remove threshold failures before winners are assigned
    // adding nseats for each party - could limit to first loser if needed
    parties.filter { !it.isBelowMin }.forEach { party ->
        repeat(nseats) { idx ->
            val divisor = idx + 1
            sortedScores.add(DhondtCandidateScore(party.id, party.totalVotes, divisor))
        }
    }
    sortedScores.sortByDescending { it.score }

    if (flip) { // TODO get rid of this
        val save1 = sortedScores[nseats - 1]
        sortedScores[nseats - 1] = sortedScores[nseats]
        sortedScores[nseats] = save1
    }

    repeat(nseats) { idx ->
        sortedScores[idx].setWinningSeat(idx + 1)
    }

    return sortedScores
}

// party id -> nseats won
// this is s(P) -> N in vanessa's paper section 2.3
fun winnerSeatCount(sortedScores: List<DhondtCandidateScore>, allParties: List<Int>): Map<Int, Int> {

    val winnerSeatCount= mutableMapOf<Int, Int>()
    allParties.forEach { winnerSeatCount[it] = 0} // make sure losers have nseats = 0
    sortedScores.filter { it.winningSeat != null }.forEach {
        val count = winnerSeatCount.getOrPut(it.partyId) { 0 }
        winnerSeatCount[it.partyId] = count + 1
    }

    return winnerSeatCount
}