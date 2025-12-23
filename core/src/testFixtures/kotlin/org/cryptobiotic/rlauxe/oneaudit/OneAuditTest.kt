package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CvrsWithPopulationsToCardManifest
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.test.assertEquals

data class ContestMvrCardAndPops(
    val contestUA: ContestUnderAudit,
    val mvrs: List<Cvr>,
    val cards: List<AuditableCard>,
    val pools: List<OneAuditPoolIF>,
)

// simulate OneAudit Contest with extra cards in pool, to get Npop > Nc, and test hasStyle
fun makeOneAuditTest(
    margin: Double,
    Nc: Int,
    cvrFraction: Double,
    undervoteFraction: Double,
    phantomFraction: Double,
    extraInPool: Int = 0,
): ContestMvrCardAndPops {
    val nvotes = roundToClosest(Nc * (1.0 - undervoteFraction - phantomFraction))
    val winner = roundToClosest((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    return makeOneAuditTest(winner, loser, cvrFraction, undervoteFraction, phantomFraction, extraInPool)
}

// two candidate contest, with specified total votes
// divide into two stratum based on cvrPercent
fun makeOneAuditTest(
    winnerVotes: Int,
    loserVotes: Int,
    cvrFraction: Double,
    undervoteFraction: Double,
    phantomFraction: Double,
    extraInPool: Int = 0,
): ContestMvrCardAndPops {

    require(cvrFraction > 0.0)

    val info1 = ContestInfo(
        "OneAuditTest", 1,
        mapOf(
            "winner" to 0,
            "loser" to 1,
        ),
        SocialChoiceFunction.PLURALITY,
        nwinners = 1,
    )
    val info2 = ContestInfo(
        "OneContestExtra", 2,
        mapOf(
            "winner" to 0,
            "loser" to 1,
        ),
        SocialChoiceFunction.PLURALITY,
        nwinners = 1,
    )
    val infos = mapOf(1 to info1, 2 to info2)

    val nvotes = winnerVotes + loserVotes
    val Nc = roundToClosest(nvotes / (1.0 - undervoteFraction - phantomFraction))
    val Np = roundToClosest(Nc * phantomFraction)
    val Ncast = Nc - Np

    val cvrSize = roundToClosest(Ncast * cvrFraction)
    val noCvrSize = Ncast - cvrSize
    require(cvrSize + noCvrSize == Ncast)

    // reported results for the two strata
    val nvotesCvr = nvotes * cvrFraction
    val winnerCvr = roundToClosest(winnerVotes * cvrFraction)
    val loserCvr = roundToClosest(nvotesCvr - winnerCvr)

    val winnerPool = winnerVotes - winnerCvr
    val loserPool = loserVotes - loserCvr

    val cvrVotes = mapOf(0 to winnerCvr, 1 to loserCvr)
    val votesNoCvr = mapOf(0 to winnerPool, 1 to loserPool)
    val votesCvrSum = cvrVotes.values.sum()
    val votesPoolSum = votesNoCvr.values.sum()

    val undervotes = undervoteFraction * Nc
    val cvrUndervotes = roundToClosest(undervotes * cvrFraction)
    val poolUnderVotes = roundToClosest(undervotes - cvrUndervotes)

    val poolNcards = votesPoolSum + poolUnderVotes
    val pool = OneAuditPoolWithBallotStyle(
            "pool42",
            42, // poolId
            voteTotals = mapOf(1 to ContestTabulation(info1, votesNoCvr, ncards=noCvrSize)),
            hasSingleCardStyle = false,
            infos = infos,
        )
    pool.adjustCards = poolUnderVotes
    val pools = listOf(pool)

    val expectNc = noCvrSize + cvrSize + Np
    require (expectNc == Nc)

    val cvrNc = votesCvrSum + cvrUndervotes
    require (cvrNc >= votesCvrSum)

    val expectNc3 = pools.sumOf { it.ncards() } + cvrNc + Np
    require (expectNc3 == Nc)

    val contest = Contest(info1, mapOf(0 to winnerVotes, 1 to loserVotes), Nc = Nc, Ncast = Nc - Np)
    info1.metadata["PoolPct"] = (100.0 * poolNcards / Nc).toInt()

    val mvrs = makeMvrs(contest, cvrNc, cvrVotes, cvrUndervotes, pool, extraInPool)
    val cardManifest=  makeCardManifest(mvrs, pool)

    val (oaUA, cardPools) = makeOneAuditTestContests(infos, listOf(contest), listOf(pool), cardManifest, mvrs)

    return ContestMvrCardAndPops(oaUA.first(), mvrs, cardManifest, cardPools)
}

// these are the mvr truth
fun makeMvrs(
    contest: Contest,
    cvrNcards: Int,
    cvrVotes:Map<Int, Int>,
    cvrUndervotes: Int,
    pool: OneAuditPoolIF,
    extraInPool: Int,
    ): List<Cvr> {

    val mvrs = mutableListOf<Cvr>()
    val info = contest.info()
    val infos = mapOf( contest.id to info)

    // add the regular cvrs
    if (cvrNcards > 0) {
        val vunderCvrs = Vunder(cvrVotes, cvrUndervotes, info.voteForN)
        val cvrCvrs = makeVunderCvrs(mapOf(info.id to vunderCvrs), "regularCvr", poolId = null)
        mvrs.addAll(cvrCvrs) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    }

    // add the pooled cvrs
    pool.contests().forEach { contestId ->
        val vunderPool = pool.votesAndUndervotes(contestId, infos[contestId]?.voteForN ?: 1)
        val poolCvrs = makeVunderCvrs(mapOf(info.id to vunderPool), pool.poolName, poolId = pool.poolId)
        mvrs.addAll(poolCvrs)
    }

    // add phantoms
    repeat(contest.Nphantoms()) {
        mvrs.add(Cvr("phantom$it", mapOf(contest.info().id to intArrayOf()), phantom = true))
    }
    require(contest.Nc() == mvrs.size)

    // add the extra cvrs: these are also in the pool, and they cause the margin to be diluted
    val extraContestId = contest.id+1
    repeat(extraInPool) {
        mvrs.add( Cvr("extra$it", mapOf(extraContestId to intArrayOf()), false, poolId=pool.id()))
    }

    mvrs.shuffle()
    return mvrs
}

// make the card manifest
fun makeCardManifest(mvrs: List<Cvr>, pool: OneAuditPoolWithBallotStyle): List<AuditableCard> {
    // the union of the first two styles
    val expandedContestIds = pool.infos.keys.toList().toIntArray()

    // here we put the pool data into a single pool, and combine their contestIds, to get a diluted margin for testing
    // val cardStyle = Population("cardPoolStyle", pool.poolId, expandedContestIds, false)

    // make the cards with the expanded card style
    val converter = CvrsWithPopulationsToCardManifest(
        type = AuditType.ONEAUDIT,
        cvrs = Closer(mvrs.iterator()),
        phantomCvrs = null,
        listOf(pool),
    )
    val cards = mutableListOf<AuditableCard>()
    converter.forEach { cards.add(it) }

    // we need to populate the pool tab with the votes
    val poolTabs = OneAuditPoolFromCvrs("pool", 1, false, pool.infos)
    expandedContestIds.forEach { id -> poolTabs.contestTabs[id] = ContestTabulation(pool.infos[id]!!) }
    mvrs.forEach { mvr ->
        if (mvr.poolId == pool.poolId) poolTabs.accumulateVotes(mvr)
    }

    // should be the same as pool, leave in as consistency check
    assertEquals(pool.voteTotals[1]?.votes, poolTabs.contestTabs[1]?.votes)

    return cards
}


////////////////////////////////////////////////////////////////////////////////////////////////

fun makeOneAuditTestContests(
    infos: Map<Int, ContestInfo>, // all the contests in the pools
    contestsToAudit: List<Contest>, // the contests you want to audit
    cardStyles: List<PopulationIF>,
    cardManifest: List<AuditableCard>,
    mvrs: List<Cvr>, // this must be just for tests
): Pair<List<ContestUnderAudit>, List<OneAuditPoolIF>> {

    val cards = Closer(cardManifest.iterator())
    val manifestTabs = tabulateAuditableCards(cards, infos)
    val npopMap = manifestTabs.mapValues { it.value.ncards }

    // create pools from cardStyles and populate the pool counts from the mvrs
    val poolsFromCvrs = calcOneAuditPoolsFromMvrs(infos, cardStyles, mvrs)

    // create the OneAudit contests
    val contestsUA = makeOneAuditContests(contestsToAudit, npopMap, poolsFromCvrs)

    return Pair(contestsUA, poolsFromCvrs)
}
