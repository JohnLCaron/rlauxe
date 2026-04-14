package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.forEach
import kotlin.math.max


private val showDetails = false
private val useBt = true // always use Bt

// f_e,s = Te /d(s)
// e = partyId, s = seatno, score = Te /d(s)
data class DhondtScore(val candidate: Int, val score: Double, val divisor: Int) {
    var winningSeat: Int? = null
    fun setWinningSeat(ws: Int?): DhondtScore {
        this.winningSeat = ws; return this
    }

    override fun toString() = buildString {
        append("DhondtScore(candidate=$candidate, divisor=$divisor, score=${df(score)}")
        if (winningSeat != null) append(", winningSeat=$winningSeat")
        append(")")
    }

    fun showLoser(name: String, votes: Int) = buildString {
        append(" ${name}")
        append("/${nfn(divisor, 2)}, ")
        append(" ${nfn(votes, 6)}, ${nfn(score.toInt(), 6)}, ")
    }
}

data class DhondtCandidate(val name: String, val id: Int, val votes: Int) {
    var lastSeatWon: Int? = null // We
    var firstSeatLost: Int? = null // Le
    var isBelowMin = false

    constructor(id: Int, votes: Int) : this("party-$id", id, votes)

    // TODO remove
    fun setResults(results: DhondtBuilder) {
        if (showDetails) results.sortedScores.filter { it.candidate == this.id }.forEach { println(" ${it}") }

        lastSeatWon = results.winners.filter { it.candidate == this.id }.maxOfOrNull { it.divisor }
        firstSeatLost = results.losers.filter { it.candidate == this.id }.minOfOrNull { it.divisor }
    }
}

// building the inital contest in CreateBelgiumElection
fun makeDhondtContest(
    name: String,
    id: Int,
    parties: List<DhondtCandidate>,
    nseats: Int,
    Nc: Int,
    undervotes: Int,
    minFraction: Double,
): DHondtContest {

    /* CreateBelgiumElection
    val pcontest = makeDhondtBuilder(electionName, contestId, dhondtParties, nwinners, 0,.05)
    val totalVotes = belgiumElection.NrOfValidVotes // + belgiumElection.NrOfBlankVotes TODO undervotes = belgiumElection.NrOfBlankVotes
    val contest = pcontest.createContest(Nc = totalVotes, Ncast = totalVotes)
    */

    val builder = DhondtBuilder2(name, id, parties, nseats, Nc, undervotes, minFraction)
    return builder.build()
}

// side effect is to set party.lastSeatWon,firstSeatLost: could move that to  assignWinners2()
data class DhondtBuilder2(
    val name: String,
    val id: Int,
    val parties: List<DhondtCandidate>,
    val nseats: Int,
    val Nc: Int, // trusted upper limit; // TODO need phantoms also
    val undervotes: Int,
    val minFraction: Double,
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
    val validVotes: Int = parties.sumOf { it.votes } // denominator of minFraction
    val winnerScores: List<DhondtScore>

    init {
        val totalVotes = validVotes + undervotes
        require (Nc != totalVotes) { "DhondtBuilder2 $Nc != $totalVotes" }

        val (sortedScores, belowMinPctIn) = assignWinners2(parties, nseats, validVotes, minFraction, null)

        winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)

        parties.forEach { party ->
            party.lastSeatWon = winnerScores.filter { it.candidate == party.id }.maxOfOrNull { it.divisor }
            party.firstSeatLost = loserScores.filter { it.candidate == party.id }.minOfOrNull { it.divisor }
        }
    }

    fun build(): DHondtContest {

        val votes = parties.associate { Pair(it.id, it.votes) }

        val contest = DHondtContest(
            info,
            votes,
            this.Nc,
            this.validVotes + this.undervotes,
            null,
        )

        // TODO why do we add the assorters after the constructor? probably not needed anymore
        //      or for serialization perhapes?

        contest.assorters.addAll(DHondtAssorter.makeDhondtAssorters(info, Nc, parties))
        val lastWinningScore = winnerScores.last()
        val lastWinner = parties.find { it.id == lastWinningScore.candidate }!!

        // each party gets a Below or Above assertion
        parties.forEach { party ->
            if (party.isBelowMin) {
                // decide which is cheaper
                val bt = BelowThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc)

                val partyCopy = party.copy()
                partyCopy.firstSeatLost = 1
                val dh = DHondtAssorter.makeFrom(info, Nc, winner = lastWinner, loser = partyCopy)

                val useAssorter = if (useBt || (bt.noerror() > dh.noerror())) bt else dh
                contest.assorters.add(useAssorter)
            } else {
                contest.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc))
            }
        }

        return contest
    }
}

// side effect is to set party.isBelowMin
fun assignWinners2(
    parties: List<DhondtCandidate>,
    nseats: Int,
    validVotes: Int,        // denominator for minFraction
    minFraction: Double,
    belowMinPctIn: Set<Int>?,  // candidateIds under minFraction, if null then calculate
): Pair<List<DhondtScore>, Set<Int>> {

    val sortedScores = mutableListOf<DhondtScore>()
    val sortedRawScores = mutableListOf<DhondtScore>() // ??

    val belowMinPct = belowMinPctIn ?:
                      parties.filter { it.votes / validVotes.toDouble() < minFraction }.map { it.id }.toSet()
    // have to do this before winners are assigned
    parties.forEach { it.isBelowMin = belowMinPct.contains( it.id)  }

    // recreate the winners and losers
    parties.filter { !it.isBelowMin }.forEach { party ->
        repeat(nseats) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            sortedScores.add( DhondtScore(party.id, party.votes / divisor, seatno) )
        }
    }
    sortedScores.sortByDescending { it.score }

    var maxRound = 0
    repeat(nseats) { idx ->
        val score = sortedScores[idx]
        score.setWinningSeat(idx + 1)
        maxRound = max(maxRound, idx + 1)
    }

    // recreate the raw scores TODO why ?
    parties.forEach { party ->
        repeat(maxRound) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            sortedRawScores.add( DhondtScore(party.id, party.votes / divisor, seatno) )
        }
    }
    sortedRawScores.sortByDescending { it.score }

    return Pair(sortedScores, belowMinPct)
}