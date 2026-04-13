package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundResult
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max


// ### Section 5.1 highest averages from Proportional paper
//
//A highest averages method is parameterized by a set of divisors d(1), d(2), . . . d(S) where S is the number of seats.
//The divisors for D’Hondt are d(i) = i. Sainte-Laguë has divisors d(i) = 2i − 1.
//
//Define
//
//    fe,s = Te/d(s) for entity e and seat s.
//
// ### Section 5.2 Simple D’Hondt: Party-only voting
//
//In the simplest form of highest averages methods, seats are allocated to each
//entity (party) based on individual entity tallies. Let We be the number of seats
//won and Le the number of the first seat lost by entity e. That is:
//
//    We = max{s : (e, s) ∈ W}; ⊥ if e has no winners. this is e's lowest winner.
//    Le = min{s : (e, s) !∈ W}; ⊥ if e won all the seats. this is e's highest loser.
//
//The inequalities that define the winners are, for all parties A with at least
//one winner, for all parties B (different from A) with at least one loser, as follows:
//
//    fA,WA > fB,LB    A’s lowest winner beat party B’s highest loser
//    TA/d(WA) > TB/d(LB)
//    TA/d(WA) - TB/d(LB) > 0
//
//From this, we define the proto-assorter for any ballot b as
//
//    g_AB(b) = 1/d(WA) if b is a vote for A
//            = -1/d(WB) if b is a vote for B
//            = 0 otherwisa
//
//    or equivilantly, g_AB(b) = bA/d(WA) - bB/d(WB)
//
//g lower bound is -1/d(WB) = -1/first (lowest winner)
//g upper bound is 1/d(WA)  = 1/last   (highest loser)
//c = -1.0 / (2 * lower) = first/2
//h upper bound is h(g upper) = h(1/last) * c + 1/2 = (1/last) * first/2 + 1/2 = (first/last+1)/2
//
//first and last both range from 1 to nseats, so
//    min upper is (1/nseats + 1)/2 which is between 1/2 and 1
//    max upper is (nseats + 1)/2 which is >= 1


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
class DHondtContest(
    info: ContestInfo,
    voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    Nc: Int,                // trusted maximum ballots/cards that contain this contest
    Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
): Contest(info, voteInput, Nc, Ncast) {
    val nvotes = votes.values.sum()

    override fun Nc() = Nc
    override fun Nphantoms() = Nc - Ncast
    override fun Nundervotes() = undervotes
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val belowMinPct: Set<Int>// candidateIds under minFraction
    val winnerSeats : Map<Int, Int> // cand, nseats

    // dhondts and threshold assorters; these are set at creation, but not serialized, so cant assume they exist
    val assorters = mutableListOf<AssorterIF>()

    val dhondtCandidates = mutableMapOf<String, DhondtCandidate>()
    val sortedScores = mutableListOf<DhondtScore>()
    val sortedRawScores = mutableListOf<DhondtScore>()

    init {
        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        require(info.minFraction != null)
        val useMin = info.minFraction
        val belowMinPctM = mutableListOf<Int>()  // candidate ids below min pct M = mutable
        votes.toList().filter{ it.second.toDouble()/nvotes < useMin }.forEach {
            belowMinPctM.add(it.first)
        }
        belowMinPctM.sort() // why sort ?
        belowMinPct = belowMinPctM.toSet()

        // recreate the parties
        val parties = info.candidateIds.map { id ->
            DhondtCandidate(info.candidateIdToName[id]!!, id, votes[id]!!)
        }
        // could use belowMinPct; these dont gave first/last yet
        parties.forEach { if (it.votes/nvotes.toDouble() < info.minFraction) it.belowMinPct = true }

        // recreate the winners and losers
        val nseats = info.nwinners
        parties.filter{ !it.belowMinPct }.forEach { party->
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
            maxRound = max(maxRound, idx+1)
        }
        val winnerScores = sortedScores.subList(0, nseats)
        val loserScores = sortedScores.subList(nseats, sortedScores.size)

        // recreate the raw scores
        parties.forEach { party->
            repeat(maxRound) { idx ->
                val seatno = idx + 1
                val divisor = seatno.toDouble()
                sortedRawScores.add(DhondtScore(party.id, party.votes / divisor, seatno))
            }
        }
        sortedRawScores.sortByDescending { it.score }

        val winnerSeatsM= mutableMapOf<Int, Int>()
        sortedScores.filter { it.winningSeat != null }.forEach {
            val count = winnerSeatsM.getOrPut(it.candidate) { 0 }
            winnerSeatsM[it.candidate] = count + 1
        }
        winnerSeats = winnerSeatsM.toMap()
        winners = winnerSeats.keys.toList()
        losers = info.candidateIds.filter { !winners.contains(it) }
        winnerNames = winners.map { info.candidateIdToName[it]!! }
    }

    override fun recountMargin(assorter: AssorterIF): Double {
        return when (assorter) {
            is DHondtAssorter -> {
                val winnerScore = votes[assorter.winner()]!! / assorter.lastSeatWon.toDouble()
                val loserScore = votes[assorter.loser()]!! / assorter.firstSeatLost.toDouble()
                (winnerScore - loserScore) / winnerScore
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes ??
                assorter.t - votes[assorter.winner()]!! / nvotes.toDouble()
            }

            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                votes[assorter.winner()]!! / nvotes.toDouble() - assorter.t
            }
            else -> throw RuntimeException()
        }
    }

    override fun showAssertionDifficulty(assorter: AssorterIF): String {
        return when (assorter) {
            is DHondtAssorter -> {
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!)
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, nvotes)
            }
            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.showAssertionDifficulty(votes[assorter.winner()]!!, nvotes)
            }
            else -> throw RuntimeException()
        }
    }

    fun difficulty(assorter: AssorterIF): Double {
        return when (assorter) {
            is DHondtAssorter -> {
                assorter.difficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!)
            }
            is BelowThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            is AboveThreshold -> {
                // val nvotes = votes.values.sum() does not include undervotes
                assorter.difficulty(votes[assorter.winner()]!!, nvotes)
            }
            else -> throw RuntimeException("unknown assorter type= ${assorter.javaClass.simpleName}")
        }
    }

    override fun marginInVotes(assorter: AssorterIF): Int {
        return when (assorter) {
            is DHondtAssorter -> {
                roundToClosest(assorter.difficulty(votes[assorter.winner()]!!, votes[assorter.loser()]!!))
            }
            is BelowThreshold -> {
                roundToClosest(assorter.t * nvotes- votes[assorter.winner()]!!)
            }
            is AboveThreshold -> {
                roundToClosest(votes[assorter.winner()]!! - assorter.t * nvotes)
            }
            else -> throw RuntimeException("unknown assorter type= ${assorter.javaClass.simpleName}")
        }
    }

    override fun show() = buildString {
        appendLine(super.show())
        append("   nseats=${winnerSeats.values.sum()} winners=${winnerSeats} belowMin=${belowMinPct} threshold=${info.minFraction} minVotes=${roundUp(info.minFraction!! * nvotes)}")
    }

    override fun showCandidates() = buildString {
        val width0 = 20
        val width = 12
        val maxRound = sortedScores.filter{ it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1

        appendLine()
        append("candidate ${trunc("Round", width0 - "candidate".length + 3)}:")
        for (round in 1 .. maxRound) {
            append("${nfn(round, width)} |")
        }
        appendLine()

        info.candidateIds.forEach { id ->
            val rounds = sortedScores.filter { it.candidate == id }.map { Dround(id, it.score, it.divisor, it.winningSeat) }
            val below = if (belowMinPct.contains(id)) "*" else " "
            val candName = "${nfn(id, 2)} ${trunc(info.candidateIdToName[id]!!, width0)}$below"
            append(showCandidate(candName, votes[id]!!, maxRound, rounds, width))
        }
        appendLine("\n* failed threshold")
    }

    fun showCandidate(candName: String, candTotal: Int, maxRound: Int, rounds: List<Dround>, width:Int) = buildString {
        append("${candName}:")
        for (round in 1 .. maxRound) {
            val candRound = rounds.find { it.round == round }
            if (candRound == null) {
                val score = candTotal / round
                append("${nfn(score, width)} |")
                // append("${trunc(" ", width)}|")
            } else {
                val winner = if (candRound.winningSeat != null) " (${candRound.winningSeat})" else ""
                val entry = "${candRound.score.toInt()}$winner"
                append("${sfn(entry, width)} |")
            }
        }
        appendLine()
    }

    data class Dround(val candId: Int, val score: Double, val round: Int, val winningSeat: Int?)

    fun showAssertions(rounds: List<AuditRound>) = buildString {
        val lastAssertionRounds = mutableMapOf<String, AssertionRound>()
        rounds.map {
            val contestRound = it.contestRounds.find { cr -> cr.id == id }!!
            contestRound.assertionRounds.forEach { ar ->
                lastAssertionRounds[ar.assertion.assorter.hashcodeDesc()] = ar
            }
        }

        val candNameWidth = 20
        val width = 12
        val maxRound = sortedScores.filter { it.winningSeat != null }.maxOfOrNull { it.divisor }!! + 1
        val nseats = winnerSeats.values.sum()

        append(" seat ${sfn("winner", candNameWidth - 3)}/round     ${sfn("nvotes", 6)}, ")
        append("${sfn(" score", 6)},  ${sfn("voteDiff", 6)}, ")
        appendLine()

        var prev: Int? = null
        repeat(nseats) { idx ->
            val score = sortedScores[idx]
            // sortedRawScores.filter{ it.divisor <= maxRound }.forEachIndexed { idx, score ->
            val candId = score.candidate
            if (idx < nseats) append(" (${nfn(idx + 1, 2)}) ") else append("      ")
            val below = if (belowMinPct.contains(candId)) "*" else " "
            append(" ${trunc(info.candidateIdToName[candId]!!, candNameWidth)}$below")
            append("/${nfn(score.divisor, 2)}, ")
            append(" ${nfn(votes[candId]!!, 6)}, ${nfn(score.score.toInt(), 6)}, ")

            if (prev != null) append(" ${nfn(prev - score.score.toInt(), 6)},")
            prev = score.score.toInt()
            appendLine()
        }
        appendLine("winners=${winnerSeats}")
        appendLine()

        val armsMap = mutableMapOf<Int, AssertionRiskGroup>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter is DHondtAssorter) {
                val loserId = assorter.loser()
                val loserScore = sortedScores.find { it.divisor == assorter.firstSeatLost && it.candidate == loserId }!!
                val group = armsMap.getOrPut(loserId) { AssertionRiskGroup(loserId) }
                group.arms.add(AssertionRiskMargin(ar, ar.assertion.assorter, loserScore, ar.auditResult!!))
            }
        }
        if (armsMap.isNotEmpty()) {
            val sortedGroups = armsMap.values.toList().sortedByDescending { it -> it.highScore() }
            append(showAssertionsAtRisk(sortedGroups, candNameWidth))
        }
        appendLine()

        val thrashers = mutableListOf<AssertionThrasher>()
        lastAssertionRounds.forEach { (key, ar) ->
            val assorter = ar.assertion.assorter
            val risk = ar.auditResult?.pmin ?: Double.NaN
            if (risk > .05 && assorter !is DHondtAssorter) {
                thrashers.add(AssertionThrasher(ar, ar.assertion.assorter, ar.auditResult))
            }
        }
        if (thrashers.isNotEmpty()) {
            appendLine("------------------------------------------------------------------------------")
            append(showAssertionThrashers(thrashers, candNameWidth))
            val thrasherIds = thrashers.map { it.assorter.winner() }.toSet()
            val alt = AltDhondt(info, votes, belowMinPct - thrasherIds)
            append(alt.showAssertions(lastAssertionRounds))
        }
    }


    data class AssertionRiskGroup(val loserId: Int) {
        val arms = mutableListOf<AssertionRiskMargin>()
        fun highScore(): Double {
            val wtf = arms.maxOfOrNull { it.loserScore.score }
            return wtf ?: 0.0
        }
        fun sortedArms() = arms.sortedBy { it.nomargin }
    }

    data class AssertionRiskMargin(val ar: AssertionRound, val assorter: DHondtAssorter, val loserScore: DhondtScore, val auditResult: AuditRoundResult?) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val risk = ar.auditResult?.pmin ?: Double.NaN
        val nmvrs = ar.auditResult?.samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }
    }

    fun showAssertionsAtRisk(sortedGroups: List<AssertionRiskGroup>, candNameWidth: Int) = buildString {

        appendLine("Contested          loser/round    nvotes,  score, voteDiff,  noerror, nomargin, estSamples, actSamples,   risk, assertion")

        sortedGroups.forEach {  group ->
            group.sortedArms().forEachIndexed { idx, arm ->
                val assorter = arm.assorter
                val candId = assorter.loser()
                append("       ")
                if (idx == 0) {
                    append(arm.loserScore.showLoser(trunc(info.candidateIdToName[candId]!!, candNameWidth), votes[candId]!!))
                } else {
                    append("                                           ")
                }
                append(" ${nfn(marginInVotes(assorter), 7)}, ${dfn(assorter.noerror(), 6)},   ${dfn(arm.nomargin, 4)}, ")
                append(" ${nfn(arm.estMvrs(), 8)}, ${nfn(arm.nmvrs, 8)},    ${dfn(arm.risk, 4)},")
                    append(" winner ${assorter.winnerNameRound()} loser ${assorter.loserNameRound()}")
                appendLine()
           }
        }
    }

    inner class AssertionThrasher(val ar: AssertionRound, val assorter: AssorterIF, val auditResult: AuditRoundResult?) {
        val nomargin = 2.0 * assorter.noerror() - 1.0
        val risk = ar.auditResult?.pmin ?: Double.NaN
        val nmvrs = ar.auditResult?.samplesUsed ?: 0
        fun estMvrs(): Int  {
            // payoff_noerror = (1 + λ * (noerror − 1/2))  ;  (µ_i is approximately 1/2)
            // payoff_noerror^n > 1/alpha
            // n = 1/ln(alpha) / ln(λ * (noerror − 1/2)); noerror − 1/2 = nomargin/2
            // TODO
            val maxLoss: Double = 1.0 / 1.03905
            return roundUp(estSamples(2*maxLoss, nomargin, .05)) // =  -ln(alpha) / ln(1.0 + bet * nomargin/2)
        }

        override fun toString() = buildString {
            append("${assorter.shortName()}: ")
            append(" ${nfn(marginInVotes(assorter), 7)}, ${dfn(nomargin, 6)}, ")
            append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
        }

    }

    fun showAssertionThrashers(thrashers: List<AssertionThrasher>, candNameWidth: Int) = buildString {
        appendLine("Thrashers              marginInVotes, nomargin, estSamples, actSamples,   risk")
        thrashers.forEach {  thrasher ->
            appendLine(thrasher)
        }
    }


    // create a cvr for each vote
    fun createSimulatedCvrs(): List<Cvr> {
        val cvrs = mutableListOf<Cvr>()
        var count=0
        this.votes.forEach { (candid, nvotes) ->
            repeat(nvotes) {
                count++
                cvrs.add( Cvr("cvr$count", mapOf(id to intArrayOf(candid)), poolId=info.id))
            }
        }
        repeat(undervotes) {
            count++
            cvrs.add( Cvr("undervote$count", mapOf(id to IntArray(0)), poolId=info.id))
        }
        repeat(Nphantoms()) {
            count++
            cvrs.add( Cvr("phantom$count", mapOf(id to IntArray(0)), poolId=info.id, phantom=true))
        }
        cvrs.shuffle()
        return cvrs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtContest) return false
        if (!super.equals(other)) return false

        if (sortedScores != other.sortedScores) return false
        if (belowMinPct != other.belowMinPct) return false
        if (winnerSeats != other.winnerSeats) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + sortedScores.hashCode()
        result = 31 * result + belowMinPct.hashCode()
        result = 31 * result + winnerSeats.hashCode()
        return result
    }
}

// winner,loser: candidate ids
// lastSeatWon: last seat won by winner
// firstSeatLost: last seat lost by loser
data class DHondtAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int): AssorterIF  {
    val upperg = 1.0 / lastSeatWon  // upper bound of g = 1/d(WA)  = 1/lastSeatWon   (highest loser)
    val lowerg = -1.0 / firstSeatLost  // lower bound of g = -1/d(WB) = -1/firstSeatLost (lowest winner)
    val c = -1.0 / (2 * lowerg)  // first/2
    private var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): DHondtAssorter {
        this.dilutedMean = mean
        return this
    }

    fun g(partyVote: Int): Double {
        return if (partyVote == winner) upperg
            else if (partyVote == loser) lowerg
            else 0.0
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    // l = h(-1/first) = -1/first * first/2 + 1/2 = 0
    // u = h(1/last) = 1/last * first/2 + 1/2 = (firstSeatLost/lastSeatWon+1)/2
    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    // (first/last+1)/2
    override fun upperBound() = h2(upperg)
    override fun winner() = winner
    override fun loser() = loser
    override fun dilutedMean() = dilutedMean
    override fun dilutedMargin() = mean2margin(dilutedMean)

    // [ 0, .5, u]
    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(info.id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val cands = cvr.votes(info.id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun desc() = buildString {
        append("${shortName()}: winner '${info.candidateIdToName[winner()]}'/$firstSeatLost")
        append(" loser '${info.candidateIdToName[loser()]}/$lastSeatWon' upperBound=${df(upperBound())}")
    }
    override fun shortName() = "DHondt w/l='${info.candidateIdToName[winner()]}'/'${info.candidateIdToName[loser()]}'"
    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization

    fun winnerNameRound() =  "${info.candidateIdToName[winner()]}/$lastSeatWon"
    fun loserNameRound() =  "${info.candidateIdToName[loser()]}/$firstSeatLost"

    fun showAssertionDifficulty(votesForWinner: Int, votesForLoser: Int): String {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return "fw=${dfn(winnerScore, 1)} fl=${dfn(loserScore, 1)} fw-fl=${dfn(winnerScore - loserScore, 0)}"
    }

    fun difficulty(votesForWinner: Int, votesForLoser: Int): Double {
        val winnerScore = votesForWinner / lastSeatWon.toDouble()
        val loserScore = votesForLoser / firstSeatLost.toDouble()
        return winnerScore - loserScore
    }

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val loserVotes = useVotes[loser()] ?: 0

        val fw = winnerVotes / lastSeatWon.toDouble()
        val fl = loserVotes / firstSeatLost.toDouble()

        val gmean = (fw - fl)/N
        val hmean = h2(gmean)
        val margin = mean2margin(hmean)

        return margin
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DHondtAssorter) return false

        if (winner != other.winner) return false
        if (loser != other.loser) return false
        if (lastSeatWon != other.lastSeatWon) return false
        if (firstSeatLost != other.firstSeatLost) return false
        if (dilutedMean != other.dilutedMean) return false
        if (lowerg != other.lowerg) return false
        if (upperg != other.upperg) return false
        if (c != other.c) return false
        if (info != other.info) return false

        return true
    }

    override fun hashCode(): Int {
        var result = winner
        result = 31 * result + loser
        result = 31 * result + lastSeatWon
        result = 31 * result + firstSeatLost
        result = 31 * result + dilutedMean.hashCode()
        result = 31 * result + lowerg.hashCode()
        result = 31 * result + upperg.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }

    override fun toString() = desc()
}