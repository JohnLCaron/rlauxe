package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.min

// For a Contest; Assertions may be mixed.
enum class SocialChoiceFunction(val hasMinPct: Boolean) {
    PLURALITY(false),
    APPROVAL(false),
    THRESHOLD(true),
    IRV(false),
    DHONDT(true)
}

/** pre-election information **/
data class ContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: SocialChoiceFunction,  // electionguard has "VoteVariationType"
    val nwinners: Int = 1,          // aka "numberElected"
    val voteForN: Int = nwinners,   // aka "contestSelectionLimit" or "optionSelectionLimit"
    val minFraction: Double? = null, // threshold, dhondt only.
) {
    val candidateIds: List<Int> // same order as candidateNames
    val metadata = mutableMapOf<String, Int>()
    val isIrv = choiceFunction == SocialChoiceFunction.IRV

    val candidateIdToName: Map<Int, String> by lazy { candidateNames.entries.associate {(k,v) -> v to k } }

    init {
        if (choiceFunction.hasMinPct) require(minFraction != null) { "$choiceFunction requires minFraction"}
        if (!choiceFunction.hasMinPct)  require(minFraction == null) { "$choiceFunction may not have minFraction"}
        if (minFraction != null) require(minFraction in (0.0..1.0)) { "minFraction must be between 0 and 1"}
        if (choiceFunction != SocialChoiceFunction.DHONDT) require(nwinners in (1..candidateNames.size)) { "nwinners between 1 and candidateNames.size"}
        require(voteForN in (1..candidateNames.size)) { "voteForN between 1 and candidateNames.size"}

        val candidateSet = candidateNames.toList().map { it.first }.toSet()
        require(candidateSet.size == candidateNames.size) { "duplicate candidate name $candidateNames"} // may not be possible
        candidateSet.forEach { candidate ->
            candidateSet.filter{ it != candidate }.forEach {
                require(candidate.isNotEmpty() ) { "empty candidate name: $candidateNames"}
                require(!candidate.equals(it, ignoreCase = true) ) { "candidate names differ only by case: $candidateNames"}
            }
        }

        candidateIds = candidateNames.toList().map { it.second }
        val candidateIdSet = candidateIds.toSet()
        require(candidateIdSet.size == candidateIds.size) { "duplicate candidate id $candidateIds"}
    }

    constructor(id: Int): this("", id, mapOf("name" to 1), SocialChoiceFunction.PLURALITY)

    fun desc() = buildString {
        append("'$name' ($id) candidates=${candidateIds} choiceFunction=$choiceFunction nwinners=$nwinners voteForN=${voteForN}")
    }

    override fun toString() = desc()

    fun show() = buildString {
        appendLine("$name ($id) choiceFunction=${choiceFunction} nwinners=$nwinners")
        candidateNames.forEach { (name, id) -> appendLine("  $name -> $id") }
    }
}

// Needed to allow RaireContest, which does not have votes: Map<Int, Int>
interface ContestIF {
    val id get() = info().id
    val name get() = info().name
    val ncandidates get() = info().candidateNames.size
    val choiceFunction get() = info().choiceFunction

    fun Nc(): Int  // independent contest bound
    fun Nphantoms(): Int  // number of phantoms
    fun Ncast() = Nc() - Nphantoms()
    fun Nundervotes(): Int  // number of undervotes
    fun info(): ContestInfo
    fun winnerNames(): List<String>
    fun winners(): List<Int>
    fun losers(): List<Int>

    fun undervotePct() = roundToClosest(100.0 * Nundervotes() / (info().voteForN * Nc())) // for viewer
    fun phantomRate() = Nphantoms() / Nc().toDouble()
    fun isIrv() = choiceFunction == SocialChoiceFunction.IRV
    fun show() : String = toString()
    fun showCandidates(): String

    fun recountMargin(assorter: AssorterIF): Double // (w-l)/w
    fun showAssertionDifficulty(assorter: AssorterIF): String

    fun votes() : Map<Int, Int>? {
        return if (this is Contest) this.votes else null
    }
}

/**
 * Contest with the reported votes.
 * Immutable.
 * @parameter voteInput: candidateId -> reported number of votes. keys must be in info.candidateIds, though zeros may be omitted.
 * @parameter Nc: maximum ballots/cards that contain this contest, independently verified (not from cvrs).
 * @parameter Ncast: number of cards cast for this contest: Nphantoms() = Nc - Ncast
 */
open class Contest(
        val info: ContestInfo,
        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
        val Nc: Int,               // trusted maximum ballots/cards that contain this contest
        val Ncast: Int,            // number of cast ballots containing this Contest, including undervotes
    ): ContestIF {

    override fun Nc() = Nc
    override fun Nphantoms() = Nc - Ncast
    override fun Nundervotes() = undervotes
    override fun info() = info
    override fun winnerNames() = winnerNames
    override fun winners() = winners
    override fun losers() = losers

    val votes: Map<Int, Int>  // candidateId -> nvotes; zero vote candidates have been added
    val undervotes: Int

    // overridden by DHondt
    var winnerNames: List<String>
    var winners: List<Int>
    var losers: List<Int>

    init {
        require(Ncast <= Nc) { "contest $id Ncast= $Ncast must be <= Nc= $Nc" }

        // construct votes, adding 0 votes if needed
        voteInput.forEach {
            require(info().candidateIds.contains(it.key)) {
                "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"
            }
        }
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.putAll(voteInput)
        info.candidateIds.forEach {
            if (!voteInput.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap() // sort by votes recieved
        votes.forEach { (candId, candVotes) ->
            require(candVotes <= Ncast) { // LOOK
                "contest $id candidate= $candId votes = $candVotes must be <= (Nc - Nphantoms) = ${Nc - Nphantoms()}"
            }
        }
        val nvotes = votes.values.sum()
       if (info.choiceFunction != SocialChoiceFunction.IRV) {
            require(nvotes <= info.voteForN * Ncast) {
                "contest $id nvotes= $nvotes must be <= nwinners=${info.voteForN} * (Nc=$Nc - Nphantoms=${Nphantoms()}) = ${info.voteForN * (Nc - Nphantoms())}"
            }
        }
        undervotes = info.voteForN * Ncast - nvotes   // C1

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, THRESHOLD.  IRV handled by RaireContest, DHONDT by DHondtContest

        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        val useMin = info.minFraction ?: 0.0
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) } // invert the map
        winnerNames = winners.map { mapIdToName[it]!! }
        if (winners.isEmpty()) {
            val pct = votes.toList().associate { it.first to it.second.toDouble() / nvotes }.toMap()
            println("*** there are no winners for minFraction=$useMin vote% = $pct")
        }

        // find losers
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
    }

    // TODO candidate for removal
    fun margin(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    fun percentForCand(cand: Int): Double {
        val candVotes = votes[cand] ?: 0
        return candVotes / Nc.toDouble()
    }

    // put the undervotes into the candidate map.
    fun votesAndUndervotes(): VotesAndUndervotes {
        return VotesAndUndervotes(votes, undervotes, info.voteForN)
    }

    override fun recountMargin(assorter: AssorterIF): Double  {
        val winner = votes[assorter.winner()]!!
        val loser = votes[assorter.loser()]!!
        return (winner - loser) / (winner.toDouble())
    }

    override fun showAssertionDifficulty(assorter: AssorterIF): String {
        val votes = votes()!!
        val winner = votes[assorter.winner()]!!
        val loser = votes[assorter.loser()]!!
        return "${assorter.shortName()} votes=$winner/$loser diff=${winner-loser} (w-l)/w =${df(recountMargin(assorter))}"
    }

    override fun show() = buildString {
        appendLine("'$name' ($id) $choiceFunction voteForN=${info.voteForN} ${votesAndUndervotes()}")
        append("   winners=${winners()} Nc=${Nc()} Nphantoms=${Nphantoms()} Nu=${Nundervotes()} sumVotes=${votes.values.sum()}")
    }

    override fun showCandidates() = buildString {
        if (votes() != null) {
            val votes = votes()!!
            info().candidateNames.forEach { (name, id) ->
                val win = if (winners().contains(id)) " (winner)" else ""
                appendLine("   $id '$name': votes=${votes[id]} $win")
            }
            append("    Total=${votes.values.sum()}")
        } else {
            info().candidateNames.forEach { (name, id) ->
                val win = if (winners().contains(id)) " (winner)" else ""
                appendLine("   $id '$name' $win")
            }
        }
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Nphantoms=${Nphantoms()} ${votesAndUndervotes()}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contest

        if (Nc != other.Nc) return false
        if (Ncast != other.Ncast) return false
        if (info != other.info) return false
        if (winnerNames != other.winnerNames) return false
        if (winners != other.winners) return false
        if (losers != other.losers) return false
        if (votes != other.votes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Nc
        result = 31 * result + Ncast
        result = 31 * result + info.hashCode()
        result = 31 * result + winnerNames.hashCode()
        result = 31 * result + winners.hashCode()
        result = 31 * result + losers.hashCode()
        result = 31 * result + votes.hashCode()
        return result
    }

    companion object {
        fun makeWithCandidateNames(info: ContestInfo, votesByName: Map<String, Int>, Nc: Int, Ncast: Int): Contest {
            val votesById = votesByName.map { (key, value) -> Pair(info.candidateNames[key]!!, value) }.toMap()
            return Contest(info, votesById, Nc, Ncast)
        }
    }
}

/** Could rename to "Contest with assertions". note mutability. */
open class ContestUnderAudit(
    val contest: ContestIF,
    val isClca: Boolean = true,
    val hasStyle: Boolean = true,
    NpopIn: Int? = null,
) {
    val id = contest.id
    val name = contest.name
    val choiceFunction = contest.choiceFunction
    val ncandidates = contest.ncandidates
    val Nc = contest.Nc()
    val Nphantoms = contest.Nphantoms()
    val Npop: Int = NpopIn ?: Nc // "sample population size" for this contest, used to make diluted margins
    val isIrv = contest.info().isIrv

    var preAuditStatus = TestH0Status.InProgress // pre-auditing status: NoLosers, NoWinners, ContestMisformed, MinMargin, TooManyPhantoms
    var pollingAssertions: List<Assertion> = emptyList() // mutable needed for Raire override and serialization
    var clcaAssertions: List<ClcaAssertion> = emptyList() // mutable needed for serialization

    init {
        if (contest.losers().size == 0) {
            preAuditStatus = TestH0Status.NoLosers
        } else if (contest.winners().size == 0) {
            preAuditStatus = TestH0Status.NoWinners
        }
    }

    // dhondt
    fun addAssertionsFromAssorters(assorters: List<AssorterIF>): ContestUnderAudit {
        val assertions = mutableListOf<Assertion>()
        assorters.forEach { assorter ->
            assertions.add(Assertion(contest.info(), assorter))
        }
        pollingAssertions = assertions

        if (isClca) {
            addClcaAssertionsFromDilutedMargin()
        }

        return this
    }

    fun addStandardAssertions(): ContestUnderAudit {
        if (contest.votes() == null) {
            throw RuntimeException("contest type ${contest.javaClass.simpleName} is not supported for addStandardAssertions")
        }

        this.pollingAssertions = when (choiceFunction) {
            SocialChoiceFunction.APPROVAL,
            SocialChoiceFunction.PLURALITY -> makePluralityAssertions()
            SocialChoiceFunction.THRESHOLD -> makeThresholdAssertions()
            else -> throw RuntimeException("choice function ${choiceFunction} is not supported")
        }

        if (isClca) {
            addClcaAssertionsFromDilutedMargin()
        }

        return this
    }

    private fun makePluralityAssertions(): List<Assertion> {
        // test that every winner beats every loser. SHANGRLA 2.1
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { winner ->
            contest.losers().forEach { loser ->
                val assorter = PluralityAssorter.makeWithVotes(contest, winner, loser, Npop)
                assertions.add(Assertion(contest.info(), assorter))
            }
        }
        return assertions
    }

    private fun makeThresholdAssertions(): List<Assertion> {
        require(contest.info().minFraction != null)
        // each winner generates 1 assertion. SHANGRLA 2.3
        val assertions = mutableListOf<Assertion>()
        contest.winners().forEach { candId ->
            val assorter = AboveThreshold.makeFromVotes(contest as Contest, candId, Npop)
            assertions.add(Assertion(contest.info(), assorter))
        }
        return assertions
    }

    // TODO check that assorters already have the diluted margin
    fun makeDilutedMargin(assorter: AssorterIF): Double {
        val margin = assorter.calcMargin(contest.votes(), Npop)
        return margin
    }

    private fun addClcaAssertionsFromDilutedMargin(): ContestUnderAudit {
        require(isClca) { "makeComparisonAssertions() can be called only on comparison contest"}

        this.clcaAssertions = pollingAssertions.map { assertion ->
            ClcaAssertion(contest.info(), makeClcaAssorter(assertion))
        }
        return this
    }

    open fun makeClcaAssorter(assertion: Assertion): ClcaAssorter {
        return ClcaAssorter(contest.info(), assertion.assorter, hasStyle=hasStyle, dilutedMargin=makeDilutedMargin(assertion.assorter))
    }

    fun assertions(): List<Assertion> {
        return if (isClca) clcaAssertions else pollingAssertions
    }

    // Pair(ClcaAssertion, noerror)
    fun minClcaAssertion(): ClcaAssertion? {
        if (clcaAssertions.isEmpty()) return null
        val margins = clcaAssertions.map { Pair(it, it.cassorter.noerror())  }
        val minMargin = margins.sortedBy { it.second }
        return minMargin.first().first
    }

    // Pair(ClcaAssertion, dilutedMargin)
    fun minPollingAssertion(): Assertion? {
        if (pollingAssertions.isEmpty()) return null
        val margins = pollingAssertions.map { Pair(it, makeDilutedMargin(it.assorter))  }
        val minMargin = margins.sortedBy { it.second }
        return minMargin.first().first
    }

    fun minAssertion(): Assertion? {
        return if (isClca) minClcaAssertion() else minPollingAssertion()
    }

    fun minDilutedMargin(): Double? {
        val minAssertion = minAssertion()
        return if (minAssertion != null) makeDilutedMargin(minAssertion.assorter) else null
    }

    fun minNoError(): Double? {
        val minAssertion = minAssertion()
        return if (minAssertion != null) minAssertion.assorter.noerror() else null
    }

    fun minRecountMargin(): Double? {
        val minAssertion = minAssertion()
        return if (minAssertion != null)  contest.recountMargin(minAssertion.assorter) else null
    }

    fun minAssertionDifficulty(): String {
        val minAssertion = minAssertion()
        return if (minAssertion != null)  contest.showAssertionDifficulty(minAssertion.assorter) else "N/A"
    }

    override fun toString() = showShort()

    open fun show() = buildString {
        appendLine("${contest.javaClass.simpleName} ${contest.show()}")
        val minAssertion = minAssertion()
        if (minAssertion != null) {
            val minAssorter = minAssertion.assorter
            append("   ${contest.showAssertionDifficulty(minAssertion.assorter)}")
            append(" Npop=$Npop dilutedMargin=${pfn(makeDilutedMargin(minAssorter))}")
            appendLine(" reportedMargin=${pfn(minAssorter.dilutedMargin())} recountMargin=${pfn(contest.recountMargin(minAssorter))} ")
        }
        append(contest.showCandidates())
    }

    open fun showShort() = buildString {
        val votes = contest.votes() ?: "N/A"
        append("$name ($id) votes=${votes} Nc=$Nc Npop=$Npop minDilutedMargin=${df(minDilutedMargin())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContestUnderAudit

        if (isClca != other.isClca) return false
        if (hasStyle != other.hasStyle) return false
        if (!contest.equals(other.contest)) return false
        if (preAuditStatus != other.preAuditStatus) return false
        if (pollingAssertions != other.pollingAssertions) return false
        if (clcaAssertions != other.clcaAssertions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isClca.hashCode()
        result = 31 * result + hasStyle.hashCode()
        result = 31 * result + contest.hashCode()
        result = 31 * result + preAuditStatus.hashCode()
        result = 31 * result + pollingAssertions.hashCode()
        result = 31 * result + clcaAssertions.hashCode()
        return result
    }

    companion object {
        fun make(contests: List<ContestIF>, cards: CloseableIterator<AuditableCard>, isClca: Boolean, hasStyle: Boolean): List<ContestUnderAudit> {
            val infos = contests.map { it.info() }.associateBy { it.id }
            val manifestTabs = tabulateAuditableCards(cards, infos)
            val Nbs = manifestTabs.mapValues { it.value.ncards }
            return contests.map {
                val cua = ContestUnderAudit(it, isClca, hasStyle, NpopIn=Nbs[it.id]).addStandardAssertions()
                if (it is DHondtContest) {
                    cua.addAssertionsFromAssorters(it.assorters)
                } else {
                    cua.addStandardAssertions()
                }
            }
        }
    }

}

