package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.calcAssorterMargin
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.estimate.partition
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull


class TestClcaDilutedVsReportedMargins {

    // isnt it a problem that we get noerror when the contest isnt even on the card? it should not count one way or another = 1/2


    // whether cassorter == assorter.reportedMargin() or assorter.dilutedMargin(). the average is noerror when hasStyle = false
    // so what determines whether assorter.reportedMargin() or assorter.dilutedMargin() is correct ??
    // in SHANGRLA 3.2, we have "Define v ≡ 2Āc − 1, the reported assorter margin."
    // But I think this assumes all cards have the contest? If some dont, dont you need the diluted margin ??
    // but if you intercept those and "discard" by setting bassort to 1/2, then you are left with the cards with the contest ??
    // ok, just take SHANGRLA at its word, so you want to use the reported votes in noerror. Which makes CLCA more efficient.
    // TODO when hasStyle=true, you dont discard, but count against

    @Test
    fun testClcaAssorterAverage() {
        val cvrBuilder = CvrBuilders()
        val cvrs = cvrBuilder
            .addCvr().addContest("AvB", "0").ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()

        val contestInfo = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("0", "1", "2", "3", "4"),
        )
        val contest = makeContestFromCvrs(contestInfo, cvrs) // Nc is set as number of cvrs with that contest
        println(contest)
        assertEquals(cvrs.size, contest.Nc)
        // make dilutedMargin different than reportedMargin by setting NpopIn != contest.Nc
        val contestUA = ContestWithAssertions(contest, isClca = true, NpopIn = contest.Nc + 2).addStandardAssertions()
        val assertions = contestUA.clcaAssertions

        assertions.forEach {
            val assorter = it.assorter
            val cassorter = it.cassorter

            val assortAvg = cvrs.map { cvr -> cassorter.bassort(cvr, cvr, false) }.average()
            println("${cassorter.shortName()}: cvrMean=${assortAvg} noerror = ${cassorter.noerror}")
            assertEquals(assortAvg, cassorter.noerror, doublePrecision)
        }

        // as long as haveStyle = false, the non-contests return noerror also. so average is noerror.
        println()
        val plusOtherCvrs = cvrBuilder
            .addCvr().addContest("other", "0").ddone()
            .addCvr().addContest("other", "1").ddone()
            .build()
        assertEquals(plusOtherCvrs.size, contestUA.Npop)

        assertions.forEach {
            val assorter = it.assorter
            val cassorter = it.cassorter
            showAverage(plusOtherCvrs, cassorter)
            val assortAvg = plusOtherCvrs.map { cvr -> cassorter.bassort(cvr, cvr, false) }.average()
            println("${cassorter.shortName()}: cvrMean=${assortAvg} noerror = ${cassorter.noerror}")
            assertEquals(assortAvg, cassorter.noerror, doublePrecision)
        }
    }

    fun showAverage(cvrs: List<Cvr>, cassorter: ClcaAssorter) {
        cvrs.forEach {
            println("cvr=$it, bassort= ${cassorter.bassort(it, it, false)}")
        }
    }

}