package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.min

private val logger = KotlinLogging.logger("Contest")

// For a Contest; but a contest may have mixed Assertions, eg DHondt.
enum class SocialChoiceFunction(val hasMinPct: Boolean) {
    PLURALITY(false), // “first past the post”
    APPROVAL(false), // choose as many candidates as they want, all the votes are added up, and the candidate with the most votes win.
    THRESHOLD(true),
    IRV(false), // “preferential voting” or “instant runoff voting”
    DHONDT(true),
    RUNOFF(true),  // one majority winner or two runoff winners
}

/** pre-election information **/
data class ContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>, // candidate name -> candidate id
    val choiceFunction: SocialChoiceFunction,
    val nwinners: Int = 1,              // max number of winners; for Dhondt == nseats; for RUNOFF, may be 1 or 2
    val voteForN: Int = nwinners,       // how many votes can a user cast in this contest?
    val minFraction: Double? = null,    // used in threshold, dhondt, runoff
) {
    val metadata = mutableMapOf<String, String>()
    val candidateIds: List<Int> // same order as candidateNames
    val isIrv = choiceFunction == SocialChoiceFunction.IRV
    // inverse of candidateNames
    val candidateIdToName: Map<Int, String> by lazy { candidateNames.entries.associate {(k,v) -> v to k } }
    // needed for Raire
    val candidateIdToIdx: Map<Int, Int>

    init {
        if (choiceFunction.hasMinPct) require(minFraction != null) { "$choiceFunction requires minFraction" }
        if (!choiceFunction.hasMinPct)  require(minFraction == null) { "$choiceFunction may not have minFraction"}
        if (minFraction != null) require(minFraction in (0.0..1.0)) { "minFraction must be between 0 and 1"}
        if (choiceFunction != SocialChoiceFunction.DHONDT) {
            require(nwinners in (1..candidateNames.size)) { "nwinners between 1 and candidateNames.size"}
        }
        require(voteForN in (1..candidateNames.size)) { "voteForN between 1 and candidateNames.size"}

        val candidateSet: Set<String> = candidateNames.toList().map { it.first }.toSet()
        require(candidateSet.size == candidateNames.size) { "duplicate candidate name $candidateNames"} // may not be possible
        candidateSet.forEach { candidate: String ->
            candidateSet.filter{ it != candidate }.forEach {
                require(candidate.isNotEmpty() ) { "blank candidate name in $candidateNames"}
                require(!candidate.equals(it, ignoreCase = true) ) { "candidate names differ only by case: $candidateNames"}
            }
        }

        candidateIds = candidateNames.toList().map { it.second }
        candidateIdToIdx = candidateIds.mapIndexed { idx, id -> Pair(id, idx) }.toMap()
        val candidateIdSet = candidateIds.toSet()
        require(candidateIdSet.size == candidateIds.size) { "duplicate candidate id $candidateIds"}
    }

    fun desc() = buildString {
        append("'$name' ($id) candidates=${candidateIds} choiceFunction=$choiceFunction nwinners=$nwinners voteForN=${voteForN}")
    }

    override fun toString() = desc()

    fun show() = buildString {  // used by viewer
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
    fun isIrv() = choiceFunction == SocialChoiceFunction.IRV
    fun show() : String = toString()
    fun showCandidates(): String

    fun recountMargin(assorter: AssorterIF): Double // (w-l)/w
    fun showAssertionDifficulty(assorter: AssorterIF): String
    fun marginInVotes(assorter: AssorterIF): Int

    fun votes() : Map<Int, Int>? {
        return if (this is Contest) this.votes else null
    }
    fun nvotes() : Int {
        return votes()?.values?.sum() ?: 0
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

    val votes: Map<Int, Int>  // candidateId -> nvotes; zero vote candidates have been added; sorted by nvotes
    val undervotes: Int

    // overridden by DHondt
    var winnerNames: List<String>
    var winners: List<Int>
    var losers: List<Int>

    init {
        require(info.choiceFunction != SocialChoiceFunction.IRV) { "contest $id: use DHondtContest for SocialChoiceFunction.IRV" }
        require(Ncast <= Nc) { "contest $id Ncast= $Ncast must be <= Nc= $Nc" }

        // verify that the candidateIds match whats in the ContestInfo
        voteInput.forEach {
            require(info().candidateIds.contains(it.key)) {
                "'${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"
            }
        }
        // construct votes, adding 0 votes as needed
        val voteBuilder = mutableMapOf<Int, Int>()
        voteBuilder.putAll(voteInput)
        info.candidateIds.forEach {
            if (!voteInput.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap() // reverse sort by number of votes recieved, do not change
        votes.forEach { (candId, candVotes) ->
            require(candVotes <= Ncast) { // LOOK
                "contest $id candidate= $candId votes = $candVotes must be <= (Nc - Nphantoms) = ${Nc - Nphantoms()}"
            }
        }
        val nvotes = votes.values.sum()
        require(nvotes <= info.voteForN * Ncast) {
            "contest $id nvotes= $nvotes must be <= voteForN=${info.voteForN} * Ncast=$Ncast = ${info.voteForN * Ncast}"
        }
        val sortedCandidateIds = votes.map { it.key } // candidate ids sorted by nvotes
        
        // maximum votes possible - actual votes
        undervotes = info.voteForN * Ncast - nvotes   // C1

        // check that the minimum value is satisfied
        // "A winning candidate must have a minimum fraction f ∈ (0, 1) of the valid votes to win". assume that means nvotes, not Nc.
        val useMin = info.minFraction ?: 0.0
        val candidatesOverTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin } // Pair(candidateId, nvotes)
        
        //// find winners
        if (choiceFunction == SocialChoiceFunction.RUNOFF) {
            // TODO do we have to modify info.nwinners ??
            // 2026-07-06 12:24:59.822 WARN  checkContestsCorrectlyFormed
            // *** Contest US Senate - Rep (1) has 2 winners should be 1
            if (candidatesOverTheMin.isEmpty()) {
                winners = sortedCandidateIds.take(2) // first two go to runoff
            } else {
                winners  = listOf(sortedCandidateIds.first()) // passes majority
            }
        } else {
            val useNwinners = min(candidatesOverTheMin.size, info.nwinners)
            winners = sortedCandidateIds.take(useNwinners) 
        }
        
        if (winners.isEmpty()) {
            logger.info {"*** there are no winners for $info" }
        }
        
        winnerNames = winners.map { info.candidateIdToName[it]!! }
        losers = sortedCandidateIds.drop(winners.size)
    }

    fun reportedMargin(winnerId: Int, loserId: Int): Double {
        val winnerVotes = votes[winnerId] ?: 0
        val loserVotes = votes[loserId] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    fun percentForCand(cand: Int): Double {
        val candVotes = votes[cand] ?: 0
        return candVotes / Nc.toDouble()
    }

    override fun recountMargin(assorter: AssorterIF): Double  {
        val winner = votes[assorter.winner()]!!
        val marginInVotes = marginInVotes(assorter)
        return marginInVotes / (winner.toDouble())
    }

    override fun showAssertionDifficulty(assorter: AssorterIF): String {
        val votes = votes()!!
        val winner = votes[assorter.winner()]
        val loser = votes[assorter.loser()] // may be null
        val marginInVotes = marginInVotes(assorter)

        return "${assorter.shortName()} votes=$winner/$loser diff=${marginInVotes}"
    }

    override fun marginInVotes(assorter: AssorterIF): Int {
        return when (assorter) {
            is DHondtAssorter -> {
                roundToClosest(assorter.voteDiff(votes[assorter.winner()]!!, votes[assorter.loser()]!!))
            }

            is BelowThreshold -> {
                roundToClosest(assorter.t * nvotes() - votes[assorter.winner()]!!)
            }

            is AboveThreshold -> {
                roundToClosest(votes[assorter.winner()]!! - assorter.t * nvotes())
            }

            else -> {
                val winner = votes[assorter.winner()]!!
                val loser = votes[assorter.loser()]!!
                winner - loser
            }
        }
    }

    override fun show() = buildString {
        append("'$name' ($id) $choiceFunction voteForN=${info.voteForN} votes=${votes()} undervotes=$undervotes,")
        append(" winners=${winners()} Nc=${Nc()} Nphantoms=${Nphantoms()} Nu=${Nundervotes()} sumVotes=${votes.values.sum()}")
    }

    override fun showCandidates() = buildString {
        info().candidateNames.forEach { (name, id) ->
            val win = if (winners().contains(id)) " (winner)" else ""
            val pct = 100 * (votes[id] ?: 0) / nvotes().toDouble()
            appendLine("   $id '$name': votes=${votes[id]} (${dfn(pct, 2)}%) $win")
        }
        append("    Total=${votes.values.sum()}")
    }

    override fun toString() = buildString {
        append("$name ($id) Nc=$Nc Nphantoms=${Nphantoms()} votes=${votes()} undervotes=$undervotes, voteForN=${info.voteForN}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contest) return false

        if (Nc != other.Nc) return false
        if (Ncast != other.Ncast) return false
        if (undervotes != other.undervotes) return false
        if (info != other.info) return false
        if (votes != other.votes) return false
        if (winnerNames != other.winnerNames) return false
        if (winners != other.winners) return false
        if (losers != other.losers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Nc
        result = 31 * result + Ncast
        result = 31 * result + undervotes
        result = 31 * result + info.hashCode()
        result = 31 * result + votes.hashCode()
        result = 31 * result + winnerNames.hashCode()
        result = 31 * result + winners.hashCode()
        result = 31 * result + losers.hashCode()
        return result
    }

    companion object {
        fun makeWithCandidateNames(info: ContestInfo, votesByName: Map<String, Int>, Nc: Int, Ncast: Int): Contest {
            val votesById = votesByName.map { (key, value) -> Pair(info.candidateNames[key]!!, value) }.toMap()
            return Contest(info, votesById, Nc, Ncast)
        }
    }
}

