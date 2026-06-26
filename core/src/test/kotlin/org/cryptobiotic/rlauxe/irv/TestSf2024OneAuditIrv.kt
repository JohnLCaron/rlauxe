package org.cryptobiotic.rlauxe.irv

import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.SortedManifest
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.SubsetIterator

import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.workflow.readSortedManifest
import kotlin.test.Test
import kotlin.use

class TestSf2024OneAuditIrv() {
    val config: Config
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val cardPools: List<CardPool>

    val cardManifest: SortedManifest
    val mvrsIterable: CloseableIterable<AuditableCard>
    val mvrs = mutableListOf<AuditableCard>()

    init {
        val topdir = "$testdataDir/cases/sf2024/oa"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        // cardManifest = auditRecord.readCardManifest()
        config = auditRecord.config
        contests = auditRecord.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }

        cardPools = auditRecord.readCardPools()!!

        // use the cvrs from the clca as the mvrs
        val cvrdir = "$testdataDir/cases/sf2024/clca"
        val cvrPublisher = Publisher(cvrdir)
        cardManifest = readSortedManifest(cvrPublisher, infos, auditRecord.electionInfo.totalCardCount)
        mvrsIterable = cardManifest.cards

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
        val rcontest = contest24.contest as IrvContest
        println("  ${rcontest.showCandidates()}")

        val poolId = 3464
        val cardPool = cardPools.find { it.poolId == poolId }!! //random pool with contest 24 in it
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

   //  @Test
    fun testCalcVoteMargin() {
        val contest14wa = contests.find { it.id == 14 }!!
        val contest14 = contest14wa.contest
        val info14 = contest14.info()
        val infos14 = mapOf(24 to info14)
        println(contest14)

        val minAssertion = contest14wa.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.assorterMargin}")

        val passorter = minAssertion.assorter
        println("rassorter dilutedMargin = ${mean2margin(passorter.dilutedMean())}")

        val tab2k = tabulateAuditableCards(SubsetIterator(0, 2000, mvrsIterable.iterator()), infos14).values.first()
        val tab2kmargin = passorter.calcMarginFromRegVotes(tab2k.votes, tab2k.ncards())
        println("  first 2000 calcMarginInVotes= ${tab2kmargin}")

        val firstTab = tabulateAuditableCards(SubsetIterator(0, 1000, mvrsIterable.iterator()), infos14).values.first()
        val firstMargin = passorter.calcMarginFromRegVotes(firstTab.votes, firstTab.ncards())
        println("  first 1000 calcMarginInVotes= ${firstMargin}")

        val secondTab = tabulateAuditableCards(SubsetIterator(1000, 1000, mvrsIterable.iterator()), infos14).values.first()
        val secondMargin = passorter.calcMarginFromRegVotes(secondTab.votes, secondTab.ncards())
        println("  second 1000 calcMarginInVotes= ${secondMargin}")

        // assertEquals(tab2kmargin, firstMargin+secondMargin)
    }
}