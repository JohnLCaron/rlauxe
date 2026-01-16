package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.OneAuditVunderFuzzer
import org.cryptobiotic.rlauxe.estimate.showChangeMatrix
import org.cryptobiotic.rlauxe.estimate.sumDiagonal
import org.cryptobiotic.rlauxe.estimate.sumOffDiagonal
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.mergeReduceS
import org.cryptobiotic.rlauxe.util.tabulateCards
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateVotesWithUndervotes
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestOneAuditFuzzers {
    val showOA = true

    @Test
    fun testMakeFuzzedCvrsFrom() {
        val Nc = 10000
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val choiceChanges = mutableListOf<MutableMap<String, Int>>()
        fuzzPcts.forEach { fuzzPct ->
            println("===================================")
            val welfordFromCvrs = Welford()
            val welfordFromFuzz = Welford()
            margins.forEach { margin ->
                val (contestOA, mvrs, cardManifest, pools) = makeOneAuditTest(
                    margin,
                    Nc,
                    cvrFraction = .70,
                    undervoteFraction = .01,
                    phantomFraction = .01,
                    extraInPool=0,
                )
                val ncands = contestOA.ncandidates
                val contest = contestOA.contest as Contest
                val contestId = contest.id
                if (showOA) println("ncands = $ncands fuzzPct = $fuzzPct, margin = $margin ${contest.votes}")

                val vunder = tabulateVotesWithUndervotes(mvrs.iterator(), contestOA.id, ncands)
                if (showOA) println("cvrVotes = ${vunder}  contestVotes = ${contest.votesAndUndervotes()}")
                assertEquals(vunder, contest.votesAndUndervotes())
                assertEquals(Nc, mvrs.size)

                val fuzzed = makeFuzzedCvrsFrom(listOf(contestOA.contest.info()), mvrs, fuzzPct, welfordFromFuzz)
                val mvrVotes = tabulateVotesWithUndervotes(fuzzed.iterator(), contestOA.id, ncands)
                if (showOA) println("mvrVotes = ${mvrVotes}")

                val choiceChange = mutableMapOf<String, Int>() // org-fuzz -> count
                mvrs.zip(fuzzed).forEach { (mvr, fuzzedCvr) ->
                    if (!mvr.phantom) {
                        val orgChoice = mvr.votes[contestId]!!.firstOrNull() ?: ncands
                        val fuzzChoice = fuzzedCvr.votes[contestId]!!.firstOrNull() ?: ncands
                        val changeKey = (orgChoice).toString() + fuzzChoice.toString()
                        val count = choiceChange[changeKey] ?: 0
                        choiceChange[changeKey] = count + 1
                    }
                }
                if (showOA) {
                    println(" choiceChange")
                    print(showChangeMatrix(ncands, choiceChange))
                    // choiceChange.toSortedMap().forEach { println("  $it") }
                }
                val ncast = contestOA.contest.Ncast()
                choiceChanges.add(choiceChange)
                val allSum = choiceChange.values.sum()
                assertEquals(ncast, allSum)

                val changed = sumOffDiagonal(ncands, choiceChange)
                val unchanged = sumDiagonal(ncands, choiceChange)
                assertEquals(ncast, changed + unchanged)

                val changedPct = 1.0 - unchanged / ncast.toDouble()
                if (showOA) println(" unchanged=$unchanged = changedPct=$changedPct should be ${1.0 - fuzzPct} diff = ${
                    df(
                        fuzzPct - changedPct
                    )
                }")
                welfordFromCvrs.update(fuzzPct - changedPct)
                if (showOA) println()
            }
            println(" fuzzPct =$fuzzPct welfordFromCvrs: ${welfordFromCvrs.show()}")
            println(" welfordFromFuzz: ${welfordFromFuzz.show()}")
        }

        val totalChange = mutableMapOf<String, Int>()
        totalChange.mergeReduceS(choiceChanges)
    }

// class OneAuditPairFuzzer(
//    val poolMvrs: Map<Int, List<Cvr>>,
//    val infos: Map<Int, ContestInfo>,
//    val fuzzPct: Double,
//) {

    @Test
    fun testOneAuditVunderBarFuzzer() {
        val fuzzPct = 0.001
        val extraPct = 0.01
        val Nc = 10000

        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(
                margin = .01,
                Nc = Nc,
                cvrFraction = .95,
                undervoteFraction = 0.01,
                phantomFraction = 0.005,
                extraInPool= (extraPct * Nc).toInt(), // this creates a second contest with only undervotes
            )

        val info = contestOA.contest.info()
        val infos = mapOf(info.id to info, 2 to ContestInfo(2))
        val mvrTabs = tabulateCvrs(mvrs.iterator(), infos)
        val mvrTab = mvrTabs[info.id]!!
        println("mvrTab = $mvrTab")

        assertEquals(mvrTab.votes, contestOA.contest.votes())
        assertEquals(mvrTab.ncards, contestOA.contest.Nc())

        assertEquals(1, pools.size)
        val cardPool = pools.first()
        println(cardPool.show())
        assertTrue(cardPool.hasContest(info.id))
        assertFalse(cardPool.hasContest(42))
        assertEquals(2, cardPool.contests().size) // when extraPct > 0

        val countPoolCards = cards.count { it.poolId == cardPool.poolId }
        assertEquals(countPoolCards, cardPool.ncards())

        val vunderFuzz = OneAuditVunderFuzzer(pools, infos, fuzzPct, cards)
        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.fuzzedPairs
        assertEquals(cards.size, oaFuzzedPairs.size)

        val countPoolCards2 = oaFuzzedPairs.count { it.second.poolId == cardPool.poolId }
        assertEquals(countPoolCards, countPoolCards2)

        val fuzzedMvrs = oaFuzzedPairs.map { it.first }
        val fuzzedMvrTab = tabulateCards(fuzzedMvrs.iterator(), infos)
        println("fuzzedMvrTab= ${fuzzedMvrTab[info.id]}")

        val countPoolCards3 = fuzzedMvrs.count { it.poolId == cardPool.poolId }
        assertEquals(countPoolCards, countPoolCards3)

        val fuzzedPool = calcOneAuditPoolsFromMvrs(
            infos,
            populations = listOf(Population("fuzzedPool", 42, intArrayOf(1, 2), false)),
            fuzzedMvrs.map { it.cvr() },
        )
        println("fuzzedPool= ${fuzzedPool.first().show()}")
        println()

        /* cvrs arent exact ??
        val poolMvrs: Map<Int, List<Cvr>> = pools.associate { it.poolId to it.simulateMvrsForPool() }
        poolMvrs.forEach { println("pool ${it.key} has ncards= ${cardPool.ncards()} and ${it.value.size} simulatedMvrsForPool") }

        // makeOneAuditTestP adds separate cards for "extra"; simulateMvrsForPool combines them.
        // so we dont have enough simulatedMvrsForPoolfor the fuzzing

        // what about a real contest? simulateMvrsForPool has to match whatever ncards has.

        val oaFuzzer = OneAuditPairFuzzer( poolMvrs, infos, fuzzPct)

        val oaFuzzedPairs: List<Pair<CardIF, AuditableCard>> = oaFuzzer.makeFromCards(cards)
        assertEquals(cards.size, oaFuzzedPairs.size) */
    }


}