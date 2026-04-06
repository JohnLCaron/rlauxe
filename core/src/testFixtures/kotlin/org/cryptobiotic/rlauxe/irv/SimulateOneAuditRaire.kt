package org.cryptobiotic.rlauxe.irv

import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.oneaudit.calcOneAuditPoolsFromMvrs
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.random.Random

// Try using in San Francisco, since we could generate the VoteConsolidators from the cvrs in the pool
fun simulateOneAuditRaire(N: Int, contestId: Int, ncands:Int, minMargin: Double, poolPct: Int,
                             undervotePct: Double = .05, phantomPct: Double = .005, quiet: Boolean = true)
        : Triple<RaireContestWithAssertions, List<Cvr>, List<CardPool>> {

    val (raireCUA, cvrs) = simulateRaireTestContest(N, contestId, ncands, minMargin, undervotePct, phantomPct, quiet)

    // change poolPct of cvrs
    val cvrsWithPools = cvrs.map { cvr ->
        val random = Random.nextInt(100)
        if (random < poolPct) cvr.copy( poolId = 42) else cvr
    }


    // turn it into OneAudit
    val info =raireCUA.contest.info()
    val infos = mapOf(contestId to info)
    val cvrTab = tabulateCvrs(cvrsWithPools.iterator(), infos).values.first()

    // data class Population(
    //    val name: String,
    //    val id: Int,
    //    val possibleContests: IntArray, // the list of possible contests.
    //    val hasSingleCardStyle: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
    //)
    val pop = CardStyle("simulateOneAuditRaire", 42, intArrayOf(contestId), true)
    val pools = calcOneAuditPoolsFromMvrs(
        infos,
        listOf(pop),
        cvrsWithPools,
    ).map { it.toOneAuditPool() }

    val raireOAUA = makeRaireOneAuditContest(info, cvrTab, N, Nbin=N, pools)
    if (!quiet) println(raireOAUA)

    return Triple(raireOAUA, cvrsWithPools, pools)
}