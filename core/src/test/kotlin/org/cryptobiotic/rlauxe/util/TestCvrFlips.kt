package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.sampling.makeFlippedMvrs
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class TestCvrFlips {

    @Test
    fun testCvrFlips() {
        val mean = .55
        val cvrs = makeCvrsByExactMean(1000, mean)

        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        val contest =  makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions(cvrs)
        val margin = contestUA.minMargin()
        assertEquals(mean2margin(mean), margin, doublePrecision)

        val minClcaAssertion: ClcaAssertion = contestUA.minClcaAssertion()!!
        val cassorter = (minClcaAssertion.cassorter as ClcaAssorter)
        val assorter = cassorter.assorter
        val calcAssorter = assorter.calcAssorterMargin(0, cvrs)
        println("margin = $margin reportedMargin=${assorter.reportedMargin()} calcAssorterMargin=${calcAssorter}")

        var p2o = .01
        var mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        var calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o=$p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)

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

    @Test
    fun testFalsePositives() {
        val mean = .505
        val cvrs = makeCvrsByExactMean(1000, mean)

        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        val contest =  makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions(cvrs)
        val margin = contestUA.minMargin()
        assertEquals(mean2margin(mean), margin, doublePrecision)

        val minClcaAssertion: ClcaAssertion = contestUA.minClcaAssertion()!!
        val cassorter = (minClcaAssertion.cassorter as ClcaAssorter)
        val assorter = cassorter.assorter
        val calcAssorter = assorter.calcAssorterMargin(0, cvrs)
        println("margin = $margin reportedMargin=${assorter.reportedMargin()} calcAssorterMargin=${calcAssorter}")

        var p2o = .01
        var mvrs = makeFlippedMvrs(cvrs, cvrs.size, p2o, null)
        var calcMargin = assorter.calcAssorterMargin(0, mvrs)
        println("flip p2o=$p2o: calcMargin = ${df(calcMargin)} diff = ${df(margin-calcMargin)}")
        assertEquals(margin-calcMargin, 2 * p2o, doublePrecision)

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