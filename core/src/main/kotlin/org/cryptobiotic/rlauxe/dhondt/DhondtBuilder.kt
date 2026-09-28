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
import kotlin.math.max

private val logger = KotlinLogging.logger("DHondtBuilder")
private val showDetails = false

// f_e,s = Te /d(s)
// e = partyId, s = seatno, score = Te /d(s)
data class DhondtCandidateScore(val partyId: Int, val totalVotes: Int, val divisor: Int) {
    val score = totalVotes/divisor.toDouble() // so its not in the hash

    var winningSeat: Int? = null
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

// mutable state !!
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

    /* CreateBelgiumElection
    val pcontest = makeDhondtBuilder(electionName, contestId, dhondtParties, nwinners, 0,.05)
    val totalVotes = belgiumElection.NrOfValidVotes // + belgiumElection.NrOfBlankVotes TODO undervotes = belgiumElection.NrOfBlankVotes
    val contest = pcontest.createContest(Nc = totalVotes, Ncast = totalVotes)
    */

    val builder = DhondtBuilder(name, id, parties, nseats, Nc, undervotes, minFraction)
    return builder.build()
}

// always go through DhondtBuilder
// side effect is to set party.lastSeatWon,firstSeatLost: could move that to  assignWinners2()
class DhondtBuilder(  // TODO ok to not be data class ??
    val name: String,
    val id: Int,
    val partyBs: List<DhondtPartyBuilder>,
    val nseats: Int,
    val Nc: Int, // trusted upper limit; // TODO need phantoms also
    val undervotes: Int,
    val minFraction: Double,
    val thresholdOverride: Set<Int>? = null,
    // flip: Boolean = false,
) {

    constructor(info: ContestInfo, partyBs: List<DhondtPartyBuilder>, Nc: Int, Ncast: Int, undervotes: Int)
        : this(info.name, info.id, partyBs, nseats=info.nwinners, Nc=Nc, undervotes=undervotes, minFraction = info.minFraction!!)

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

    init {
        val totalVotes = validVotes + undervotes
        require (Nc == totalVotes) { "DhondtBuilder2 $Nc != $totalVotes" }

        val sortedScores = assignWinners(partyBs, nseats, validVotes, minFraction, thresholdOverride, flip = false)

        winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)

        partyBs.forEach { party ->
            party.lastSeatWon = winnerScores.filter { it.partyId == party.id }.maxOfOrNull { it.divisor }
            party.firstSeatLost = loserScores.filter { it.partyId == party.id }.minOfOrNull { it.divisor }
        }
    }

    fun build(): DhondtContest {
        val votes = partyBs.associate { Pair(it.id, it.totalVotes) }
        val sortedScores = assignWinners(partyBs, info.nwinners, Nc, info.minFraction!!, thresholdOverride)
        val parties = partyBs.map { it.build() }

        val dcontest = DhondtContest(info,
            votes,
            this.Nc,
            Ncast = this.validVotes + this.undervotes,
            parties,
            sortedScores,
            thresholdOverride,
        )
        dcontest.assorters.addAll( DhondtAssorter.makeDhondtAssorters(info, Nc, parties) )

        // each party gets a Below or Above assertion
        val lastWinningScore = winnerScores.last()
        val lastWinner = parties.find { it.id == lastWinningScore.partyId }!!
        parties.forEach { party ->
            if (party.isBelowMin) {
                // decide if its cheaper to use DH assertions
                val bt = BelowThreshold.makeFromVotes(info, candId = party.id, votes, this.Nc,)
                val useAssorters = chooseBtOrDhs(bt, party, dcontest.winningParties(), lastWinner)
                dcontest.assorters.addAll(useAssorters)

            } else {
                dcontest.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc))
            }
        }

        return dcontest
    }

    fun chooseBtOrDhs(bt: BelowThreshold, partyBelowMin: DhondtParty, winners: List<DhondtParty>, lastWinner: DhondtParty): List<AssorterIF> {
        // is this party's total vote larger than the lastwinner ?
        val fw = lastWinner.totalVotes / lastWinner.lastSeatWon!!.toDouble()
        if (partyBelowMin.totalVotes > fw) return listOf(bt)

        val partyCopy = DhondtPartyBuilder(partyBelowMin)
        partyCopy.firstSeatLost = 1

        // make assert for each winer
        val dhs = winners.map { winner ->
            DhondtAssorter.makeFrom(info, winner = winner, loser = partyCopy.build(), Nc)
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

    companion object {
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

// return sortedScores and set of contests that didnt make threshold
fun assignWinners(
    parties: List<DhondtPartyBuilder>,
    nseats: Int,
    validVotes: Int,        // denominator for minFraction
    minFraction: Double,
    thresholdOverride: Set<Int>? = null,
    flip: Boolean = false,
): List<DhondtCandidateScore> {

    val sortedScores = mutableListOf<DhondtCandidateScore>()

    val belowMinPct = thresholdOverride ?: parties.filter { it.totalVotes / validVotes.toDouble() < minFraction }.map { it.id }.toSet()
    // remove threshold failures before winners are assigned
    parties.forEach { it.isBelowMin = belowMinPct.contains(it.id)  }

    parties.filter { !it.isBelowMin }.forEach { party ->
        repeat(nseats) { idx ->
            val divisor = idx + 1
            sortedScores.add( DhondtCandidateScore(party.id, party.totalVotes, divisor) )
        }
    }
    sortedScores.sortByDescending { it.score }

    if (flip) {
        val save1 = sortedScores[nseats-1]
        val save2 = sortedScores[nseats]
        sortedScores[nseats-1] = save2
        sortedScores[nseats] = save1
    }

    var maxRound = 0
    repeat(nseats) { idx ->
        val score = sortedScores[idx]
        score.setWinningSeat(idx + 1)
        maxRound = max(maxRound, idx + 1)
    }

    return sortedScores
}