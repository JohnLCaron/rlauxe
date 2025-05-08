package org.cryptobiotic.rlauxe.oneaudit

import kotlin.test.Test

// when does (winner - loser) / Nc agree with AvgAssortValue?
class TestAvgAssortValues {

    @Test
    fun testAvgAssortValuesSmall() {
        val oaContest1 = makeContestOA(
            11, 6, cvrPercent = .66,
            undervotePercent = .0, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = makeTestMvrs(oaContest1)

        //println("  mvrs1.size=${mvrs1.size}")
        //mvrs1.forEach{ println("   $it")}
        println("\noaContest1 with mvrs")
        checkAssorterAvg(oaContest1, mvrs1)

        val oaContest2 = makeContestOA(
            5, 3, cvrPercent = .50,
            undervotePercent = .0, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = makeTestMvrs(oaContest2)
        //println("  mvrs2.size=${mvrs2.size}")
        //mvrs2.forEach{ println("   $it")}

        val allCvrs = mergeCvrsWithPools(mvrs1, mvrs2)
        //println("  allCvrs.size=${allCvrs.size}")
        //allCvrs.forEach{ println("   $it")}

        val Nc1 = allCvrs.filter { it.hasContest(1) }.count()
        println("oaContest1.Nc=${oaContest1.Nc} count=$Nc1")

        println("\noaContest1 with merged")
        checkAssorterAvg(oaContest1, allCvrs, show = true)

        println("=========================================")
        //println("  mvrs2.size=${mvrs2.size}")
        //mvrs2.forEach{ println("   $it")}
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        checkAssorterAvg(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        checkAssorterAvg(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValues() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .0, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = makeTestMvrs(oaContest1)

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .0, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = makeTestMvrs(oaContest2)

        val allCvrs = mergeCvrsWithPools(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        checkAssorterAvg(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        checkAssorterAvg(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        checkAssorterAvg(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValuesWithUndervotes() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .11, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = makeTestMvrs(oaContest1)

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .17, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = makeTestMvrs(oaContest2)

        val allCvrs = mergeCvrsWithPools(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        checkAssorterAvg(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        checkAssorterAvg(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        checkAssorterAvg(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValuesWithPhantoms() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .11, phantomPercent = .005, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = makeTestMvrs(oaContest1)

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .17, phantomPercent = .01, skewPct = .01, contestId = 2
        )
        val mvrs2 = makeTestMvrs(oaContest2)

        val allCvrs = mergeCvrsWithPools(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        checkAssorterAvg(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        checkAssorterAvg(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        checkAssorterAvg(oaContest2, allCvrs)
    }
}
