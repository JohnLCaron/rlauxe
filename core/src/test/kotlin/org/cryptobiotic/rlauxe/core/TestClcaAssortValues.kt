package org.cryptobiotic.rlauxe.core


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
        println("${taus.taus.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.taus.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser, phantom))
    }
//[0.0, 0.2857142857142857, 0.7142857142857143, 1.0, 1.2857142857142856, 1.7142857142857144, 2.0] * noerror=0.5147058823529412
//[0, 1/2u, 1-1/2u, noerror, 1+1/2u, 2-1/2u, 2] * noerror
//     winner-loser expect 0.0000 actual 0.0000 tau='      0' (win-lose)
//     winner-other expect 0.2857 actual 0.2857 tau='   1/2u' (win-oth)
//      other-loser expect 0.7143 actual 0.7143 tau=' 1-1/2u' (oth-los)
//    winner-winner expect 1.0000 actual 1.0000 tau='noerror' (noerror)
//      other-other expect 1.0000 actual 1.0000 tau='noerror' (noerror)
//      loser-loser expect 1.0000 actual 1.0000 tau='noerror' (noerror)
//      loser-other expect 1.2857 actual 1.2857 tau=' 1+1/2u' (oth-win)
//     other-winner expect 1.7143 actual 1.7143 tau=' 2-1/2u' (los-oth)
//     loser-winner expect 2.0000 actual 2.0000 tau='      2' (los-win)

//         Phantom CVRs and MVRs are treated specially:
//            A phantom CVR is considered a non-vote in every contest (assort()=1/2).
//            A phantom MVR is considered a vote for the loser (i.e., assort()=0) in every contest.
// so phantom-* == other-*
// so *-phantom == *-loser

    //     winner-loser expect 0.0000 actual 0.0000 tau='      0' (win-lose)
    //   winner-phantom expect 0.0000 actual 0.0000 tau='      0' (win-lose)
    //     winner-other expect 0.2857 actual 0.2857 tau='   1/2u' (win-oth)
    //      other-loser expect 0.7143 actual 0.7143 tau=' 1-1/2u' (oth-los)
    //    other-phantom expect 0.7143 actual 0.7143 tau=' 1-1/2u' (oth-los)
    //    phantom-loser expect 0.7143 actual 0.7143 tau=' 1-1/2u' (oth-los)
    //  phantom-phantom expect 0.7143 actual 0.7143 tau=' 1-1/2u' (oth-los)
    //    winner-winner expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //      other-other expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //      loser-loser expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //    loser-phantom expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //    phantom-other expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //      loser-other expect 1.2857 actual 1.2857 tau=' 1+1/2u' (oth-win)
    //     other-winner expect 1.7143 actual 1.7143 tau=' 2-1/2u' (los-oth)
    //   phantom-winner expect 1.7143 actual 1.7143 tau=' 2-1/2u' (los-oth)
    //     loser-winner expect 2.0000 actual 2.0000 tau='      2' (los-win)

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
        println("${taus.taus.map { it.first }} * noerror=${cassorter.noerror}")
        println("${taus.taus.map { it.second }} * noerror")
        testAll(cassorter, taus, listOf(winner,other,loser, phantom))
    }
    // PluralityAssorter
    //[0.0, 0.5, 1.0, 1.5, 2.0] * noerror=0.5025125628140703
    //[0, 1-1/2u, 1, 1+1/2u, 2] * noerror

    //      winner-loser expect 0.0000 actual 0.0000 tau='     0' (p2o)
    //     winner-other expect 0.5000 actual 0.5000 tau='1-1/2u' (p1o)
    //      other-loser expect 0.5000 actual 0.5000 tau='1-1/2u' (p1o)
    //    winner-winner expect 1.0000 actual 1.0000 tau='     1' (noerror)
    //      other-other expect 1.0000 actual 1.0000 tau='     1' (noerror)
    //      loser-loser expect 1.0000 actual 1.0000 tau='     1' (noerror)
    //     other-winner expect 1.5000 actual 1.5000 tau='1+1/2u' (p1u)
    //      loser-other expect 1.5000 actual 1.5000 tau='1+1/2u' (p1u)
    //     loser-winner expect 2.0000 actual 2.0000 tau='     2' (p2u)

    //     winner-loser expect 0.0000 actual 0.0000 tau='      0' (win-lose)
    //   winner-phantom expect 0.0000 actual 0.0000 tau='      0' (win-lose)
    //     winner-other expect 0.5000 actual 0.5000 tau=' 1-1/2u' (oth-los)
    //      other-loser expect 0.5000 actual 0.5000 tau=' 1-1/2u' (oth-los)
    //    other-phantom expect 0.5000 actual 0.5000 tau=' 1-1/2u' (oth-los)
    //    phantom-loser expect 0.5000 actual 0.5000 tau=' 1-1/2u' (oth-los)
    //  phantom-phantom expect 0.5000 actual 0.5000 tau=' 1-1/2u' (oth-los)
    //    winner-winner expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //      other-other expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //      loser-loser expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //    loser-phantom expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //    phantom-other expect 1.0000 actual 1.0000 tau='noerror' (noerror)
    //     other-winner expect 1.5000 actual 1.5000 tau=' 1+1/2u' (oth-win)
    //      loser-other expect 1.5000 actual 1.5000 tau=' 1+1/2u' (oth-win)
    //   phantom-winner expect 1.5000 actual 1.5000 tau=' 1+1/2u' (oth-win)
    //     loser-winner expect 2.0000 actual 2.0000 tau='      2' (los-win)

    fun testAll(cassorter: ClcaAssorter, taus: Taus, cvrs: List<Cvr>) {
        val triples = mutableListOf<Triple<Double, Cvr, Cvr>>()
        cvrs.forEach { cvr ->
            cvrs.forEach { mvr ->
                val bassort = cassorter.bassort(mvr=mvr, cvr=cvr)
                triples.add(Triple(bassort, cvr, mvr))
            }
        }
        triples.sortBy { it.first }

        triples.forEach { (_, cvr, mvr) ->
            testOne("${cvr.id}-${mvr.id}", taus, cassorter, cvr, mvr)
        }

    }

    fun testOne(what: String, taus: Taus, cassorter: ClcaAssorter, cvr: Cvr, mvr: Cvr) {
        val cvrValue = if (cvr.isPhantom()) 0.5 else cassorter.assorter.assort(cvr, usePhantoms=false)
        val mvrValue = if (mvr.isPhantom()) 0.0 else cassorter.assorter.assort(mvr, usePhantoms=false)
        val expect = expectV(cvrAssort=cvrValue, mvrAssort=mvrValue, cassorter.assorter().upperBound())
        val actual = cassorter.bassort(mvr=mvr, cvr=cvr) / cassorter.noerror() // hasStyle ??
        val tauName = taus.name(expect)
        val tauDesc = taus.desc(expect)
        println("  ${sfn(what, 15)} tau= ${df(actual)} '${sfn(tauName,7)}' (${tauDesc})")
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