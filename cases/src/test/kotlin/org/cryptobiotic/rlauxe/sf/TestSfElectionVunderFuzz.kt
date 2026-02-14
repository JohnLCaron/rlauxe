package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.*

import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.workflow.CardManifest
import org.cryptobiotic.rlauxe.workflow.readPopulations
import kotlin.collections.iterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.use

class TestSfElectionVunderFuzz {
    val cardManifest: CardManifest
    val config: AuditConfig
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>

    val cardPools: List<OneAuditPoolFromCvrs>?
    val privateMvrs: CloseableIterator<AuditableCard>

    init {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val auditRecord = AuditRecord.readFrom(auditdir) as AuditRecord
        cardManifest = auditRecord.readCardManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        infos = contests.map { it.contest.info() }.associateBy { it.id }

        cardPools = auditRecord.readCardPools()

        val publisher = Publisher(auditdir)
        privateMvrs = readCardsCsvIterator(publisher.privateMvrsFile())
    }

    @Test
    fun testSFvunderFuzz() {
        val contestCards = mutableListOf<AuditableCard>()
        val ncards = 30_000
        var countCards = 0

        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext() && countCards < ncards) {
                val card = iter.next()
                contestCards.add(card)
                countCards++
            }
        }

        // simulate the card pools for all OneAudit contests; do it here one time for all contests
        val vunderFuzz = OneAuditVunderFuzzer(cardPools!!, infos, config.simFuzzPct ?: 0.0, contestCards)

        val pairs = vunderFuzz.mvrCvrPairs
        println(" pairs = ${pairs.size}")

        val cardPoolMap = cardPools.associateBy { it.poolId }

        var countMvr49 = 0
        var countCvr49 = 0
        var showCards = 0
        pairs.forEach { (mvr, cvr) ->
            assertEquals(mvr.location, cvr.location)
            assertEquals(mvr.poolId, cvr.poolId)
            if (mvr.hasContest(49)) countMvr49++
            if (mvr.hasContest(49)) countCvr49++

            if (cvr.poolId != null && showCards < 3) {
                println("mvr $mvr")
                println("cvr $cvr")
                println("pool ${cardPoolMap[cvr.poolId]?.contests().contentToString()}")
                println()
                showCards++
            }
        }
        println("countMvr49 = $countMvr49")
        println("countCvr49 = $countCvr49")
        println()
        assertEquals(countCvr49, countMvr49)

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
    fun testSFvunderPoolAvg() {
        val contestId = 29
        val useContest = contests.find { it.id == contestId }!!
        val useCassorter = useContest.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val usePassorter = useCassorter.assorter
        println(useContest)

        val cardPoolMap = cardPools!!.associateBy { it.poolId }
        val useCardPoolId = 3744
        val useCardPool = cardPoolMap[useCardPoolId]
        println(useCardPool)
        println("cvr assort calculated average for contest=${contestId} pool=$useCardPoolId = ${useCassorter.poolAverages.assortAverage[useCardPoolId]}")

        val mvrPoolAvg = findPoolAverage(privateMvrs, contestId, useCardPoolId, usePassorter)
        println("mvr poolAvg = ${mvrPoolAvg}")
        val mvrDilutedMargin2 = mvrPoolAvg.margin() * 84 / 336
        println("mvr pool diluted average= ${margin2mean(mvrDilutedMargin2)} margin=${mvrDilutedMargin2}")

        //// so what is the pool average in the vunder fuzzed cards ??
        //// TODO the problem is bassort, finding undervotes instead of missing contest

        val contestCards = mutableListOf<AuditableCard>()
        val ncards = 30_000_000 // all
        var countCards = 0
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext() && countCards < ncards) {
                val card = iter.next()
                contestCards.add(card)
                countCards++
            }
        }

        // simulate the card pools for all OneAudit contests; do it here one time for all contests
        val vunderFuzz = OneAuditVunderFuzzer(cardPools, infos, config.simFuzzPct ?: 0.0, contestCards)
        val pairs = vunderFuzz.mvrCvrPairs
        println(" pairs = ${pairs.size}")
        val fuzzedMvrIter = PairAdapter(pairs.iterator()) { it.first }

        /*
        val mvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.first }, infos)
        val cvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.second }, infos).toSortedMap()
        println("mvrTab = ${mvrTabs[contestId]}")
        println("cvrTab = ${cvrTabs[contestId]}")

        // duplicate Vunders that vunderFuzz uses
        val vunderPools =  VunderPools(cardPools, infos)
        val vunderPool = vunderPools.vunderPools[useCardPoolId]!!
        println("vunderPool = ${vunderPool}") */

        val assortAvg = AssortAvg()
        for (mvr in fuzzedMvrIter) {
            if (mvr.poolId == useCardPoolId) {
                if (mvr.hasContest(contestId)) {
                    val assortVal = usePassorter.assort(mvr.cvr(), usePhantoms = false)
                    assortAvg.totalAssort += assortVal
                    assortAvg.ncards++
                }
            }
        }

        val dilutedMargin = usePassorter.dilutedMargin()
        println("fuzzed poolAvg = ${assortAvg}")
    }

    @Test
    fun testSFvunderPoolAvgIRV() {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val publisher = Publisher(auditdir)
        val config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
        val populations = readPopulations(publisher)
        val pools = populations as List<OneAuditPoolIF>
        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())

        val privateMvrs: CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.privateMvrsFile())

        val contestId = 18
        val useContest = contests.find { it.id == contestId }!!
        val useCassorter = useContest.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val usePassorter = useCassorter.assorter
        println(useContest)
        println(usePassorter)

        val populationMap = populations.associateBy { it.poolId }
        val useCardPoolId = 3744
        val usePopulation = populationMap[useCardPoolId]
        println(usePopulation)
        println("cvr assort calculated average for contest=${contestId} pool=$useCardPoolId = ${useCassorter.poolAverages.assortAverage[useCardPoolId]}")

        val mvrPoolAvg = findPoolAverage(privateMvrs, contestId, useCardPoolId, usePassorter)
        println("mvr poolAvg = ${mvrPoolAvg}")
        val mvrDilutedMargin2 = mvrPoolAvg.margin() * 84 / 336
        println("mvr pool diluted average= ${margin2mean(mvrDilutedMargin2)} margin=${mvrDilutedMargin2}")

        //// so what is the pool average in the vunder fuzzed cards ??
        //// TODO the problem is bassort, finding undervotes instead of missing contest

        val contestCards = mutableListOf<AuditableCard>()
        val ncards = 30_000_000 // all
        var countCards = 0
        cardManifest.cards.iterator().use { iter ->
            while (iter.hasNext() && countCards < ncards) {
                val card = iter.next()
                contestCards.add(card)
                countCards++
            }
        }

        // simulate the card pools for all OneAudit contests; do it here one time for all contests
        val infos = contests.map { it.contest.info() }.associateBy { it.id }

        val cardPools = readCardPoolCsvFile(publisher.cardPoolsFile(),  infos)
        val cardPoolMap = cardPools.associateBy { it.poolId }
        val useCardPool = cardPoolMap[useCardPoolId]!!
        // println(useCardPool)
        val tab = useCardPool.contestTabs[contestId]
        println(tab)

        val vunderFuzz = OneAuditVunderFuzzer(cardPools, infos, config.simFuzzPct ?: 0.0, contestCards)
        val pairs = vunderFuzz.mvrCvrPairs
        println(" pairs = ${pairs.size}")
        val fuzzedMvrIter = PairAdapter(pairs.iterator()) { it.first }

        /*
    val mvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.first }, infos)
    val cvrTabs = tabulateCards(PairAdapter(pairs.iterator()) { it.second }, infos).toSortedMap()
    println("mvrTab = ${mvrTabs[contestId]}")
    println("cvrTab = ${cvrTabs[contestId]}")

    // duplicate Vunders that vunderFuzz uses
    val vunderPools =  VunderPools(cardPools, infos)
    val vunderPool = vunderPools.vunderPools[useCardPoolId]!!
    println("vunderPool = ${vunderPool}") */

        val assortAvg = AssortAvg()
        vunderFuzz.mvrCvrPairs.forEach { (mvr, cvr) ->
            if (mvr.poolId == useCardPoolId) {
                if (mvr.hasContest(contestId)) {
                    val assortVal = usePassorter.assort(mvr.cvr(), usePhantoms = false)
                    assortAvg.totalAssort += assortVal
                    assortAvg.ncards++
                }
            }
        }

        println("fuzzed poolAvg = ${assortAvg}")
    }

}

fun findPoolAverage(cardIter: CloseableIterator<AuditableCard>, contestId: Int, poolId: Int, passorter: AssorterIF): AssortAvg {
    val assortAvg = AssortAvg()
    cardIter.use { iter ->
        for (mvr in iter) {
            if (mvr.poolId == poolId) {
                if (mvr.hasContest(contestId)) {
                    val assortVal = passorter.assort(mvr.cvr(), usePhantoms = false)
                    assortAvg.totalAssort += assortVal
                    assortAvg.ncards++
                    // println("${mvr.location} ${mvr.poolId} votes[$contestId]=${mvr.votes!![contestId].contentToString()}")
                }
            }
        }
    }
    return assortAvg
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
OneAuditPool(poolName=986-0, poolId=3746, hasSingleCardStyle=false, ncards=267, regVotes={39=RegVotes(votes={128=30, 129=24}, ncards=66, undervotes=12), 40=RegVotes(votes={130=30, 131=25}, ncards=66, undervotes=11), 41=RegVotes(votes={132=32, 133=21}, ncards=66, undervotes=13), 42=RegVotes(votes={135=30, 134=22}, ncards=66, undervotes=14), 43=RegVotes(votes={136=21, 137=29}, ncards=66, undervotes=16), 44=RegVotes(votes={139=31, 138=22}, ncards=66, undervotes=13), 45=RegVotes(votes={140=26, 141=31}, ncards=66, undervotes=9), 46=RegVotes(votes={142=25, 143=31}, ncards=66, undervotes=10), 47=RegVotes(votes={144=31, 145=20}, ncards=66, undervotes=15), 48=RegVotes(votes={146=31, 147=18}, ncards=66, undervotes=17), 49=RegVotes(votes={149=44, 148=19}, ncards=66, undervotes=3), 50=RegVotes(votes={150=30, 151=24}, ncards=66, undervotes=12), 51=RegVotes(votes={152=25, 153=20}, ncards=66, undervotes=21), 52=RegVotes(votes={154=17, 155=33}, ncards=66, undervotes=16), 53=RegVotes(votes={157=17, 156=35}, ncards=66, undervotes=14), 18=RegVotes(votes={}, ncards=67, undervotes=5), 19=RegVotes(votes={}, ncards=67, undervotes=16), 21=RegVotes(votes={}, ncards=67, undervotes=14), 22=RegVotes(votes={}, ncards=67, undervotes=18), 20=RegVotes(votes={}, ncards=67, undervotes=24), 29=RegVotes(votes={109=26, 108=32}, ncards=67, undervotes=9), 30=RegVotes(votes={110=38, 111=22}, ncards=67, undervotes=7), 31=RegVotes(votes={113=23, 112=36}, ncards=67, undervotes=8), 32=RegVotes(votes={115=30, 114=26}, ncards=67, undervotes=11), 33=RegVotes(votes={116=31, 117=24}, ncards=67, undervotes=12), 34=RegVotes(votes={118=27, 119=32}, ncards=67, undervotes=8), 35=RegVotes(votes={121=38, 120=20}, ncards=67, undervotes=9), 36=RegVotes(votes={122=24, 123=32}, ncards=67, undervotes=11), 37=RegVotes(votes={124=37, 125=18}, ncards=67, undervotes=12), 38=RegVotes(votes={126=46, 127=14}, ncards=67, undervotes=7), 1=RegVotes(votes={5=25, 2=39, 1=1, 4=1}, ncards=67, undervotes=1), 2=RegVotes(votes={7=26, 8=31}, ncards=67, undervotes=10), 3=RegVotes(votes={9=25, 10=29}, ncards=67, undervotes=13), 5=RegVotes(votes={13=28, 14=32}, ncards=67, undervotes=7), 9=RegVotes(votes={22=24, 21=32}, ncards=67, undervotes=11), 13=RegVotes(votes={30=16, 29=26}, ncards=67, undervotes=25), 14=RegVotes(votes={31=15, 34=16, 35=7, 39=19, 41=9, 40=13, 38=10, 33=14, 37=9, 36=10, 32=2}, ncards=67, undervotes=144), 15=RegVotes(votes={45=6, 47=7, 43=20, 44=15, 46=14, 49=7, 42=9, 48=24}, ncards=67, undervotes=166)})

cvr assort calculated average for contest=29 pool=3744 = 0.5803571428571429
mvr poolAvg = AssortAvg(ncards=84, totalAssort=69.0 avg=0.8214285714285714 margin=0.6428571428571428)
mvr pool diluted average= 0.5803571428571428 margin=0.1607142857142857
fuzzed poolAvg = AssortAvg(ncards=84, totalAssort=69.0 avg=0.8214285714285714 margin=0.6428571428571428)

old way: captures the diluted margin

fuzzed poolAvg = AssortAvg(ncards=336, totalAssort=195.0 avg=0.5803571428571429 margin=0.1607142857142858)
 */



