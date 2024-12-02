package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.df

//// Adapted from SHANGRLA Audit.py

// SHANGRLA.make_phantoms(). Probably 2.d ?
fun makePhantomCvrs(
    contestas: List<ContestUnderAudit>,
    prefix: String = "phantom-",
    prng: Prng,
): List<CvrUnderAudit> {
    // code assertRLA.ipynb
    // + Prepare ~2EZ:
    //    - `N_phantoms = max_cards - cards_in_manifest`
    //    - If `N_phantoms < 0`, complain
    //    - Else create `N_phantoms` phantom cards
    //    - For each contest `c`:
    //        + `N_c` is the input upper bound on the number of cards that contain `c`
    //        + if `N_c is None`, `N_c = max_cards - non_c_cvrs`, where `non_c_cvrs` is #CVRs that don't contain `c`
    //        + `C_c` is the number of CVRs that contain the contest
    //        + if `C_c > N_c`, complain
    //        + else if `N_c - C_c > N_phantoms`, complain
    //        + else:
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom CVRs
    //            - Consider contest `c` to be on the first `N_c - C_c` phantom ballots

    // 3.4 SHANGRLA
    // If N_c > ncvrs, create N − n “phantom ballots” and N − n “phantom CVRs.”

    // create phantom CVRs as needed for each contest
    val phantombs = mutableListOf<PhantomBuilder>()

    for (contest in contestas) {
        val phantoms_needed = contest.Nc - contest.ncvrs
        while (phantombs.size < phantoms_needed) { // make sure you have enough phantom CVRs
            phantombs.add(PhantomBuilder(id = "${prefix}${phantombs.size + 1}"))
        }
        // include this contest on the first n phantom CVRs
        repeat(phantoms_needed) {
            phantombs[it].contests.add(contest.id)
        }
    }
    return phantombs.map { it.build(prng) }
}

class PhantomBuilder(val id: String) {
    val contests = mutableListOf<Int>()
    fun build(prng: Prng): CvrUnderAudit {
        val votes = contests.associateWith { IntArray(0) }
        return CvrUnderAudit(Cvr(id, votes, phantom = true), prng.next())
    }
}
///////////////////////////////////////////////////////////////////////

// its possible that an already sampled cvr is not needed this round
// but, we already have the mvr so theres no cost to including it.
// otoh, why bother? maybe just complicates things.
// note that SHANGRLA assertion_RLA.ipynb doesnt pass in the previously sampled indices.
// Nor anywhere else in SHANGRLA esp. *.ipynb
// so then, do we even need cvr.sampled ?? used in find_sample_size()

// StylishWorkflow.chooseSamples()
// AssertionRLAipynb.workflow()
// first time only, we'll add the subsequent rounds later. KISS
// sampling without replacement only

fun consistentCvrSampling(
    contests: List<ContestUnderAudit>, // must have sampleSizes set
    cvrList: List<CvrUnderAudit>, // must have sampleNums assigned
): List<Int> {
    if (cvrList.isEmpty()) return emptyList()

    val currentSizes = mutableMapOf<Int, Int>()
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedCvrIndices = cvrList.indices.sortedBy { cvrList[it].sampleNum }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) } && inx < sortedCvrIndices.size) {
        // get the next sorted cvr
        val sidx = sortedCvrIndices[inx]
        val cvr = cvrList[sidx]
        // does this cvr contribute to one or more contests that need more samples?
        if (contests.any { contestInProgress(it) && cvr.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            cvr.sampled = true
            contests.forEach { contest ->
                if (cvr.hasContest(contest.id)) {
                    currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }
    if (inx == sortedCvrIndices.size) {
        println("ran out of samples!!")
    }
    contests.forEach { contest ->
        if (currentSizes[contest.id] == null)
            println("hey")
        contest.availableInSample = currentSizes[contest.id]!!
        if (show) println(" ${contest} minMargin=${df(contest.minComparisonAssertion()?.margin ?: 0.0)} " +
                "availableInSample=${contest.availableInSample}")
    }
    return sampledIndices
}

fun consistentPollingSampling(
    contests: List<ContestUnderAudit>, // all the contests you want to sample
    ballots: List<BallotUnderAudit>, // all the ballots available to sample
    ballotManifest: BallotManifest,
): List<Int> {
    if (ballots.isEmpty()) return emptyList()

    val currentSizes = mutableMapOf<Int, Int>()
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedCvrIndices = ballots.indices.sortedBy { ballots[it].sampleNum }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) }  && (inx < sortedCvrIndices.size)) {
        // get the next sorted cvr
        val sidx = sortedCvrIndices[inx]
        val ballot = ballots[sidx]
        val ballotStyle = ballotManifest.getBallotStyleFor(ballot.ballot.ballotStyleId!!)
        // does this cvr contribute to one or more contests that need more samples?
        if (contests.any { contestInProgress(it) && ballotStyle.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            ballot.sampled = true
            ballotStyle.contestIds.forEach {
                currentSizes[it] = currentSizes[it]?.plus(1) ?: 1
            }
        }
        inx++
    }
    if (inx == sortedCvrIndices.size) {
        println("ran out of samples!!")
    }
    contests.forEach { contest ->
        if (currentSizes[contest.id] == null)
            println("hey")
        contest.availableInSample = currentSizes[contest.id]!!
        if (show) println(" ${contest} minMargin=${df(contest.minPollingAssertion()?.margin ?: 0.0)} " +
                "availableInSample=${contest.availableInSample}")
    }
    return sampledIndices
}

private val show = true


