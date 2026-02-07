package org.cryptobiotic.rlauxe.core


import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.betting.computeBassortValues
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.test.*

class TestClcaAssortValues {
    val hasStyle = true

    @Test
    fun testDhondt() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.DHONDT,
            candidateNames = listToMap("A", "B", "C"),
            minFraction = .05,
        )
        // val votes = mapOf(0 to 1010, 1 to 990) // Map<Int, Int>
        // data class DHondtAssorter(val info: ContestInfo, val winner: Int, val loser: Int, val lastSeatWon: Int, val firstSeatLost: Int): AssorterIF  {
        val assorter = DHondtAssorter(info, winner = 0, loser = 1, lastSeatWon=2, firstSeatLost=5).setDilutedMean(.55)
        assertEquals(assorter, assorter)
        assertEquals(assorter.hashCode(), assorter.hashCode())

        val assorter2 = assorter.copy()
        assertNotEquals(assorter2, assorter)
        assertNotEquals(assorter2.hashCode(), assorter.hashCode())

        val cassorter = ClcaAssorter(info, assorter, dilutedMargin=assorter.dilutedMargin())
        println(cassorter)

        val winner = Cvr("winner", mapOf(0 to intArrayOf(0)))
        val loser = Cvr("loser", mapOf(0 to intArrayOf(1)))
        val other = Cvr("other", mapOf(0 to intArrayOf(2)))
        val phantom = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        val bassv = computeBassortValues(cassorter.noerror, cassorter.assorter.upperBound())
        println(bassv)

        println("DHondtAssorter")
        val taus = Taus(cassorter.assorter().upperBound())
        println("${taus.names.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.names.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser, phantom), hasStyle=false)
    }
// output:
// ClcaAssorter for contest AvB (0)
//  assorter=DHondt w/l='A'/'B': dilutedMean=55.0000% upperBound=1.7500
//  dilutedMargin=0.10000000 dilutedMean=0.55000000 assortUpper=1.75000000 noerror=0.51470588
//[0.0, 0.14705882352941177, 0.36764705882352944, 0.6617647058823529, 0.8823529411764708, 1.0294117647058825]
//DHondtAssorter
//[0.0, 0.2857142857142857, 0.7142857142857143, 1.0, 1.2857142857142856, 1.7142857142857144, 2.0] * noerror=0.5147058823529412
//[win-los, win-oth, oth-los, noerror, oth-win, los-oth, los-win] * noerror
//     winner-loser tau= 0.0000 '      0' (win-los)
//   winner-phantom tau= 0.0000 '      0' (win-los)
//     winner-other tau= 0.2857 '   1/2u' (win-oth)
//      other-loser tau= 0.7143 ' 1-1/2u' (oth-los)
//    other-phantom tau= 0.7143 ' 1-1/2u' (oth-los)
//    phantom-loser tau= 0.7143 ' 1-1/2u' (oth-los)
//  phantom-phantom tau= 0.7143 ' 1-1/2u' (oth-los)
//    winner-winner tau= 1.0000 'noerror' (noerror)
//      other-other tau= 1.0000 'noerror' (noerror)
//      loser-loser tau= 1.0000 'noerror' (noerror)
//    loser-phantom tau= 1.0000 'noerror' (noerror)
//    phantom-other tau= 1.0000 'noerror' (noerror)
//      loser-other tau= 1.2857 ' 1+1/2u' (oth-win)
//     other-winner tau= 1.7143 ' 2-1/2u' (los-oth)
//   phantom-winner tau= 1.7143 ' 2-1/2u' (los-oth)
//     loser-winner tau= 2.0000 '      2' (los-win)

    @Test
    fun testPlurality() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val votes = mapOf(0 to 1010, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=2000)
        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val cassorter = ClcaAssorter(info, assorter, dilutedMargin=assorter.dilutedMargin())

        val winner = Cvr("winner", mapOf(0 to intArrayOf(0)))
        val loser =  Cvr("loser", mapOf(0 to intArrayOf(1)))
        val other =  Cvr("other", mapOf(0 to intArrayOf(2)))
        val phantom =Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        println("PluralityAssorter")
        val taus = Taus(cassorter.assorter().upperBound())
        println("${taus.names.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.names.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser, phantom), hasStyle=false)
    }
    // output:
    // PluralityAssorter
    //[0.0, 0.5, 1.0, 1.5, 2.0] * noerror=0.5025125628140703
    //[win-los, oth-los, noerror, oth-win, los-win] * noerror
    //     winner-loser tau= 0.0000 '      0' (win-los)
    //   winner-phantom tau= 0.0000 '      0' (win-los)
    //     winner-other tau= 0.5000 ' 1-1/2u' (oth-los)
    //      other-loser tau= 0.5000 ' 1-1/2u' (oth-los)
    //    other-phantom tau= 0.5000 ' 1-1/2u' (oth-los)
    //    phantom-loser tau= 0.5000 ' 1-1/2u' (oth-los)
    //  phantom-phantom tau= 0.5000 ' 1-1/2u' (oth-los)
    //    winner-winner tau= 1.0000 'noerror' (noerror)
    //      other-other tau= 1.0000 'noerror' (noerror)
    //      loser-loser tau= 1.0000 'noerror' (noerror)
    //    loser-phantom tau= 1.0000 'noerror' (noerror)
    //    phantom-other tau= 1.0000 'noerror' (noerror)
    //     other-winner tau= 1.5000 ' 1+1/2u' (oth-win)
    //      loser-other tau= 1.5000 ' 1+1/2u' (oth-win)
    //   phantom-winner tau= 1.5000 ' 1+1/2u' (oth-win)
    //     loser-winner tau= 2.0000 '      2' (los-win)

    @Test
    fun testMissing() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val votes = mapOf(0 to 1010, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=2000)
        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val cassorter = ClcaAssorter(info, assorter, dilutedMargin=assorter.dilutedMargin())

        val winner = Cvr("winner", mapOf(0 to intArrayOf(0)))
        val loser =  Cvr("loser", mapOf(0 to intArrayOf(1)))
        val other =  Cvr("other", mapOf(0 to intArrayOf(2)))
        val missing =Cvr("missing", mapOf(1 to intArrayOf(2)))

        println("PluralityWithMissing hasStyle=false")
        val taus = Taus(cassorter.assorter().upperBound())
        println("${taus.names.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.names.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser,missing), hasStyle=false)

        println("\nPluralityWithMissing hasStyle=true")
        println("${taus.names.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.names.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser,missing), hasStyle=true)
    }

    fun testAll(cassorter: ClcaAssorter, taus: Taus, cvrs: List<Cvr>, hasStyle: Boolean) {
        val triples = mutableListOf<Triple<Double, Cvr, Cvr>>()
        cvrs.forEach { cvr ->
            cvrs.forEach { mvr ->
                val bassort = cassorter.bassort(mvr=mvr, cvr=cvr, hasStyle=hasStyle)
                triples.add(Triple(bassort, cvr, mvr))
            }
        }
        // triples.sortBy { it.first }

        triples.forEach { (_, cvr, mvr) ->
            testOne("${cvr.id}-${mvr.id}", taus, cassorter, cvr, mvr, hasStyle=hasStyle)
        }
    }

    fun testOne(what: String, taus: Taus, cassorter: ClcaAssorter, cvr: Cvr, mvr: Cvr, hasStyle: Boolean) {
        val cvrValue =
            if (hasStyle && !cvr.hasContest(cassorter.info.id)) Double.NaN
            else if (cvr.isPhantom()) 0.5
            else cassorter.assorter.assort(cvr, usePhantoms=false)

        val mvrValue =
            if (mvr.isPhantom()) 0.0
            else if (!mvr.hasContest(cassorter.info.id)) { if (hasStyle) 0.0 else 0.5 }
            else cassorter.assorter.assort(mvr, usePhantoms = false)

        val expect = expectV(cvrAssort=cvrValue, mvrAssort=mvrValue, cassorter.assorter().upperBound())
        val actual = cassorter.bassort(mvr=mvr, cvr=cvr, hasStyle=hasStyle) / cassorter.noerror() // hasStyle ??
        val tauName = taus.nameOf(expect)
        val tauDesc = taus.descOf(expect)
        println("  ${sfn(what, 15)} tau= ${df(actual)} '${sfn(tauName,7)}' (${tauDesc})")

        if (expect.isNaN() && actual.isNaN()) return
        if (expect != actual) {
            cassorter.bassort(mvr=mvr, cvr=cvr, hasStyle=hasStyle) / cassorter.noerror() // hasStyle ??
        }
        assertEquals( expect, actual)
    }

    // o = cvr_assort - mvr_assort when l = 0:
    // [0, .5, u] - [0, .5, u] = 0, -.5, -u
    //                         = .5,  0, .5-u
    //                         = u, u-.5, 0
    // u, u-.5, .5,  0, .5-u, -.5, -u = (cvr - mvr) = (u-0), (u-.5), (.5-0), (cvr==mvr), (.5-u), (0-.5), (0-u)

    // (u-0),       cvr has vote for winner, mvr has vote for loser : p2o = 2 vote overstatement
    // (u-.5),      cvr has vote for winner, mvr has vote for other : p1o = 1 vote overstatement
    // (.5-0),      cvr has vote for other, mvr has vote for loser : p1o = 1 vote overstatement
    // (cvr==mvr),  no error
    // (.5-u),      cvr has vote for other, mvr has vote for winner : p1u = 1 vote understatement
    // (0-.5),      cvr has vote for loser, mvr has vote for other  : p1u = 1 vote understatement
    // (0-u)        cvr has vote for loser, mvr has vote for winner : p2u = 2 vote understatement

    // so bassort in
    // [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)

    // B(bi, ci) = (1-o/u)/(2-v/u) = (1-o/u) * noerror, where
    //                o is the overstatement = (cvr_assort - mvr_assort)
    //                u is the upper bound on the value the assorter assigns to any ballot
    //                v is the assorter margin
    //                noerror = 1/(2-v/u) == B(bi, ci) when o = 0 (no error)

    fun expectV(cvrAssort: Double, mvrAssort: Double, upper: Double): Double {
        val o = cvrAssort - mvrAssort
        return (1-o/upper)
    }

}