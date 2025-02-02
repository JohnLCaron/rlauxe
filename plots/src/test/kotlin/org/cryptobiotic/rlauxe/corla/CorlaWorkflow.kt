package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*

class CorlaWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Nb: Int = Nc
): WorkflowTaskGenerator {
    override fun name() = "CorlaWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val auditConfig = auditConfigIn ?:
        AuditConfig(
            AuditType.CARD_COMPARISON, true, nsimEst = 10,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        if (!auditConfig.hasStyles && Nb > Nc) {
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeOtherCvrForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clca = CorlaWorkflow(auditConfig, listOf(sim.contest), testCvrs, quiet = true)
        return WorkflowTask(
            "genAuditWithErrorsPlots mvrsFuzzPct = $mvrsFuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

// cloned ClcaWorkflow
class CorlaWorkflow(
    val auditConfig: AuditConfig,
    val contestsToAudit: List<Contest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
    val quiet: Boolean = false,
): RlauxWorkflow {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>

    init {
        require (auditConfig.auditType == AuditType.CARD_COMPARISON)

        // 2. Pre-processing and consistency checks
        // 	a) Check that the winners according to the CVRs are the reported winners.
        //	b) If there are more CVRs that contain any contest than the upper bound on the number of cards that contain the contest, stop: something is seriously wrong.
        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }

        // 3. Prepare for sampling
        //	a) Generate a set of SHANGRLA [St20] assertions A_𝑐 for every contest 𝑐 under audit.
        //	b) Initialize A ← ∪ A_𝑐, c=1..C and C ← {1, . . . , 𝐶}. (Keep track of what assertions are proved)

        // val votes =  makeVotesPerContest(contests, cvrs)
        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    // debugging
    fun estimateSampleSizes(roundIdx: Int, show: Boolean): List<EstimationResult> {
        return estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show = show,
        )
    }

    /**
     * Choose lists of ballots to sample.
     * @parameter prevMvrs: use existing mvrs to estimate samples. may be empty.
     */
    override fun chooseSamples(roundIdx: Int, show: Boolean): List<Int> {
        if (!quiet) println("----------estimateSampleSizes round $roundIdx")

        val maxContestSize = estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show=show,
        )
        val contestsNotDone = contestsUA.filter{ !it.done }


        //	2.c) If the upper bound on the number of cards that contain any contest is greater than the number of CVRs that contain the contest, create a corresponding set
        //	    of “phantom” CVRs as described in section 3.4 of [St20]. The phantom CVRs are generated separately for each contest: each phantom card contains only one contest.
        //	2.d) If the upper bound 𝑁_𝑐 on the number of cards that contain contest 𝑐 is greater than the number of physical cards whose locations are known,
        //     create enough “phantom” cards to make up the difference. TODO c) vs d)  diffrence?
        //  3.c) Assign independent uniform pseudo-random numbers to CVRs that contain one or more contests under audit
        //      (including “phantom” CVRs), using a high-quality PRNG [OS19].
        // val ncvrs =  makeNcvrsPerContest(contests, cvrs)
        // val phantomCVRs = makePhantomCvrs(contests, ncvrs)

        //	4.c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        // draws random ballots and returns their locations to the auditors.
        if (contestsNotDone.size > 0) {
            return if (auditConfig.hasStyles) {
                if (!quiet) println("\nconsistentSampling round $roundIdx")
                val sampleIndices = consistentSampling(contestsNotDone, cvrsUA)
                if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            } else {
                if (!quiet) println("\nuniformSampling round $roundIdx")
                val sampleIndices = uniformSampling(contestsNotDone, cvrsUA, auditConfig.samplePctCutoff, cvrs.size, roundIdx)
                if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            }
        }
        return emptyList()
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        //4.d) Retrieve any of the corresponding ballot cards that have not yet been audited and inspect them manually to generate MVRs.
        // 	e) Import the MVRs.
        //	f) For each MVR 𝑖:
        //		For each 𝑐 ∈ C:
        //			If 𝑢_𝑖 ≤ 𝑡_𝑐 , then for each 𝑎 ∈ A 𝑐 ∩ A:
        //				• If the 𝑖th CVR is a phantom, define 𝑎(CVR𝑖 ) := 1/2.
        //				• If card 𝑖 cannot be found or if it is a phantom, define 𝑎(MVR𝑖 ) := 0.
        //				• Find the overstatement of assertion 𝑎 for CVR 𝑖, 𝑎(CVR𝑖 ) − 𝑎(MVR𝑖 ).
        //	g) Use the overstatement data from the previous step to update the measured risk for every assertion 𝑎 ∈ A.

        val contestsNotDone = contestsUA.filter{ !it.done }
        val sampledCvrs = sampleIndices.map { cvrs[it] }

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parallelize across assertions
        if (!quiet) println("runAudit round $roundIdx")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.clcaAssertions.forEach { cassertion ->
                if (!cassertion.status.complete) {
                    val testH0Result = runClcaAssertionAudit(auditConfig, contestUA, cassertion, cvrPairs, roundIdx, quiet=quiet)
                    cassertion.status = testH0Result.status
                    cassertion.round = roundIdx
                    allAssertionsDone = allAssertionsDone && cassertion.status.complete
                }
            }
            if (allAssertionsDone) {
                contestUA.done = true
                contestUA.status = TestH0Status.StatRejectNull // TODO ???
            }
            allDone = allDone && contestUA.done

        }
        return allDone
    }

    override fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minClcaAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else {
                if (minAssertion.roundResults.size == 1) {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} Np=${contest.Np} minMargin=${df(contest.minMargin())} ${minAssertion.roundResults[0]}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                } else {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} minMargin=${df(contest.minMargin())} est=${contest.estSampleSize} round=${minAssertion.round} status=${contest.status}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                    minAssertion.roundResults.forEach { rr -> println("   $rr") }
                }
            }
        }

        val minAssertion = getContests().first().minClcaAssertion()!!
        if (minAssertion.roundResults.isNotEmpty()) {
            val lastRound = minAssertion.roundResults.last()
            println("extra = ${lastRound.estSampleSize - lastRound.samplesNeeded}")
        }
        println()
    }

    override fun getContests(): List<ContestUnderAudit> = contestsUA
}

/////////////////////////////////////////////////////////////////////////////////

fun runClcaAssertionAudit(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ClcaAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
    quiet: Boolean = true,
): TestH0Result {
    val debug = false
    val cassorter = cassertion.cassorter
    val sampler = ComparisonWithoutReplacement(contestUA.contest, cvrPairs, cassorter, allowReset = false)

    val clcaConfig = auditConfig.clcaConfig
    val bettingFn = when (clcaConfig.strategy) {
        ClcaStrategyType.oracle -> {
            // use the actual errors comparing mvrs to cvrs. Testing only
            val errorRates = ClcaErrorRates.calcErrorRates(contestUA.id, cassorter, cvrPairs)
            OracleComparison(a = cassorter.noerror(), errorRates = errorRates)
        }

        ClcaStrategyType.noerror -> {
            // optimistic, no errors as apriori, then adapt to actual mvrs
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
            )
        }

        ClcaStrategyType.fuzzPct -> {
            /* val errorRates = if (auditConfig.version == 1.0 || cassertion.roundResults.isEmpty()) {
                // use given fuzzPct to generate apriori errors, then adapt to actual mvrs
                ClcaErrorRates.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct)
            } else {
                // use last rounds' errorRates as apriori. TODO: incremental audit
                cassertion.roundResults.last().errorRates
            } */
            val errorRates = ClcaErrorRates.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct)
            if (debug) println("simulateSampleSizeClcaAssorter errorRates = ${errorRates} for round ${cassertion.roundResults.size + 1}")

            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                errorRates
            )
        }

        ClcaStrategyType.apriori ->
            // use given errors as apriori, then adapt to actual mvrs.
            AdaptiveComparison(
                Nc = contestUA.Nc,
                withoutReplacement = true,
                a = cassorter.noerror(),
                d = clcaConfig.d,
                clcaConfig.errorRates!!
            )
    }

    // Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
    //    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn
    val testFn = Corla(
        N = contestUA.Nc,
        riskLimit = auditConfig.riskLimit,
        reportedMargin = cassertion.assorter.reportedMargin(),
        noerror = cassorter.noerror(),
        p1 = 0.0, p2 = 0.0, p3 = 0.0, p4 = 0.0, // todo
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit } + 1,
        samplesUsed = testH0Result.sampleCount,
        pvalue = testH0Result.pvalues.last(),
        status = testH0Result.status,
        errorRates = testH0Result.errorRates
        )
    cassertion.roundResults.add(roundResult)

    if (!quiet) println(" ${contestUA.name} $roundResult")
    return testH0Result
}