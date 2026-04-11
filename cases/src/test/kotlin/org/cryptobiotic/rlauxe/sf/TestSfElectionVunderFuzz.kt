package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.VunderPoolsFuzzer
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.*

import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.verify.AssortAvg
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.collections.iterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.use

class TestSfElectionVunderFuzz {
    val auditdir = "$testdataDir/cases/sf2024/oa/audit"
    val publisher = Publisher(auditdir)

    val mvrManager: PersistedMvrManager
    val cardManifest: CardManifest
    val config: Config
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>

    val cardPools: List<CardPool>?

    init {
        val auditRecord = AuditRecord.readFrom(auditdir) as AuditRecord
        mvrManager = PersistedMvrManager(auditRecord)
        cardManifest = auditRecord.readSortedManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        infos = contests.map { it.contest.info() }.associateBy { it.id }

        cardPools = auditRecord.readCardPools()
    }

    @Test
    fun testSFvunderFuzz() {
        val contestCards = mutableListOf<AuditableCard>()
        val ncards = 30_000
        var countCards = 0

        // use the first 30_000 actual cards
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext() && countCards < ncards) {
                val card = iter.next()
                contestCards.add(card)
                countCards++
            }
        }

        // simulate the card pools for all OneAudit contests; do it here one time for all contests
        val vunderFuzz = VunderPoolsFuzzer(cardPools!!, infos, config.round.simulation.simFuzzPct ?: 0.0, contestCards)

        val pairs = vunderFuzz.mvrCvrPairs
        println(" pairs = ${pairs.size}")

        val cardPoolMap = cardPools.associateBy { it.poolId }

        // look in detail at contest49
        var countMvr49 = 0
        var countCvr49 = 0
        var showCards = 0
        pairs.forEach { (mvr, cvr) ->
            assertEquals(mvr.location, cvr.location)
            assertEquals(mvr.poolId(), cvr.poolId())
            if (mvr.hasContest(49)) countMvr49++
            if (mvr.hasContest(49)) countCvr49++

            if (cvr.poolId() != null && showCards < 3) {
                println("mvr $mvr")
                println("cvr $cvr")
                println("pool ${cardPoolMap[cvr.poolId()]?.possibleContests().contentToString()}")
                println()
                showCards++
            }
        }
        println("countMvr49 = $countMvr49")
        println("countCvr49 = $countCvr49")
        println()
        assertEquals(countCvr49, countMvr49)

        // contest tabulations for both mvr and cvr
        val mvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.first }, infos)
        val cvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.second }, infos).toSortedMap()

        cvrTabs.forEach { id, cvrTab ->
            val mvrTab = mvrTabs[id]!!
            println("mvrTab = $mvrTab")
            println("cvrTab = $cvrTab")
            println()
        }
    }

    @Test
    fun showSFvunderPoolAvg2() {
        testSFvunderPoolAvg2(29, 3744)
        testSFvunderPoolAvg2(18, 3744)
    }

    fun testSFvunderPoolAvg2(contestId: Int, useCardPoolId: Int) {
        println("--------------------------------------------------")

        val useContest = contests.find { it.id == contestId }!!
        println("useContest = $useContest")
        val cassorter = useContest.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val passorter = cassorter.assorter

        val cardPoolMap = cardPools!!.associateBy { it.poolId }
        val useCardPool = cardPoolMap[useCardPoolId]!!
        print(useCardPool)
        val tab = useCardPool.contestTabs[contestId]
        println(tab)

        val poolAvg = cassorter.poolAverages.assortAverage[useCardPoolId]!!
        println(
            "cvr assort calculated average for contest=${contestId} pool=$useCardPoolId = ${poolAvg} margin = ${mean2margin(poolAvg)}"
        )
        // could be reproduced as (non-IRV) val poolMargin = assertion.assorter.calcMarginFromRegVotes(regVotes.votes, cardPool.ncards())
        println()

        // over all cards
        val countCardsInPool = countCardsInPool(cardManifest.cards.iterator(), contestId, useCardPoolId)
        println("countCardsInPool=${countCardsInPool}")

        val privateMvrs = mvrManager.readCardsAndMerge(publisher.sortedMvrsFile())
        val mvrPoolAvg = findPoolAverage(privateMvrs, contestId, useCardPoolId, passorter)
        println("mvr poolAvg = ${mvrPoolAvg}")

        val cvrPoolAvg = findPoolAverage(cardManifest.cards.iterator(), contestId, useCardPoolId, passorter)
        println("cvrPoolAvg = ${cvrPoolAvg}")

        // over all mvr, cvr pairs
        val privateMvrs2 = mvrManager.readCardsAndMerge(publisher.sortedMvrsFile())
        val clcaPoolAvg =
            findPoolAverageB(privateMvrs2, cardManifest.cards.iterator(), contestId, useCardPoolId, cassorter)
        println("clcaPoolAvg = ${clcaPoolAvg}")

        // TODO what can we test?

        ///////////////////////////////////////////////////////////////////////////////////////////
        val contestCards = mutableListOf<AuditableCard>()
        val ncards = 100_000 // all
        var countCards = 0
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext() && countCards < ncards) {
                val card = iter.next()
                contestCards.add(card)
                countCards++
            }
        }

        // simulate the card pools for all OneAudit contests; do it here one time for all contests
        val vunderFuzz = VunderPoolsFuzzer(cardPools, infos, config.round.simulation.simFuzzPct ?: 0.0, contestCards)
        val vunderPool = vunderFuzz.vunderPools.vunderPools[useCardPoolId]!!
        val vunderPicker = vunderPool.vunderPickers[contestId]!!
        println("vunder= ${vunderPicker.vunder}")

        val mvrCvrPairs = vunderFuzz.mvrCvrPairs
        val vunderAvg = findPoolAverageB(mvrCvrPairs, contestId, useCardPoolId, cassorter)

        println("vunderAvg = ${vunderAvg}")
    }

    /*
    useContest = PROPOSITION 2 (29) votes={108=290905, 109=91500} Nc=409893 Npop=567428 minDilutedMargin=0.3514
    OneAuditPoolFromCvrs(poolName='811-0', poolId=3744, totalCards=336
    ContestTabulation(id=29 isIrv=false, voteForN=1, votes=[108=67, 109=13], nvotes=80 ncards=84, undervotes=4, novote=4, overvotes=0)
    cvr assort calculated average for contest=29 pool=3744 = 0.5803571428571429 margin = 0.1607142857142858

    countCardsInPool=336
    mvr poolAvg = AssortAvg(ncards=84, totalAssort=69.0 avg=0.8214285714285714 margin=0.6428571428571428)
    cvrPoolAvg = AssortAvg(ncards=336, totalAssort=168.0 avg=0.5 margin=0.0)
    missingInMvr= 252
    clcaPoolAvg = AssortAvg(ncards=336, totalAssort=203.8116459333505 avg=0.6065822795635432 margin=0.21316455912708632)
    vunder= id=29, voteForN=1, votes={108=67, 109=13}, nvotes=80 ncards=336, undervotes=4, missing=252
    missingInMvr= 13
    vunderAvg = AssortAvg(ncards=17, totalAssort=10.089846310954364 avg=0.5935203712326097 margin=0.1870407424652194)
    --------------------------------------------------
    useContest = MAYOR (18) Nc=410105 Npop=567598 winner 57 losers [54, 55, 56, 58, 59, 60, 61, 62, 63, 64, 65, 66, 173, 175, 176] minMargin=0.0536
    OneAuditPoolFromCvrs(poolName='811-0', poolId=3744, totalCards=336
    ContestTabulation(id=18 isIrv=true, voteForN=1, votes=[], nvotes=81 ncards=84, undervotes=3, novote=3, overvotes=0)
    cvr assort calculated average for contest=18 pool=3744 = 0.5119047619047619 margin = 0.023809523809523725

    countCardsInPool=336
    mvr poolAvg = AssortAvg(ncards=84, totalAssort=46.0 avg=0.5476190476190477 margin=0.09523809523809534)
    cvrPoolAvg = AssortAvg(ncards=336, totalAssort=168.0 avg=0.5 margin=0.0)
    missingInMvr= 252
    clcaPoolAvg = AssortAvg(ncards=336, totalAssort=172.62697269745865 avg=0.5137707520757698 margin=0.027541504151539664)
    vunder= id=18, voteForN=1, votes={55=1, 57=1, 58=1, 61=1, 62=1, 64=1, 66=1}, nvotes=81 ncards=336, undervotes=3, missing=252
    missingInMvr= 13
    vunderAvg = AssortAvg(ncards=17, totalAssort=8.887010747215463 avg=0.5227653380714978 margin=0.04553067614299566)
    */
}

fun findPoolAverageB(mvrs: CloseableIterator<AuditableCard>, cvrs: Iterator<AuditableCard>, contestId: Int, poolId: Int, cassorter: ClcaAssorter): AssortAvg {
    var missingInMvr = 0
    val assortAvg = AssortAvg()
    mvrs.use { iter ->
        for (mvr in iter) {
            val cvr = cvrs.next()
            if (cvr.poolId() == poolId) {
                if (cvr.hasContest(contestId)) {
                    if (!mvr.hasContest(contestId)) {
                        missingInMvr++
                    }
                    val assortVal = cassorter.bassort(mvr, cvr, hasStyle=cvr.hasStyle())
                    assortAvg.totalAssort += assortVal
                    assortAvg.ncards++
                }
            }
        }
    }
    println("  missingInMvr= $missingInMvr")
    return assortAvg
}

fun findPoolAverageB(mvrCvrPairs: List<Pair<AuditableCard, AuditableCard>>, contestId: Int, poolId: Int, cassorter: ClcaAssorter): AssortAvg {
    var missingInMvr = 0
    val assortAvg = AssortAvg()
    mvrCvrPairs.forEach { (mvr, cvr) ->
        if (cvr.poolId() == poolId) {
            if (cvr.hasContest(contestId)) {
                if (!mvr.hasContest(contestId)) {
                    missingInMvr++
                }
                val assortVal = cassorter.bassort(mvr, cvr, hasStyle=cvr.hasStyle())
                assortAvg.totalAssort += assortVal
                assortAvg.ncards++
            }
        }
    }
    println("  missingInMvr pairs= $missingInMvr")
    return assortAvg
}

fun findPoolAverage(cardIter: CloseableIterator<AuditableCard>, contestId: Int, poolId: Int, passorter: AssorterIF): AssortAvg {
    val assortAvg = AssortAvg()
    cardIter.use { iter ->
        for (card in iter) {
            if (card.poolId() == poolId) {
                if (card.hasContest(contestId)) {
                    val assortVal = passorter.assort(card, usePhantoms = false)
                    assortAvg.totalAssort += assortVal
                    assortAvg.ncards++
                    // println("${mvr.location} ${mvr.poolId} votes[$contestId]=${mvr.votes!![contestId].contentToString()}")
                }
            }
        }
    }
    return assortAvg
}

fun countCardsInPool(cardIter: CloseableIterator<AuditableCard>, contestId: Int, poolId: Int): Int {
    var ncards = 0
    cardIter.use { iter ->
        for (mvr in iter) {
            if (mvr.poolId() == poolId) {
                if (mvr.hasContest(contestId)) {
                    ncards++
                    // println("${mvr.location} ${mvr.poolId} votes[$contestId]=${mvr.votes!![contestId].contentToString()}")
                }
            }
        }
    }
    return ncards
}

class PairAdapter(val org: Iterator<Pair<AuditableCard, AuditableCard>>,
                  val trans: (Pair<AuditableCard, AuditableCard>) -> AuditableCard)
    : AbstractIterator<AuditableCard>() {

    override fun computeNext() {
        if (org.hasNext())
            setNext(trans(org.next()))
        else
            done()
    }
}

/*
new way: captures the actual mvr average

PROPOSITION 2 (29) votes={108=290905, 109=91500} Nc=409893 Npop=567428 minDilutedMargin=0.3514
OneAuditPool(poolName=986-0, poolId=3746, hasExactContests=false, ncards=267, regVotes={39=RegVotes(votes={128=30, 129=24}, ncards=66, undervotes=12), 40=RegVotes(votes={130=30, 131=25}, ncards=66, undervotes=11), 41=RegVotes(votes={132=32, 133=21}, ncards=66, undervotes=13), 42=RegVotes(votes={135=30, 134=22}, ncards=66, undervotes=14), 43=RegVotes(votes={136=21, 137=29}, ncards=66, undervotes=16), 44=RegVotes(votes={139=31, 138=22}, ncards=66, undervotes=13), 45=RegVotes(votes={140=26, 141=31}, ncards=66, undervotes=9), 46=RegVotes(votes={142=25, 143=31}, ncards=66, undervotes=10), 47=RegVotes(votes={144=31, 145=20}, ncards=66, undervotes=15), 48=RegVotes(votes={146=31, 147=18}, ncards=66, undervotes=17), 49=RegVotes(votes={149=44, 148=19}, ncards=66, undervotes=3), 50=RegVotes(votes={150=30, 151=24}, ncards=66, undervotes=12), 51=RegVotes(votes={152=25, 153=20}, ncards=66, undervotes=21), 52=RegVotes(votes={154=17, 155=33}, ncards=66, undervotes=16), 53=RegVotes(votes={157=17, 156=35}, ncards=66, undervotes=14), 18=RegVotes(votes={}, ncards=67, undervotes=5), 19=RegVotes(votes={}, ncards=67, undervotes=16), 21=RegVotes(votes={}, ncards=67, undervotes=14), 22=RegVotes(votes={}, ncards=67, undervotes=18), 20=RegVotes(votes={}, ncards=67, undervotes=24), 29=RegVotes(votes={109=26, 108=32}, ncards=67, undervotes=9), 30=RegVotes(votes={110=38, 111=22}, ncards=67, undervotes=7), 31=RegVotes(votes={113=23, 112=36}, ncards=67, undervotes=8), 32=RegVotes(votes={115=30, 114=26}, ncards=67, undervotes=11), 33=RegVotes(votes={116=31, 117=24}, ncards=67, undervotes=12), 34=RegVotes(votes={118=27, 119=32}, ncards=67, undervotes=8), 35=RegVotes(votes={121=38, 120=20}, ncards=67, undervotes=9), 36=RegVotes(votes={122=24, 123=32}, ncards=67, undervotes=11), 37=RegVotes(votes={124=37, 125=18}, ncards=67, undervotes=12), 38=RegVotes(votes={126=46, 127=14}, ncards=67, undervotes=7), 1=RegVotes(votes={5=25, 2=39, 1=1, 4=1}, ncards=67, undervotes=1), 2=RegVotes(votes={7=26, 8=31}, ncards=67, undervotes=10), 3=RegVotes(votes={9=25, 10=29}, ncards=67, undervotes=13), 5=RegVotes(votes={13=28, 14=32}, ncards=67, undervotes=7), 9=RegVotes(votes={22=24, 21=32}, ncards=67, undervotes=11), 13=RegVotes(votes={30=16, 29=26}, ncards=67, undervotes=25), 14=RegVotes(votes={31=15, 34=16, 35=7, 39=19, 41=9, 40=13, 38=10, 33=14, 37=9, 36=10, 32=2}, ncards=67, undervotes=144), 15=RegVotes(votes={45=6, 47=7, 43=20, 44=15, 46=14, 49=7, 42=9, 48=24}, ncards=67, undervotes=166)})

cvr assort calculated average for contest=29 pool=3744 = 0.5803571428571429
mvr poolAvg = AssortAvg(ncards=84, totalAssort=69.0 avg=0.8214285714285714 margin=0.6428571428571428)
mvr pool diluted average= 0.5803571428571428 margin=0.1607142857142857
fuzzed poolAvg = AssortAvg(ncards=84, totalAssort=69.0 avg=0.8214285714285714 margin=0.6428571428571428)

old way: captures the diluted margin

fuzzed poolAvg = AssortAvg(ncards=336, totalAssort=195.0 avg=0.5803571428571429 margin=0.1607142857142858)
 */



