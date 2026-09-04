package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.Cvr

//// each contest gets separate card
// you have to make sure that the contest population is increased by the phantoms that contain it.

// cvrs for single contest
fun makePhantomCvrs(
    contestId: Int,
    nphantoms: Int,
    prefix: String = "phantom-",
): List<Cvr> {
    val results = mutableListOf<Cvr>()
    repeat(nphantoms) {
        val votes = mapOf( contestId to intArrayOf() )
        results.add(Cvr("$prefix$it", votes, phantom = true))
    }
    return results
}

//// cvrs for multiple contests
fun makePhantomCvrs(
    contests: List<ContestIF>,
    prefix: String = "phantom-",
): List<Cvr> {

    val phantoms = mutableListOf<Cvr>()
    for (contest in contests) {
        phantoms.addAll(makePhantomCvrs(contest.id, contest.Nphantoms(), prefix))
    }
    return phantoms
}

fun makePhantomCards(
    contests: List<ContestIF>,
    startIdx: Int,
    prefix: String = "phantom-",
): List<AuditableCard> {
    var idx = startIdx

    val phantoms = mutableListOf<AuditableCard>()
    contests.forEach { contest ->
        val votes = mapOf( contest.id to intArrayOf() )
        repeat(contest.Nphantoms()) {
            val card = AuditableCard.fromVotes(id = "${prefix}${idx}", location = null, index = idx, prn = 0L, phantom = true,
                    styleId=CardStyle.phantomStyle.id, poolId = null, votes=votes)
                .setStyle(CardStyle.phantomStyle)

            phantoms.add(card)
            idx++
        }
    }
    return phantoms
}

/*
fun makeCountPhantomCvrs(
    phantomCount: Map<Int, Int>, // contestId -> Nphantoms
    prefix: String = "phantom-",
): List<Cvr> {
    val phantombs = mutableListOf<PhantomBuilder>()
    phantomCount.forEach { (contestId, phantoms_needed) ->
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}", 0))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contestId)
        }
    }
    return phantombs.map { it.buildCvr() }
}

class PhantomBuilder(val id: String, val idx: Int) {
    val contests = mutableListOf<Int>()

    fun buildCvr(): Cvr {
        val votes = contests.associateWith { IntArray(0) }
        return Cvr(id, votes, phantom = true)
    }

    fun buildCardM(): AuditableCard {
        val votes = contests.associateWith { IntArray(0) }
       //  val (contestIds, contestStarts, candidates) = makeFromVotes(votes)
        return AuditableCard.fromVotes(id = id, location = null, index = idx, prn = 0L, phantom = true, styleId=CardStyle.phantomStyle.id, poolId = null,
            votes=votes).setStyle(CardStyle.phantomStyle)
    }

} */