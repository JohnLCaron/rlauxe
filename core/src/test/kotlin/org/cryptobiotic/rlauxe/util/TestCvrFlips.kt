package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class TestCvrFlips {

    @Test
    fun testCvrFlips() {
        val mean = .55
        val cvrs = makeCvrsByExactMean(1000, mean) // no phantoms

        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        val contest =  makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).addClcaAssertionsFromReportedMargin()
        val margin = contestUA.minMargin()
        assertEquals(mean2margin(mean), margin, doublePrecision)

        val minClcaAssertion: ClcaAssertion = contestUA.minClcaAssertion()!!
        val cassorter = minClcaAssertion.cassorter
        val assorter = cassorter.assorter
        val calcAssorter = assorter.calcAssorterMargin(0, cvrs)
        println("margin = $margin reportedMargin=${assorter.reportedMargin()} calcAssorterMargin=${calcAssorter}")

        var p2o = .01
        var mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        var calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o=$p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(2 * p2o, margin-calcMargin, doublePrecision)

        p2o = .02
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o= $p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)

        var p1o = .02
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, null, p1o)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p1o= $p1o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, p1o, doublePrecision)

        p1o = .01
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, null, p1o)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p1o= $p1o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, p1o, doublePrecision)

        p2o = .03
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o= $p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)
    }

    //flip p2o=0.01: calcMargin = 0.0800 diff = 0.0200
    //flip p2o= 0.02: calcMargin = 0.0600 diff = 0.0400
    //flip p1o= 0.02: calcMargin = 0.0800 diff = 0.0200
    //flip p1o= 0.01: calcMargin = 0.0900 diff = 0.0100
    //flip p2o= 0.03: calcMargin = 0.0400 diff = 0.0600

    @Test
    fun testFalsePositives() {
        val mean = .505
        val cvrs = makeCvrsByExactMean(1000, mean)

        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        val contest =  makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).addClcaAssertionsFromReportedMargin()
        val margin = contestUA.minMargin()
        assertEquals(mean2margin(mean), margin, doublePrecision)

        val minClcaAssertion: ClcaAssertion = contestUA.minClcaAssertion()!!
        val cassorter = minClcaAssertion.cassorter
        val assorter = cassorter.assorter
        val calcAssorter = assorter.calcAssorterMargin(0, cvrs)
        println("margin = $margin reportedMargin=${assorter.reportedMargin()} calcAssorterMargin=${calcAssorter}")

        var p2o = .01
        var mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        var calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o=$p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(2 * p2o, margin-calcMargin, doublePrecision)

        p2o = .02
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o= $p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)

        var p1o = .02
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, null, p1o)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p1o= $p1o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, p1o, doublePrecision)

        p1o = .01
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, null, p1o)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p1o= $p1o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, p1o, doublePrecision)

        p2o = .03
        mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o= $p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)
    }


    // TODO test phantoms and undervotes
}