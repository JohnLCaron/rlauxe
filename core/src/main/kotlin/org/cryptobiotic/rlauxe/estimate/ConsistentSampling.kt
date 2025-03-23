package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt

private val debug = false
private val debugConsistent = false
private val debugUniform = false
private val debugSizeNudge = true

/**
 * 2. _Choosing sample sizes_: the Auditor decides which contests and how many samples will be audited.
 * 3. _Random sampling_: The actual ballots to be sampled are selected randomly based on a carefully chosen random seed.
 * Iterates on createSampleIndices, checking for auditRound.sampleNumbers.size <= auditConfig.sampleLimit, removing contests until satisfied.
 * Also called from rlauxe_viewer
 */
fun sampleCheckLimits(workflow: RlauxAuditProxy, auditRound: AuditRound, previousSamples: Set<Long>, quiet: Boolean) {
    val auditConfig = workflow.auditConfig()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        sample(workflow, auditRound, previousSamples, quiet = quiet)

        //// the rest of this implements sampleLimit
        if (auditConfig.sampleLimit < 0 || auditRound.sampleNumbers.size <= auditConfig.sampleLimit) {
            break
        }
        // find the contest with the largest estimation size eligible for removal, remove it
        val maxEstimation = contestsNotDone.maxOf { it.estSampleSizeEligibleForRemoval() }
        val maxContest = contestsNotDone.first { it.estSampleSizeEligibleForRemoval() == maxEstimation }
        println(" ***too many samples, remove contest ${maxContest.id} with status FailMaxSamplesAllowed")

        // information we want in the persisted record
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed

        contestsNotDone.remove(maxContest)
    }
}

/** Choose what ballots to sample */
fun sample(
    workflow: RlauxAuditProxy,
    auditRound: AuditRound,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
    val auditConfig = workflow.auditConfig()
    if (auditConfig.hasStyles) {
        if (!quiet) println("consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}")
        consistentSampling(auditRound, workflow.mvrManager(), previousSamples)
        if (!quiet) println(" consistentSamplingSize= ${auditRound.sampleNumbers.size}")
    } else {
        if (!quiet) println("\nuniformSampling round ${auditRound.roundIdx}")
        uniformSampling(auditRound, workflow.mvrManager(), previousSamples.size, auditConfig.sampleLimit, auditRound.roundIdx)
        if (!quiet) println(" uniformSamplingSize= ${auditRound.sampleNumbers.size}")
    }
}

// for audits with hasStyles = true
fun consistentSampling(
    auditRound: AuditRound,
    mvrManager: MvrManager, // just need mvrManager.ballotCards().iterator()
    previousSamples: Set<Long> = emptySet(),
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // calculate how many samples are wanted for each contest
    val wantSampleSize = wantSampleSize(contestsNotDone, previousSamples, mvrManager.ballotCards())
    require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        if (c.auditorWantNewMvrs > 0 && (haveNewSamples[c.id] ?: 0) >= c.auditorWantNewMvrs) return false
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }
    val haveActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    var newMvrs = 0
    val sampledCards = mutableListOf<BallotOrCvr>()

    // while we need more samples
    val sortedBorcIter = mvrManager.ballotCards().iterator()
    while (
        ((auditRound.auditorWantNewMvrs < 0) || (newMvrs < auditRound.auditorWantNewMvrs)) &&
        contestsIncluded.any { contestWantsMoreSamples(it) } &&
        sortedBorcIter.hasNext()) {

        // get the next sorted cvr
        val boc = sortedBorcIter.next()
        val sampleNumber = boc.sampleNumber()
        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && boc.hasContest(contestRound.id) }) {
            // then use it
            sampledCards.add(boc)
            if (!previousSamples.contains(sampleNumber)) {
                newMvrs++
            }
            // count only if included
            contestsIncluded.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                }
            }
            // track actual for all contests not done
            contestsNotDone.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveActualMvrs[contest.id] = haveActualMvrs[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(sampleNumber))
                        haveNewSamples[contest.id] = haveNewSamples[contest.id]?.plus(1) ?: 1
                }
            }
        }
        // inx++
    }

    if (debugConsistent) println("**consistentSampling haveActualMvrs = $haveActualMvrs, haveNewSamples = $haveNewSamples, newMvrs=$newMvrs")
    val contestIdMap = contestsNotDone.associate { it.id to it }
    contestIdMap.values.forEach { // defaults to 0
        it.actualMvrs = 0
        it.actualNewMvrs = 0
    }
    haveActualMvrs.forEach { (contestId, nmvrs) ->
        contestIdMap[contestId]?.actualMvrs = nmvrs
    }
    haveNewSamples.forEach { (contestId, nnmvrs) ->
        contestIdMap[contestId]?.actualNewMvrs = nnmvrs
    }
    auditRound.nmvrs = sampledCards.size
    auditRound.newmvrs = newMvrs
    auditRound.sampleNumbers = sampledCards.map { it.sampleNumber() }
    auditRound.sampledBorc = sampledCards
}

// for audits with hasStyles = false
fun uniformSampling(
    auditRound: AuditRound,
    mvrManager: MvrManager,
    prevSampleSize: Int,
    sampleLimit: Int,
    roundIdx: Int,
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // scale by proportion of ballots that have this contest
    val Nb = mvrManager.nballotCards()
    contestsNotDone.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val estWithFactor = roundToInt((contestUA.estSampleSize * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        // val estPct = estWithFactor / Nb.toDouble()
        if (sampleLimit > 0 && estWithFactor > sampleLimit) { // might as well test it here, since it will happen a lot
            if (debugUniform) println("uniformSampling samplePctCutoff: $contestUA estWithFactor $estWithFactor > $sampleLimit round $roundIdx")
            contestUA.done = true // TODO dont do this here?
            contestUA.status = TestH0Status.FailMaxSamplesAllowed
        }
    }
    val estTotalSampleSizes = contestsNotDone.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return
    var nmvrs = estTotalSampleSizes.max()

    if (auditRound.roundIdx > 2) {
        val prevNudged = (1.25 * prevSampleSize).toInt()
        if (prevNudged > nmvrs) {
            if (debugSizeNudge) println(" ** uniformSampling prevNudged $prevNudged > $nmvrs; round=${auditRound.roundIdx}")
            nmvrs = prevNudged
        }
    }

    // take the first nmvrs of the sorted ballots
    val sampledCards = mvrManager.takeFirst(nmvrs)

    auditRound.sampleNumbers = sampledCards.map { it.sampleNumber() } // list of ballot indexes sorted by sampleNum
    auditRound.sampledBorc = sampledCards
}


/////////////////////////////////////////////////////////////////////////////////
// SHANGRLA computeSampleSize not needed, I think

//4.a) Pick the (cumulative) sample sizes {𝑆_𝑐} for 𝑐 ∈ C to attain by the end of this round of sampling.
//	    The software offers several options for picking {𝑆_𝑐}, including some based on simulation.
//      The desired sampling fraction 𝑓_𝑐 := 𝑆_𝑐 /𝑁_𝑐 for contest 𝑐 is the sampling probability
//	      for each card that contains contest 𝑘, treating cards already in the sample as having sampling probability 1.
//	    The probability 𝑝_𝑖 that previously unsampled card 𝑖 is sampled in the next round is the largest of those probabilities:
//	      𝑝_𝑖 := max (𝑓_𝑐), 𝑐 ∈ C ∩ C𝑖, where C_𝑖 denotes the contests on card 𝑖.
//	b) Estimate the total sample size to be Sum(𝑝_𝑖), where the sum is across all cards 𝑖 except phantom cards.

// given the contest.sampleSize, we can calculate the total number of ballots.
// however, we get this from consistent sampling, which actually picks which ballots to sample.
/* dont really need
fun computeSampleSize(
    rcontests: List<ContestUnderAudit>,
    cvrs: List<CvrUnderAudit>,
): Int {
    // unless style information is being used, the sample size is the same for every contest.
    val old_sizes: MutableMap<Int, Int> =
        rcontests.associate { it.id to 0 }.toMutableMap()

    // setting p toodoo whats this doing here? shouldnt it be in consistent sampling ?? MoreStyle section 3 ??
    for (cvr in cvrs) {
        if (cvr.sampled) {
            cvr.p = 1.0
        } else {
            cvr.p = 0.0
            for (con in rcontests) {
                if (cvr.hasContest(con.id) && !cvr.sampled) {
                    val p1 = con.estSampleSize.toDouble() / (con.Nc!! - old_sizes[con.id]!!)
                    cvr.p = max(p1, cvr.p) // toodoo nullability
                }
            }
        }
    }

    // when old_sizes == 0, total_size should be con.sample_size (61); python has roundoff to get 62
    // total_size = ceil(np.sum([x.p for x in cvrs if !x.phantom))
    // toodoo total size is the sum of the p's over the cvrs (!wtf)
    val summ: Double = cvrs.filter { !it.phantom }.map { it.p }.sum()
    val total_size = ceil(summ).toInt()
    return total_size // toodoo what is this? doesnt consistent sampling decide this ??
}

// STYLISH 4 a,b. I think maybe only works when you use sampleThreshold ??
fun computeSampleSizePolling(
    rcontests: List<ContestUnderAudit>,
    ballots: List<BallotUnderAudit>,
): Int {
    ballots.forEach { ballot ->
        if (ballot.sampled) {
            ballot.p = 1.0
        } else {
            ballot.p = 0.0
            for (con in rcontests) {
                if (ballot.hasContest(con.id) && !ballot.sampled) {
                    val p1 = con.estSampleSize.toDouble() / con.Nc
                    ballot.p = max(p1, ballot.p)
                }
            }
        }
    }
    val summ: Double = ballots.filter { !it.phantom }.map { it.p }.sum()
    return ceil(summ).toInt()
}
 */


