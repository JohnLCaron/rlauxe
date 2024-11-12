package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BallotStyle(val contests: List<String>, val ncards: Int)

val contests: List<Contest> = listOf(
    Contest(
        "city_council", 0, candidateNames = listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
        winnerNames = listOf("Alice"), choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    Contest(
        "mayor", 1, candidateNames = listToMap("Lonnie", "Lenny", "Laney"),
        winnerNames = listOf("Laney"), choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    Contest(
        "dog_catcher", 2, candidateNames = listToMap("Fido", "Rover"),
        winnerNames = listOf("Fido"), choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    Contest(
        "measure_1", 3, candidateNames = listToMap("yes", "no"),
        winnerNames = listOf("no"), SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666
    ),
    Contest(
        "measure_2", 4, candidateNames = listToMap("yes", "no"),
        winnerNames = listOf("no"), SocialChoiceFunction.PLURALITY
    ),
    Contest(
        "measure_3", 5, candidateNames = listToMap("yes", "no"),
        winnerNames = listOf("no"), SocialChoiceFunction.PLURALITY
    ),
)

class TestConsistentSamplingFuzz {

    // the cvrs include all the contests, and always have a vote in that contest
    @Test
    fun testSamplingWithFuzz() {
        TestSamplingWithFuzz(0).runTest()
    }

    // the cvrs include all the contests, but dont always have a vote in that contest
    @Test
    fun testSamplingSkipSome() {
        TestSamplingWithFuzz(1).runTest()
    }

    class TestSamplingWithFuzz(val skipSomeContests: Int) {

        fun runTest() {
            val styles = listOf(
                BallotStyle(listOf("city_council", "mayor", "measure_1", "measure_2", "measure_3"), 1111),
                BallotStyle(listOf("city_council", "measure_3"), 111),
                BallotStyle(listOf("dog_catcher", "measure_1", "measure_2"), 222),
                BallotStyle(listOf("city_council", "mayor"), 666),
            )

            val cvrs = samplingWithFuzz(contests, styles)
            tabulateVotes(cvrs).toSortedMap().forEach { (key, votes) ->
                val totalVotes = votes.values.sum()
                println("  contest $key = ${votes.toSortedMap()} total = $totalVotes")
            }
            println()

            // now we find out who the winners are

            val prng = Prng(123456789012L)
            val cvrsUA = cvrs.mapIndexed { idx, it ->
                CvrUnderAudit(it as Cvr, false, prng.next())
            }
            val cards = cardsPerContest(cvrs)

            val contestsUA = contests.mapIndexed { idx, it ->
                val ncards = cards[it.id]!!
                val ca = ContestUnderAudit(it, ncards, ncards + 2)
                ca.sampleSize = ncards / 11 // TODO
                ca
            }

            val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
            val cvrsUAP = cvrsUA + phantomCVRs
            assertEquals(2112, cvrsUAP.size)

            contestsUA.forEach { contest ->
                contest.makePollingAssertions(cvrsUA)
            }

            val sample_cvr_indices = consistentSampling(contestsUA, cvrsUAP)
            println("nsamples = ${sample_cvr_indices.size}\n")
            contestsUA.forEach {
                print(it)
                it.pollingAssertions.forEach { ass ->
                    println("      $ass")
                }
                println()
            }

            // double check the number of cvrs == sampleSize, and the cvrs are marked as sampled
            println("contest.name (id) == sampleSize")
            contestsUA.forEach { contest ->
                val cvrs = cvrsUAP.filter { it.hasContest(contest.id) && it.sampleNum <= contest.sampleThreshold }
                cvrs.forEach { assertTrue(it.sampled) }
                val count = cvrs.size
                assertEquals(contest.sampleSize, count)
                println(" ${contest.name} (${contest.id}) == ${contest.sampleSize}")
            }
        }

        fun samplingWithFuzz(contests: List<Contest>, styles: List<BallotStyle>): List<CvrIF> {
            val cvrbs = CvrBuilders().addContests(contests)
            val result = mutableListOf<CvrIF>()
            styles.forEach { ballotStyle ->
                val scontests = contests.filter { ballotStyle.contests.contains(it.name) }
                repeat(ballotStyle.ncards) {
                    result.add(samplingWithFuzz(cvrbs, scontests))
                }
            }
            return result
        }

        fun samplingWithFuzz(cvrbs: CvrBuilders, contests: List<Contest>): CvrIF {
            val cvrb = cvrbs.addCrv()
            contests.forEach {
                cvrb.addContest(it.name, chooseCandName(it)).done()
            }
            return cvrb.build()
        }

        fun chooseCandName(contest: Contest): String? {
            val choiceId = if (Random.nextInt(100) < 5) contest.winners[0] else { // make sure winner wins
                val choiceIdx = Random.nextInt(contest.candidates.size + skipSomeContests)
                if (choiceIdx >= contest.candidates.size) return null
                contest.candidates[choiceIdx]
            }
            val findit = contest.candidateNames.entries.find { it.value == choiceId }
            return findit!!.key
        }
    }
}