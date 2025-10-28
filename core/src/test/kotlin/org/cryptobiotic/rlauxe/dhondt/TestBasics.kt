package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBasics {

    @Test
    fun testAssortAvg() {
        testAssortAvg(listOf(Party(1, 10), Party(2, 20), Party(3, 30)), 2)
        testAssortAvg(listOf(Party(1, 10000), Party(2, 6000), Party(3, 1500)), 8)
    }

    fun testAssortAvg(parties: List<Party>, nseats: Int) {
        val assorters = testTabulateVotes(parties, nseats)
        assorters.forEach { it ->
            val (gavg, havg) = it.getAssortAvg(parties)
            println("${it.show()}")
            println("             gavg=${gavg.show2()}")
            println("             havg=${havg.show2()}")
            assertEquals(it.margin, gavg.mean, doublePrecision)
            assertEquals(it.h(it.margin), havg.mean, doublePrecision)

            // TODO: in plurality, the avg assort value = margin2mean( assort.margin). here, avg assort value = assort margin. maybe a different choice of c ?
            // TODO margin2mean and mean2margin assumes lower = 1. is that wrong ?
        }
    }


    @Test
    fun testTabulateVotes() {
        testTabulateVotes(listOf(Party(1, 10000), Party(2, 6000), Party(3, 1500)), 8)
        testTabulateVotes(listOf(Party(1, 11000), Party(2, 7000), Party(3, 2500)), 11)
    }

    fun testTabulateVotes(parties: List<Party>, nseats: Int): List<DHondtAssorter>{
        println("---------------------------------------------------------------")
        val Nc = parties.sumOf { it.votes }
        parties.forEach { it.Nc = Nc  }

        val dhResult = assignWinners(parties, nseats = nseats)
        parties.forEach {
            it.setResults(dhResult)
            println()
        }

        val assorters= makeAssorters(parties)
        assorters.forEach { println(it.show()) }
        println()
        return assorters
    }

    fun assignWinners(parties: List<Party>, nseats : Int): DHondtResult {
        val avgs = mutableListOf<Fes>()

        parties.forEach { party->
            repeat(nseats) { idx ->
                val seatno = idx + 1
                val divisor = seatno.toDouble()
                avgs.add(Fes(party.id, party.votes / divisor, seatno))
            }
        }
        avgs.sortByDescending { it.avg }

        repeat(nseats) { idx ->
            val avg = avgs[idx]
            avg.winningSeat = idx + 1
            println(" ${idx+1}: ${avgs[idx]}")
        }
        println()
        return DHondtResult(avgs, nseats)
    }

    fun makeAssorters(parties: List<Party>): List<DHondtAssorter> {
        // Let f_e,s = Te /d(s) for entity e and seat s
        // f_A,WA > f_B,LB, so e = A and s = Wa

        val assorters = mutableListOf<DHondtAssorter>()
        parties.forEach { winner ->
            if (winner.lastSeatWon != null) {
                parties.filter { it.id != winner.id }.forEach { loser ->
                    if (loser.firstSeatLost != null) {
                        assorters.add(DHondtAssorter(winner, loser))
                    }
                }
            }
        }
        return assorters
    }
}

data class Party(val id: Int, val votes: Int) {
    var seatsWon: Int = 0 // We
    var lastSeatWon: Int? = null // We
    var firstSeatLost: Int? = null // Le
    var Nc: Int = 0

    fun setResults(results: DHondtResult) {
        results.sortedAvgs.filter{ it.partyId == this.id }.forEach { println(" ${it}") }

        seatsWon = results.winners.count { it.partyId == this.id }
        lastSeatWon = results.winners.filter { it.partyId == this.id }.maxOfOrNull { it.seatno }
        firstSeatLost = results.losers.filter { it.partyId == this.id }.minOfOrNull { it.seatno }
        println(this)
    }

    override fun toString(): String {
        val fw = if (this.lastSeatWon == null) 0.0 else this.votes / this.lastSeatWon!!.toDouble()
        val fl = if (this.firstSeatLost == null) 0.0 else this.votes / this.firstSeatLost!!.toDouble()
        return "Party(id=$id, votes=$votes, lastSeatWon=$lastSeatWon, firstSeatLost=$firstSeatLost, fw=${df(fw)} fl=${df(fl)}"
    }

}

// f_e,s = Te /d(s)
data class Fes(val partyId: Int, val avg: Double, val seatno: Int) {
    var winningSeat: Int? = null // probably not needed
    override fun toString() = buildString {
        append("Fes(partyId=$partyId, avg=${df(avg)}, seatno=$seatno")
        if (winningSeat != null) append(", winningSeat=$winningSeat")
        append (")")
    }

}

data class DHondtResult(val sortedAvgs: List<Fes>, val nseats: Int) {
    val winners = sortedAvgs.subList(0, nseats)
    val losers = sortedAvgs.subList(nseats, sortedAvgs.size)
}

data class DHondtAssorter(val winner: Party, val loser: Party) {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val fw = winner.votes / winner.lastSeatWon!!.toDouble()
    val fl = loser.votes / loser.firstSeatLost!!.toDouble()
    val margin = (fw - fl)/winner.Nc

    val a = -1.0 / loser.firstSeatLost!!  // -1/d(WB): lower bound of g
    val c = -1.0 / (2 * a)  // affine transform h = c * g + 1/2

    fun g(partyVote: Int): Double {
        return if (partyVote == winner.id) 1.0 / winner.lastSeatWon!!
            else if (partyVote == loser.id) -1.0 / loser.firstSeatLost!!
            else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    fun h(g: Double): Double {
        return c * g + 0.5
    }

    fun show() = "(${winner.id}/${loser.id}) votes=${winner.votes}/${loser.votes} denom=${winner.lastSeatWon}/${loser.firstSeatLost} " +
            "a = $a margin=${df(margin)} hmargin=${df(h(margin))}"

    // assumes no undervotes
    fun getAssortAvg(parties: List<Party>): Pair<Welford, Welford> {
        val gavg = Welford()
        val havg = Welford()
        parties.forEach { party ->
            repeat(party.votes) {
                // println("  (${winner.id}/${loser.id}) voteFor= ${party.id} g=${g(party.id)} h=${h(party.id)}")
                gavg.update(g(party.id))
                havg.update(h(party.id))
            }
        }
        return Pair(gavg, havg)
    }
}

/*




 */