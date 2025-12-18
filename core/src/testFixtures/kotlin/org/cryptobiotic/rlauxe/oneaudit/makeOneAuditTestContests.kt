package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssertionType
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.showTabs
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards

private const val debug = false

fun makeOneAuditTestContests(
    infos: Map<Int, ContestInfo>, // all the contests in the pools
    contestsToAudit: List<Contest>, // the contests you want to audit
    cardStyles: List<CardStyleIF>,
    cardManifest: List<AuditableCard>,
    mvrs: List<Cvr>, // this must be just for tests
): Pair<List<ContestUnderAudit>, List<CardPoolIF>> {

    // The Nbs come from the cards
    val manifestTabs = tabulateAuditableCards(Closer(cardManifest.iterator()), infos)
    val Nbs = manifestTabs.mapValues { it.value.ncards }

    val contestsUA = contestsToAudit.map {
        val cua = ContestUnderAudit(it, true, NpopIn=Nbs[it.id])
        if (it is DHondtContest) {
            cua.addAssertionsFromAssorters(it.assorters)
        } else {
            cua.addStandardAssertions()
        }
    }
    if (debug) println(showTabs("manifestTabs", manifestTabs))

    // create from cardStyles and populate the pool counts from the mvrs
    val poolsFromCvrs = calcCardPoolsFromMvrs(infos, cardStyles, mvrs)

    /* The styles have the name, id, and contest list
    val poolsFromCvrsOld = cardStyles.map { style ->
        val poolFromCvr = CardPoolFromCvrs(style.name(), style.poolId(), infos)
        style.contests().forEach { poolFromCvr.contestTabs[it]  = ContestTabulation( infos[it]!!) }
        poolFromCvr
    }.associateBy { it.poolId }

    // populate the pool counts from the mvrs
    mvrs.filter{ it.poolId != null }.forEach {
        val pool = poolsFromCvrs[it.poolId]
        if (pool != null) pool.accumulateVotes(it)
    }
    if (debug) {
        println("tabulatePooledMvrs")
        poolsFromCvrs.forEach { (id, pool) ->
            println(pool)
            pool.contestTabs.forEach {
                println(" $it")
            }
            println()
        }
    } */

    // The OA assort averages come from the mvrs
    addOAClcaAssortersFromMargin(contestsUA, poolsFromCvrs)

    // poolsFromCvrs record the complete pool contests,
    return Pair(contestsUA, poolsFromCvrs)
}

// TODO OAIrv
// TODO try in SF?
fun makeTestContestOAIrv(): RaireContestUnderAudit {

    val info = ContestInfo(
        "TestOneAuditIrvContest",
        0,
        mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
        SocialChoiceFunction.IRV,
        voteForN = 6,
    )
    val Nc = 2120
    val Np = 2
    val rcontest = RaireContest(info, winners = listOf(1), Nc = Nc, Ncast = Nc - Np, undervotes = 0)

    // where did these come from ??
    val assert1 = RaireAssertion(1, 0, 0.0, 42, RaireAssertionType.winner_only)
    val assert2 = RaireAssertion(
        1, 2, 0.0, 422, RaireAssertionType.irv_elimination,
        listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3)
    )

    val oaIrv = RaireContestUnderAudit(rcontest, rassertions = listOf(assert1, assert2))

    // add pools

    // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
    //val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
    //val cvrNc = 200
    // val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
    val pools = emptyList<CardPoolIF>()

    // TODO
    val cardPool = CardPoolFromCvrs(
        "noCvr",
        42, // poolId
        infos = mapOf(info.id to info),
    )

    val votesNoCvr = mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0)

    //voteTotals = mapOf(info.id to votesNoCvr)
    val cardPools = listOf(cardPool)

    val clcaAssertions = oaIrv.pollingAssertions.map { assertion ->
        val passort = assertion.assorter
        val pairs = pools.map { pool ->
            Pair(pool.poolId, 0.55)
        }
        val poolAvgs = AssortAvgsInPools(pairs.toMap())
        val clcaAssertion = ClcaAssorterOneAudit(assertion.info, passort, oaIrv.makeDilutedMargin(passort), poolAvgs)
        ClcaAssertion(assertion.info, clcaAssertion)
    }
    oaIrv.clcaAssertions = clcaAssertions

    return oaIrv
}