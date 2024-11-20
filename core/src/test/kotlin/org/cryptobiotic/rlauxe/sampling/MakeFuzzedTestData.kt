package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class FuzzedContest(val contestId: Int, val ncands: Int, val margin: Double) {
    val candidateNames: List<String> = IntArray(ncands).map { "cand$it" }
    val contest = Contest("contest$contestId", contestId, candidateNames = listToMap(candidateNames),
        winnerNames=listOf("cand0"), choiceFunction = SocialChoiceFunction.PLURALITY)
}

class TestDataMaker(margins: List<Double>) {
    val contests: List<FuzzedContest> = margins.mapIndexed { idx, margin -> FuzzedContest(idx, min(idx % 4, 2), margin) }

    val ballotStyles = listOf(
        BallotStyle(listOf("city_council", "mayor", "measure_1", "measure_2", "measure_3"), 1111),
        BallotStyle(listOf("city_council", "measure_3"), 111),
        BallotStyle(listOf("dog_catcher", "measure_1", "measure_2"), 555),
        BallotStyle(listOf("city_council", "mayor"), 666),
    )


    fun findContest(id: Int) = contests.find { it.contestId == id }!!

    fun FuzzedContest.checkWinner(votes: Map<Int, Int>): Boolean {
        if (votes.isEmpty()) return true
        val winningIdx = votes.maxBy { it.value }.key
        val result = winningIdx == this.contest.winners[0]
        if (!result)
            print("")
        return result
    }

    /*
    fun makeRandomTestData(
        skipSomeContests: Int,
        show: Boolean = false
    ): Pair<List<ContestUnderAudit>, List<CvrUnderAudit>> {
        val cvrs = samplingWithThumbSkip(contests, ballotStyles, skipSomeContests)
        println("dog_catcher: $countWinner, $countIndex0, $countIndex1")
        tabulateVotes(cvrs).toSortedMap().forEach { (key, votes) ->
            val totalVotes = votes.values.sum()
            val contest = findContest(key).contest
            assertTrue(contest.checkWinner(votes)) // TODO flip the winner ?
            if (show) println("  contest ${contest.name} ${contest.winners} = ${votes.toSortedMap()} total = $totalVotes")
        }
        if (show) println()

        // now we find out who the winners are
        val prng = Prng(secureRandom.nextLong())
        val cvrsUA = cvrs.mapIndexed { idx, it ->
            CvrUnderAudit(it as Cvr, false)
        }
        val cards = cardsPerContest(cvrs)

        val contestsUA = contests.mapIndexed { idx, it ->
            val ncards = cards[it.id]!!
            val ca = ContestUnderAudit(it, ncards, ncards + 2)
            ca.sampleSize = ncards // TODO
            ca.sampleThreshold = cvrs.size.toLong()
            ca
        }

        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val cvrsUAP = cvrsUA + phantomCVRs
        assertEquals(2445, cvrsUAP.size)

        return Pair(contestsUA, cvrsUAP)
    }

     */

    // random sampling with "thumb on the scale" to make sure winner wins.
    private fun samplingWithThumbSkip(
        contests: List<Contest>,
        styles: List<BallotStyle>,
        skipSomeContests: Int
    ): List<CvrIF> {
        val cvrbs = CvrBuilders().addContests(contests)
        val result = mutableListOf<CvrIF>()
        styles.forEach { ballotStyle ->
            val scontests = contests.filter { ballotStyle.contests.contains(it.name) }
            repeat(ballotStyle.ncards) {
                result.add(samplingWithThumb(cvrbs, scontests, skipSomeContests))
            }
        }
        return result
    }

    private fun samplingWithThumb(cvrbs: CvrBuilders, contests: List<Contest>, skipSomeContests: Int): CvrIF {
        val cvrb = cvrbs.addCrv()
        contests.forEach {
            cvrb.addContest(it.name, chooseCandName(it, skipSomeContests)).done()
        }
        return cvrb.build()
    }

    // random sampling with "thumb on the scale" to make sure winner wins.
// randomly choose candidate to vote for, with 5% extra for the reported winner.
// If skipSomeContests > 0, then some contests wont have a vote
    private fun chooseCandName(contest: Contest, skipSomeContests: Int): String? {
        if (contest.winners.isEmpty()) return null
        val chooseWinner = Random.nextInt(100)
        val choiceId = if (chooseWinner < 5) contest.winners[0] else { // make sure winner wins
            val choiceIdx = Random.nextInt(contest.candidates.size + skipSomeContests)
            if (choiceIdx >= contest.candidates.size) return null
            contest.candidates[choiceIdx]
        }
        if (contest.name == "dog_catcher") {
            if (chooseWinner < 5) countWinner++
            if (choiceId == 0) countIndex0++
            if (choiceId == 1) countIndex1++
        }
        val findit = contest.candidateNames.entries.find { it.value == choiceId }
        return findit!!.key
    }
}