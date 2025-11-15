package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard

/*
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

    /*
    companion object {
        fun convertCardsToBuilders(contests:List<ContestInfo>, cvrs: List<AuditableCard>): List<CardBuilders> {
            val cvrsbs = CardBuilder()
            cvrsbs.addContests( contests)
            cvrs.forEach { CvrBuilder.fromCvr(cvrsbs, it) }
            return cvrsbs.builders
        }
    } */

} */

// data class AuditableCard (
//    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle?
//    val votes: Map<Int, IntArray>?, // for CLCA or OneAudit, a map of contest -> the candidate ids voted; must include undervotes; missing for pooled data or polling audits
//                                                                                // when IRV, ranked first to last
//    val poolId: Int?, // for OneAudit, or for setting style
//    val cardStyle: String? = null, // not used yet
//)

// builds one card
class CardBuilder(
    val location: String,
    val index: Int,
    val prn: Long,
    val phantom: Boolean,
    val possibleContests: IntArray,
    votesIn: Map<Int, IntArray>?,
    val poolId: Int?,
    val cardStyle: String? = null,
) {
    val votes = mutableMapOf<Int, IntArray>()

    init {
        if (votesIn != null) votes.putAll(votesIn)
    }

    constructor(location: String, index: Int):
            this(location, index, 0L, false, intArrayOf(), null, null, null)

    constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
            this(location, index, 0L, false, intArrayOf(), null, poolId, cardStyle)

    fun replaceContestVotes(contestId: Int, contestVotes: IntArray): CardBuilder  {
        votes[contestId] = contestVotes
        return this
    }

    fun replaceContestVote(id: Int, candidateId: Int?) {
        votes[id] = if (candidateId == null) intArrayOf() else intArrayOf(candidateId)
    }

    fun build(poolId:Int? = null) : AuditableCard {
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
        return AuditableCard(location, index, prn, phantom, possibleContests,
            votes= if (votes.isEmpty()) null else votes,
            poolId=poolId ?: this.poolId,
            cardStyle=cardStyle)
    }

    companion object {
        fun fromCard(card: AuditableCard) = CardBuilder(
            card.location,
            card.index,
            card.prn,
            card.phantom,
            card.contests(),
            card.votes,
            card.poolId,
            card.cardStyle
        )

    }
}