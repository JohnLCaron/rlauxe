package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*

class CardBuilders(startCvrId: Int = 0) {
    private val builders = mutableListOf<CardBuilder>()
    var nextCvrId = startCvrId
    val contests = mutableMapOf<String, CvrContest>()
    var contestId = 0

    fun addContests(infos: List<ContestInfo>): CardBuilders {
        infos.forEach {
            val c = CvrContest(it.name, it.id)
            c.candidates.putAll(it.candidateNames)
            contests[it.name] = c
        }
        return this
    }

    fun addCard(): CardBuilder {
        this.nextCvrId++
        val cb = CardBuilder("card${nextCvrId}", nextCvrId)
        builders.add(cb)
        return cb
    }

    fun build(): List<AuditableCard> {
        return builders.map { it.build() }
    }

}

class CardBuilder(
    val location: String,
    val idx: Int,
    val phantom: Boolean = false,
    val possibleContests: IntArray = intArrayOf(),
    val poolId: Int? = null,
    val cardStyle: String? = null,
) {
    val allVotes = mutableMapOf<Int, IntArray>()

    fun addContest(contestId: Int, votes: IntArray): CardBuilder  {
        allVotes[contestId] = votes
        return this
    }

    fun addContest(id: Int, candidateId: Int?) {
        if (allVotes[id] == null) allVotes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build() : AuditableCard {
        // data class AuditableCard (
        //    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
        //    val index: Int,  // index into the original, canonical list of cards
        //    val prn: Long,   // psuedo random number
        //    val phantom: Boolean,
        //    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle
        //    val votes: Map<Int, IntArray>?, // for CLCA, a map of contest -> the candidate ids voted; must include undervotes (??)
        //                                    // for IRV, ranked first to last; missing for pooled data or polling audits
        //    val poolId: Int?, // for OneAudit
        //    val cardStyle: String? = null,
        return AuditableCard(location, index=idx, prn=0L, phantom=phantom, possibleContests=possibleContests,
            votes= if (allVotes.isEmpty()) null else allVotes,
            poolId=poolId, cardStyle=cardStyle)
    }
}