package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.Cvr

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

    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contests) {
        val phantoms_needed = contest.Nphantoms()
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}", 0))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.buildCvr() }
}

fun makePhantomCvrs(
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

fun makePhantomCards(
    contests: List<ContestIF>,
    startIdx: Int,
    prefix: String = "phantom-",
): List<AuditableCard> {
    var idx = startIdx

    val phantombs = mutableListOf<PhantomBuilder>()
    for (contest in contests) {
        val phantoms_needed = contest.Nphantoms()
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}", idx++))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.buildCard() }
}

class PhantomBuilder(val id: String, val idx: Int) {
    val contests = mutableListOf<Int>()

    fun buildCvr(): Cvr {
        val votes = contests.associateWith { IntArray(0) }
        return Cvr(id, votes, phantom = true)
    }

    fun buildCard(): AuditableCard {
        // hijack votes
        val votes = contests.associateWith { IntArray(0) }
        return AuditableCard(location = id, index = idx, prn = 0L, phantom = true, votes = votes, poolId = null)
    }
}