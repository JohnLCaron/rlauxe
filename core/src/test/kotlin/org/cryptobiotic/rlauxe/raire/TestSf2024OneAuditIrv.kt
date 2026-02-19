package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.workflow.CardManifest
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.SubsetIterator
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean

import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.use

class TestSf2024OneAuditIrv() {
    val config: AuditConfig
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    // val cardManifest: CardManifest
    val cardPools: List<OneAuditPoolFromCvrs>
    val mvrsIterable: CloseableIterable<AuditableCard>
    val mvrs = mutableListOf<AuditableCard>()

    init {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val auditRecord = AuditRecord.readFrom(auditdir) as AuditRecord
        // cardManifest = auditRecord.readCardManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }

        cardPools = auditRecord.readCardPools()!!

        // use the cvrs from the clca as the mvrs
        val cvrdir = "$testdataDir/cases/sf2024/clca/audit"
        val cvrPublisher = Publisher(cvrdir)
        mvrsIterable = readCardManifest(cvrPublisher, auditRecord.electionInfo.ncards).cards

        mvrsIterable.iterator().use { iter ->
            while (iter.hasNext() && mvrs.size < 1000 ) {
                mvrs.add(iter.next())
            }
        }
    }

    @Test
    fun testAssorterMethods() {
        val contest24 = contests.find { it.id == 24 }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val rcontest = contest24.contest as RaireContest
        println("  ${rcontest.showCandidates()}")

        val poolId = 3464
        val cardPool: OneAuditPoolFromCvrs = cardPools.find { it.poolId == poolId }!! //random pool with contest 24 in it
        val poolTab = cardPool.contestTabs[24]!!

        rcontestUA.clcaAssertions.forEach { assertion ->
            val cassorter = assertion.cassorter as OneAuditClcaAssorter
            val rassorter = assertion.assorter as RaireAssorter
            println("${rassorter.shortName()} recountMargin ${rcontest.recountMargin(rassorter)} ${rcontest.showAssertionDifficulty(rassorter)}")

            val wlvotes = rassorter.winnerLoserVotes(poolTab.irvVotes.makeVotes(rcontestUA.ncandidates))
            println("   and ${wlvotes.first} winners and ${wlvotes.second} loser votes in pool $poolId")
            println("   and ${cassorter.assortValuesForPool(poolId)}")

            val assortValues = mvrs.map { rassorter.assort(it) }
            println("   average assort value = ${assortValues.average()}")
        }
    }

    @Test
    fun testCalcVoteMargin() {
        val contest24 = contests.find { it.id == 24 }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val info24 = rcontestUA.contest.info()
        val infos24 = mapOf(24 to info24)

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val allTab = tabulateAuditableCards(SubsetIterator(0, 2000, mvrsIterable.iterator()), infos24).values.first()
        val allVotes = allTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        val allMargin = rassorter.calcVoteMargin(allVotes)
        println("  first 2000 calcMarginInVotes= ${allMargin}")

        val firstTab = tabulateAuditableCards(SubsetIterator(0, 1000, mvrsIterable.iterator()), infos24).values.first()
        val firstVotes = firstTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        val firstMargin = rassorter.calcVoteMargin(firstVotes)
        println("  first 1000 calcMarginInVotes= ${firstMargin}")

        val secondTab = tabulateAuditableCards(SubsetIterator(1000, 1000, mvrsIterable.iterator()), infos24).values.first()
        val secondVotes = secondTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        val secondMargin = rassorter.calcVoteMargin(secondVotes)
        println("  second 1000 calcMarginInVotes= ${secondMargin}")

        assertEquals(allMargin, firstMargin+secondMargin)
    }
}