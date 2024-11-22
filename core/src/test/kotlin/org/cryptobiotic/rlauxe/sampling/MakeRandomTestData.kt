package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BallotStyle(val contests: List<String>, val ncards: Int)

val contestInfo: List<ContestInfo> = listOf(
    ContestInfo(
        "city_council", 0, candidateNames = listToMap("Alice", "Bob", "Charlie", "Doug", "Emily"),
        choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    ContestInfo(
        "mayor", 1, candidateNames = listToMap("Lonnie", "Lenny", "Laney"),
        choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    ContestInfo(
        "dog_catcher", 2, candidateNames = listToMap("Fido", "Rover"),
        choiceFunction = SocialChoiceFunction.PLURALITY
    ),
    ContestInfo(
        "measure_1", 3, candidateNames = listToMap("yes", "no"),
        SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666
    ),
    ContestInfo(
        "measure_2", 4, candidateNames = listToMap("yes", "no"),
        SocialChoiceFunction.PLURALITY
    ),
    ContestInfo(
        "measure_3", 5, candidateNames = listToMap("yes", "no"),
        SocialChoiceFunction.PLURALITY
    ),
)

val ballotStyles = listOf(
    BallotStyle(listOf("city_council", "mayor", "measure_1", "measure_2", "measure_3"), 1111),
    BallotStyle(listOf("city_council", "measure_3"), 111),
    BallotStyle(listOf("dog_catcher", "measure_1", "measure_2"), 555),
    BallotStyle(listOf("city_council", "mayor"), 666),
)

fun makeRandomTestData(skipSomeContests: Int, show: Boolean = false): Pair<List<ContestUnderAudit>, List<CvrUnderAudit>> {
    val cvrs = randomSample(contestInfo, ballotStyles, skipSomeContests)

    // now we find out who the winners are
    val prng = Prng(secureRandom.nextLong())
    val cvrsUA = cvrs.mapIndexed { idx, it ->
        CvrUnderAudit(it as Cvr, false, prng.next())
    }

    val contestsUA = contestInfo.map { info ->
        val ca = ContestUnderAudit(info, cvrs).makePollingAssertions()
        ca.sampleSize = ca.ncvrs / 10 // TODO
        ca.sampleThreshold = 0
        ca
    }

    tabulateVotes(cvrs).toSortedMap().forEach { (key, votes) ->
        val totalVotes = votes.values.sum()
        val contestUA = contestsUA.find { it.id == key }!!
        if (show) println("  contest ${contestUA.name} ${contestUA.contest.winners} = ${votes.toSortedMap()} total = $totalVotes")
    }
    if (show) println()

    val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
    val cvrsUAP = cvrsUA + phantomCVRs
    assertEquals(2443, cvrsUAP.size)

    return Pair(contestsUA, cvrsUAP)
}

// random sampling with "thumb on the scale" to make sure winner wins.
private fun randomSample(contestInfos: List<ContestInfo>, styles: List<BallotStyle>, skipSomeContests: Int): List<CvrIF> {
    val cvrbs = CvrBuilders().addContests(contestInfos)
    val result = mutableListOf<CvrIF>()
    styles.forEach { ballotStyle ->
        val scontests = contestInfos.filter { ballotStyle.contests.contains(it.name) }
        repeat(ballotStyle.ncards) {
            result.add(randomSample(cvrbs, scontests, skipSomeContests))
        }
    }
    return result
}

private fun randomSample(cvrbs: CvrBuilders, contests: List<ContestInfo>, skipSomeContests: Int): CvrIF {
    val cvrb = cvrbs.addCrv()
    contests.forEach { info ->
        cvrb.addContest(info.name, chooseCandName(info, skipSomeContests)).done()
    }
    return cvrb.build()
}

// random sampling with "thumb on the scale" to make sure winner wins.
// randomly choose candidate to vote for, with 5% extra for the reported winner.
// If skipSomeContests > 0, then some contests wont have a vote
private fun chooseCandName(info: ContestInfo, skipSomeContests: Int): Int? {
    val choiceIdx = Random.nextInt(info.candidateIds.size + skipSomeContests)
    if (choiceIdx >= info.candidateIds.size) return null
    return info.candidateIds[choiceIdx]
}