package org.cryptobiotic.rlauxe.raire

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardManifest
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigUnwrapped
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.persist.json.readElectionInfoUnwrapped
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
import org.cryptobiotic.rlauxe.workflow.readCardPools
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.use

class TestSf2024OneAuditIrv() {
    val config: AuditConfig
    val electionInfo: ElectionInfo
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val cardManifest: CardManifest
    val cardPools: List<OneAuditPoolFromCvrs>
    val mvrs: CloseableIterable<AuditableCard>

    init {
        val auditdir = "$testdataDir/cases/sf2024/oa/audit"
        val publisher = Publisher(auditdir)
        config = readAuditConfigUnwrapped(publisher.auditConfigFile())!!
        electionInfo = readElectionInfoUnwrapped(publisher.electionInfoFile())!!

        contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        infos = contests.map{ it.contest.info() }.associateBy { it.id }

        cardManifest = readCardManifest(publisher, electionInfo.ncards)
        cardPools = readCardPools(publisher, infos)!!

        // use the cvrs from the clca as the mvrs
        val cvrdir = "$testdataDir/cases/sf2024/clca/audit"
        val cvrPublisher = Publisher(cvrdir)
        mvrs = readCardManifest(cvrPublisher, electionInfo.ncards).cards
    }

    @Test
    fun testSf2024oa() {
        val contest24 = contests.find { it.id == 24 }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val rcontest = contest24.contest as RaireContest
        val Npop = rcontestUA.Npop
        val info24 = rcontestUA.contest.info()
        val infos24 = mapOf(24 to info24)

        rcontestUA.rassertions.forEach {
            println("  $it marginPct=${it.marginInVotes / Npop.toDouble()}")
        }
        println("assertionAndDifficulty")
        rcontestUA.assertions.forEach {
            println(" ${it.assorter.shortName()} ${rcontest.showAssertionDifficulty(it.assorter)}")
            if ( it.assorter.shortName() == "NEB 83/167") {
                rcontest.showAssertionDifficulty(it.assorter)
            }
        }

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        // the cards in the pools dont have votes
        val cardTab = tabulateAuditableCards(cardManifest.cards.iterator(), infos24).values.first()
        // println("cardTab.irvVotes = ${cardTab.irvVotes}")
        val cardIrvVotes = cardTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin from cvrs only = ${rassorter.calcMargin(cardIrvVotes, Npop)}")

        val mvrTab = tabulateAuditableCards(mvrs.iterator(), infos24).values.first()
        // println("mvrTab.irvVotes = ${mvrTab.irvVotes}")
        val irvVotes = mvrTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin from mvrs= ${rassorter.calcMargin(irvVotes, Npop)}")

        // sum all the assorter values in one pass across all the cards, using PoolAverage when card is in a pool
        val avgWithPool = AssortAvg()
        val cards = cardManifest.cards.iterator()
        cards.use { cardIter ->
            while (cardIter.hasNext()) {
                val card = cardIter.next()

                if (card.hasContest(rcontestUA.id)) {
                    val assortVal = if (card.poolId != null)
                        cassorter.poolAverages.assortAverage[card.poolId]!!
                    else
                        rassorter.assort(card.cvr(), usePhantoms = false)
                    avgWithPool.totalAssort += assortVal
                    avgWithPool.ncards++
                }
            }
        }
        println()
        println(avgWithPool)
        println("assortAvg.margin = ${avgWithPool.margin()}")
    }

    @Test
    fun testDivideMarginInVotes() {
        val contest24 = contests.find { it.id == 24 }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val rcontest = contest24.contest as RaireContest
        val Npop = rcontestUA.Npop
        val info24 = rcontestUA.contest.info()
        val infos24 = mapOf(24 to info24)

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val allTab = tabulateAuditableCards(mvrs.iterator(), infos24).values.first()
        val allVotes = allTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin from mvrs= ${rassorter.calcMargin(allVotes, Npop)}")
        println("  all calcMarginInVotes= ${rassorter.calcMarginInVotes(allVotes)}")

        val totalCards = 1603908

        // split .17 - .83
        val split = (totalCards*.17).toInt()

        val firstTab = tabulateAuditableCards(SubsetIterator(0, split, mvrs.iterator()), infos24).values.first()
        val firstVotes = firstTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("  first calcMarginInVotes= ${rassorter.calcMarginInVotes(firstVotes)}")

        val secondTab = tabulateAuditableCards(SubsetIterator(split, null, mvrs.iterator()), infos24).values.first()
        val secondVotes = secondTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("  second calcMarginInVotes= ${rassorter.calcMarginInVotes(secondVotes)}")

        assertEquals(rassorter.calcMarginInVotes(allVotes), rassorter.calcMarginInVotes(firstVotes)+rassorter.calcMarginInVotes(secondVotes))
    }

    @Test
    fun testMarginInVotes() {
        val contest24 = contests.find { it.id == 24 }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val Npop = rcontestUA.Npop
        val info24 = rcontestUA.contest.info()
        val infos24 = mapOf(24 to info24)

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val allTab = tabulateAuditableCards(mvrs.iterator(), infos24).values.first()
        val allVotes = allTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("rassorter calcMargin from mvrs= ${rassorter.calcMargin(allVotes, Npop)}")
        println("  mvr calcMarginInVotes= ${rassorter.calcMarginInVotes(allVotes)}")
        val expectedMvrVotes = rassorter.calcMarginInVotes(allVotes)

        // calcMargin = marginInVotes / cardPool.ncards()
        //                   val poolMargin = raireAssorter.calcMargin(irvVotes, cardPool.ncards()) // could just save margin in votes
        //                    assortAverages[cardPool.poolId] = margin2mean(poolMargin)

        var sumMarginInVotes = 0.0
        cardPools.forEach { pop ->
            val pool = pop as OneAuditPoolIF
            val poolAvg = cassorter.poolAverages.assortAverage[pool.poolId]
            if (poolAvg != null) {
                val marginInVotes = mean2margin(poolAvg) * pool.ncards()
                sumMarginInVotes += marginInVotes
            }
        }
        println("sumMarginInVotes= ${sumMarginInVotes.roundToInt()}")
        val poolMarginInVotes = sumMarginInVotes.roundToInt()

        // whats the margin in votes for the cvrs ??
        val cvrTab = tabulateAuditableCards(cardManifest.cards.iterator(), infos24).values.first()
        val cvrVotes = cvrTab.irvVotes.makeVotes(rcontestUA.ncandidates)
        println("  cvrVotes calcMarginInVotes= ${rassorter.calcMarginInVotes(cvrVotes)}")
        val cvrMarginInVotes = rassorter.calcMarginInVotes(cvrVotes)

        // another way to compute the cvr margin
        val avgNoPool = AssortAvg()
        val cards = cardManifest.cards.iterator()
        cards.use { cardIter ->
            while (cardIter.hasNext()) {
                val card = cardIter.next()
                if (card.hasContest(rcontestUA.id) && (card.poolId == null)) {
                    val assortVal = rassorter.assort(card.cvr(), usePhantoms = false)
                    avgNoPool.totalAssort += assortVal
                    avgNoPool.ncards++
                }
            }
        }
        println(avgNoPool)
        println(avgNoPool.margin())
        println((avgNoPool.margin() * avgNoPool.ncards).roundToInt())
        assertEquals((avgNoPool.margin() * avgNoPool.ncards).roundToInt(), cvrMarginInVotes)

        println("expected - actual = ${expectedMvrVotes - cvrMarginInVotes - poolMarginInVotes}")
        assertEquals(expectedMvrVotes, cvrMarginInVotes + poolMarginInVotes)
    }

    @Test
    fun testVerifyOApools() {
        val contestId = 24
        val contest24 = contests.find { it.id == contestId }!!
        val rcontestUA = contest24 as RaireContestWithAssertions
        val Npop = rcontestUA.Npop
        val info24 = rcontestUA.contest.info()
        val infos24 = mapOf(contestId to info24)

        val minAssertion = rcontestUA.minClcaAssertion()!!
        println(minAssertion)
        val cassorter = minAssertion.cassorter as OneAuditClcaAssorter
        println("cassorter dilutedMargin = ${cassorter.dilutedMargin}")

        val passorter = minAssertion.assorter
        val rassorter = minAssertion.assorter as RaireAssorter
        println("rassorter dilutedMargin = ${mean2margin(rassorter.dilutedMean)}")

        val nonpoolTab = ContestTabulation(rcontestUA.contest.info())
        val assortAvg = AssortAvg()

        // the cvrs
        val iter = cardManifest.cards.iterator()
        while (iter.hasNext()) {
            val card = iter.next()
            if (card.hasContest(contestId) && (card.poolId == null)) {
                if (card.phantom) nonpoolTab.nphantoms++
                if (card.votes != null) { // I  think this is always true
                    val cands = card.votes[contestId]!!
                    nonpoolTab.addVotes(cands, card.phantom)  // for IRV
                } else {
                    nonpoolTab.ncardsTabulated++
                }
            }
        }

        val cvrMargin = if (rcontestUA.isIrv) {
            val cvrVotes = nonpoolTab.irvVotes.makeVotes(rcontestUA.ncandidates)
            println("  cvrs calcMarginInVotes= ${rassorter.calcMarginInVotes(cvrVotes)}")
            rassorter.calcMargin(cvrVotes, nonpoolTab.ncardsTabulated)
        } else {
            passorter.calcMarginFromRegVotes(nonpoolTab.votes, nonpoolTab.ncardsTabulated)
        }
        val cvrMean = margin2mean(cvrMargin)
        assortAvg.ncards += nonpoolTab.ncardsTabulated // i think your overcounting ncards here
        assortAvg.totalAssort += nonpoolTab.ncardsTabulated * cvrMean

        // the pools
        var sumMarginInVotes2 = 0.0
        cardPools.forEach { pool ->
            val poolAvg = cassorter.poolAverages.assortAverage[pool.poolId]
            if (poolAvg != null) {
                assortAvg.totalAssort += poolAvg * pool.ncards()
                assortAvg.ncards += pool.ncards()
                sumMarginInVotes2 += mean2margin(poolAvg) * pool.ncards()
            }
        }

        var sumMarginInVotes = 0.0
        cardPools.forEach { pool ->
            val poolAvg = cassorter.poolAverages.assortAverage[pool.poolId]
            if (poolAvg != null) {
                val marginInVotes = mean2margin(poolAvg) * pool.ncards()
                sumMarginInVotes += marginInVotes
            }
        }
        println("sumMarginInVotes= ${sumMarginInVotes.roundToInt()}")
        val poolMarginInVotes = sumMarginInVotes.roundToInt()
        assertEquals(sumMarginInVotes2.roundToInt(), poolMarginInVotes)

        val dilutedMargin = passorter.dilutedMargin()
        if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
            println("  verifyOApools dilutedMargin does not agree for contest ${rcontestUA.id} assorter '$passorter'")
            println("     dilutedMargin= ${pfn(dilutedMargin)} cardPools assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards} Npop=${rcontestUA.Npop}")
        } else {
            println("  dilutedMargin agrees with cvrs.assortMargin= ${pfn(assortAvg.margin())} for contest ${rcontestUA.id} assorter '$passorter'")
        }
    }
}