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
    var belowMinPct = false

    constructor(id: Int, votes: Int) : this("party-$id", id, votes)

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
    builder.makeProtoAssorters()
    return builder.build()
}

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

    // just the dhondts
    val assorters = mutableListOf<AssorterBuilder2>()

    init {
        if (Nc != validVotes + undervotes)
            print("DhondtBuilder2 $Nc != ${validVotes + undervotes}")
        // private fun assignWinners2(
        //    name: String,
        //    id: Int,
        //    parties: List<DhondtCandidate>,
        //    nseats: Int,
        //    undervotes: Int,
        //    minFraction: Double,
        //)
        val (sortedScores, sortedRawScores) = assignWinners2(parties, nseats, validVotes, minFraction)

        winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)

        parties.forEach { party ->
            party.lastSeatWon = winnerScores.filter { it.candidate == party.id }.maxOfOrNull { it.divisor }
            party.firstSeatLost = loserScores.filter { it.candidate == party.id }.minOfOrNull { it.divisor }
        }

        //// where do these go ?? probably DhondtContest
        val winnerSeatsM = mutableMapOf<Int, Int>()
        sortedScores.filter { it.winningSeat != null }.forEach {
            val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
            winnerSeatsM[it.candidate] = count + 1
        }
        val winnerSeats = winnerSeatsM.toMap()
        val winners = winnerSeats.keys.toList()
        val losers = info.candidateIds.filter { !winners.contains(it) }
        val winnerNames = winners.map { info.candidateIdToName[it]!! }
    }

    fun makeProtoAssorters() {
        // Let f_e,s = Te /d(s) for entity e and seat s
        // f_A,WA > f_B,LB, so e = A and s = Wa

        // Section 5.2 eq (4)
        // Converting this into the notation of Section 3, expressing Equation 4 as a linear
        // assertion gives us, ∀A s.t. WA !=⊥, ∀B 6= A s.t. LB !=⊥,
        //   TA /d(WA ) − TB /d(LB ) > 0.

        // This is O(n^2)
        parties.forEach { winner ->
            if (winner.lastSeatWon != null) {
                parties.filter { it.id != winner.id }.forEach { loser ->
                    if (loser.firstSeatLost != null) {
                        val passorter = AssorterBuilder2(this, winner, loser)
                        assorters.add(passorter)
                    }
                }
            }
        }
    }

    fun build(): DHondtContest {

        val votes = parties.associate { Pair(it.id, it.votes) }

        val contest = DHondtContest(
            info,
            votes,
            this.Nc,
            this.validVotes + this.undervotes,
        )

        // TODO why do we add the assorters after the constructor? probably not needed anymore

        contest.assorters.addAll(assorters.map { it.makeAssorter(info) })
        val lastWinningScore = winnerScores.last()
        val lastWinner = parties.find { it.id == lastWinningScore.candidate }!!

        parties.forEach { party ->
            if (party.belowMinPct) {
                // decide which is cheaper
                val bt = BelowThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc)

                val partyCopy = party.copy()
                partyCopy.firstSeatLost = 1
                val dh = AssorterBuilder2(this, lastWinner, partyCopy).makeAssorter(info)

                val useAssorter = if (useBt || (bt.noerror() > dh.noerror())) bt else dh
                contest.assorters.add(useAssorter)
            } else {
                contest.assorters.add(AboveThreshold.makeFromVotes(info, partyId = party.id, votes, minFraction, this.Nc))
            }
        }

        return contest
    }
}


private fun assignWinners2(
    parties: List<DhondtCandidate>,
    nseats: Int,
    validVotes: Int, // denominator for minFraction
    minFraction: Double, // TODO else pass in candId set
): Pair<List<DhondtScore>, List<DhondtScore>> {

    val sortedScores = mutableListOf<DhondtScore>()
    val sortedRawScores = mutableListOf<DhondtScore>()

    // have to do this before winners are assigned
    parties.forEach { if (it.votes / validVotes.toDouble() < minFraction) it.belowMinPct = true }

    // recreate the winners and losers
    parties.filter { !it.belowMinPct }.forEach { party ->
        repeat(nseats) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            sortedScores.add(DhondtScore(party.id, party.votes / divisor, seatno))
        }
    }
    sortedScores.sortByDescending { it.score }

    var maxRound = 0
    repeat(nseats) { idx ->
        val score = sortedScores[idx]
        score.setWinningSeat(idx + 1)
        maxRound = max(maxRound, idx + 1)
    }

    // recreate the raw scores
    parties.forEach { party ->
        repeat(maxRound) { idx ->
            val seatno = idx + 1
            val divisor = seatno.toDouble()
            sortedRawScores.add(DhondtScore(party.id, party.votes / divisor, seatno))
        }
    }
    sortedRawScores.sortByDescending { it.score }

    val winnerSeatsM = mutableMapOf<Int, Int>()
    sortedScores.filter { it.winningSeat != null }.forEach {
        val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
        winnerSeatsM[it.candidate] = count + 1
    }

    return Pair(sortedScores, sortedRawScores)
}


data class AssorterBuilder2(
    val contest: DhondtBuilder2,
    val winner: DhondtCandidate,
    val loser: DhondtCandidate,
) {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val fw = winner.votes / winner.lastSeatWon!!.toDouble()
    val fl = loser.votes / loser.firstSeatLost!!.toDouble()
    val gmean = (fw - fl) / contest.Nc

    val lower = -1.0 / loser.firstSeatLost!!  // lower bound of g
    val upper = 1.0 / winner.lastSeatWon!!  // upper bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2
    val hmean = h(gmean)

    fun h(g: Double): Double = c * g + 0.5

    fun makeAssorter(info: ContestInfo) = DHondtAssorter(
        info,
        winner.id,
        loser.id,
        lastSeatWon = winner.lastSeatWon!!,
        firstSeatLost = loser.firstSeatLost!!
    ).setDilutedMean(hmean)
}