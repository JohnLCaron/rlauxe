package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.workflow.*

private val debug = false
private val debugConsistent = false
private val debugUniform = true
private val debugSizeNudge = true

/**
 * iterates on createSampleIndices, checking for pct <= auditConfig.samplePctCutoff,
 * removing contests until satisfied. */
fun sample(workflow: RlauxWorkflowProxy, auditRound: AuditRound, previousSamples: Set<Long>, quiet: Boolean) {
    val auditConfig = workflow.auditConfig()
    // val sortedBorc = workflow.sortedBallotsOrCvrs()

    // count the number of cvrs that have at least one contest under audit.
    // TODO this is wrong for samplePctCutoff, except maybe the first round ??
    //val NN = if (!auditConfig.hasStyles) sortedBorc.size
    //              else sortedBorc.filter { it.hasOneOrMoreContest(auditRound.contestRounds) }.count()

    var sampleIndices: List<BallotOrCvr> = emptyList()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

    val Nb = workflow.ballotCards().nballotCards()
    while (contestsNotDone.isNotEmpty()) {
        createSampleIndices(workflow, auditRound, previousSamples, quiet = quiet)

        // the rest of this implements samplePctCutoff TODO refactor this
        val pct = sampleIndices.size / Nb.toDouble()
        if (debug) println(" createSampleIndices size = ${sampleIndices.size} Nb=$Nb pct= $pct max=${auditConfig.samplePctCutoff}")
        if (pct <= auditConfig.samplePctCutoff) {
            break
        }
         // find the contest with the largest estimation size, remove it
        val maxEstimation = contestsNotDone.maxOf { it.estSampleSize }
        val maxContest = contestsNotDone.first { it.estSampleSize == maxEstimation }
        println(" ***too many samples, remove contest ${maxContest} with status FailMaxSamplesAllowed")

        // information we want in the persisted record
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed

        contestsNotDone.remove(maxContest)
        sampleIndices = emptyList() // if theres no more contests, this says were done
    }
}

/** must have contest.estSampleSize set. must have borc.sampleNumber assigned. */
fun createSampleIndices(
    workflow: RlauxWorkflowProxy,
    auditRound: AuditRound,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
    val auditConfig = workflow.auditConfig()
    if (auditConfig.hasStyles) {
        if (!quiet) println("consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}")
        consistentSampling(auditRound, workflow.ballotCards(), previousSamples)
        if (!quiet) println(" consistentSamplingSize= ${auditRound.sampleNumbers.size}")
    } else {
        if (!quiet) println("\nuniformSampling round ${auditRound.roundIdx}")
        uniformSampling(auditRound, workflow.ballotCards(), previousSamples.size, auditConfig.samplePctCutoff, auditRound.roundIdx)
        if (!quiet) println(" consistentSamplingSize= ${auditRound.sampleNumbers.size}")
    }
}

// for audits with hasStyles
fun consistentSampling(
    auditRound: AuditRound,
    ballotCards: BallotCards,
    previousSamples: Set<Long> = emptySet(),
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // calculate how many samples are wanted for each contest
    val wantSampleSize = wantSampleSize(contestsNotDone, previousSamples, ballotCards.ballotCards())
    require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        if (c.auditorWantNewMvrs > 0 && (haveNewSamples[c.id] ?: 0) >= c.auditorWantNewMvrs) return false
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }
    val haveActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    // get list of cvr indexes sorted by sampleNum TODO these should already be sorted
    // val sortedBocIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    var newMvrs = 0
    val sampledCards = mutableListOf<BallotOrCvr>()

    // while we need more samples
    // var inx = 0
    val sortedBorcIter = ballotCards.ballotCards().iterator()
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

// for audits with !hasStyles
fun uniformSampling(
    auditRound: AuditRound,
    ballotCards: BallotCards,
    prevSampleSize: Int,
    samplePctCutoff: Double,  // TODO
    roundIdx: Int,
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // scale by proportion of ballots that have this contest
    val Nb = ballotCards.nballotCards()
    contestsNotDone.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val estWithFactor = roundToInt((contestUA.estSampleSize * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        val estPct = estWithFactor / Nb.toDouble()
        if (estPct > samplePctCutoff) {
            if (debugUniform) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
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
    val sampledCards = ballotCards.takeFirst(nmvrs)

    auditRound.sampleNumbers = sampledCards.map { it.sampleNumber() } // list of ballot indexes sorted by sampleNum
    auditRound.sampledBorc = sampledCards
}


