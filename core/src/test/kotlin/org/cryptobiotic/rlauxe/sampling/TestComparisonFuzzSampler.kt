package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test


class TestComparisonFuzzSampler {

    @Test
    fun generateErrorTable() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = 12356667890L, quantile=.80, fuzzPct = null, ntrials = 1000)
        val N = 100000

        val margins = listOf(.01) // listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val ncands = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)
        println("N=$N ntrials = ${auditConfig.ntrials}")
        println("| ncand | p1     | p2     | p3     | p4     |")
        println("|-------|--------|--------|--------|--------|")

        margins.forEach { margin ->
            ncands.forEach { ncand ->
                val fcontest = TestContest(0, ncand, margin)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                // print("contest votes = ${contest.votes} ")

                val avgRatesForNcand = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
                fuzzPcts.forEach { fuzzPct ->
                    // println("margin= $margin ncand=$ncand fuzzPct=$fuzzPct")

                    repeat(auditConfig.ntrials) {
                        val cvrs = fcontest.makeCvrs()
                        val contestUA = ContestUnderAudit(contest, N)
                        contestUA.makeComparisonAssertions(cvrs)
                        val minAssert = contestUA.minComparisonAssertion()!!
                        val minAssort = minAssert.assorter

                        val samples = PrevSamplesWithRates(minAssort.noerror)
                        val sampler = ComparisonFuzzSampler(fuzzPct, cvrs, contestUA, minAssort)
                        while (sampler.hasNext()) {
                            samples.addSample(sampler.next())
                        }
                        //samples.samplingErrors()
                        //    .forEachIndexed { idx, it -> avgRates[idx] = avgRates[idx] + it / ccount.toDouble() }
                        samples.samplingErrors()
                            .forEachIndexed { idx, it ->
                                avgRatesForNcand[idx] = avgRatesForNcand[idx] + it / (N * fuzzPct)
                            }
                    }
                    //println("  errors = ${samples.samplingErrors()}")
                    //println("  rates =  ${samples.samplingErrors(total.toDouble())}")
                    //println("  error% = ${samples.samplingErrors(total * fuzzPct)}")
                }
                print("| $ncand | ")
                for (p in 1 until 5) {
                    print(" ${df(avgRatesForNcand[p]/(auditConfig.ntrials * fuzzPcts.size))} |")
                }
                println()
            }
        }
    }

    // contest votes = {1=5050, 0=4950} margin= 0.01 ncand=2 fuzzPct=0.001
    //  errors = [998954, 230, 267, 280, 269]
    //  rates =  0.0002,0.0003,0.0003,0.0003]
    //  error% = 0.2300,0.2670,0.2800,0.2690]
    //margin= 0.01 ncand=2 fuzzPct=0.005
    //  errors = [994968, 1295, 1262, 1265, 1210]
    //  rates =  0.0013,0.0013,0.0013,0.0012]
    //  error% = 0.2590,0.2524,0.2530,0.2420]
    //margin= 0.01 ncand=2 fuzzPct=0.01
    //  errors = [990077, 2512, 2475, 2398, 2538]
    //  rates =  0.0025,0.0025,0.0024,0.0025]
    //  error% = 0.2512,0.2475,0.2398,0.2538]
    //margin= 0.01 ncand=2 fuzzPct=0.02
    //  errors = [979901, 5086, 5121, 4903, 4989]
    //  rates =  0.0051,0.0051,0.0049,0.0050]
    //  error% = 0.2543,0.2561,0.2452,0.2495]
    //margin= 0.01 ncand=2 fuzzPct=0.05
    //  errors = [950131, 12608, 12537, 12406, 12318]
    //  rates =  0.0126,0.0125,0.0124,0.0123]
    //  error% = 0.2522,0.2507,0.2481,0.2464]
    //error% for ncand 2 =  [272.990594, 0.24933199999999997, 0.254738, 0.253214, 0.252122]
    // 1/4 for all = 1/(2+ncand)
    //
    //contest votes = {2=4678, 0=4578, 1=744} margin= 0.01 ncand=3 fuzzPct=0.001
    //  errors = [998994, 349, 175, 334, 148]
    //  rates =  0.0003,0.0002,0.0003,0.0001]
    //  error% = 0.3490,0.1750,0.3340,0.1480]
    //margin= 0.01 ncand=3 fuzzPct=0.005
    //  errors = [995127, 1665, 753, 1708, 747]
    //  rates =  0.0017,0.0008,0.0017,0.0007]
    //  error% = 0.3330,0.1506,0.3416,0.1494]
    //margin= 0.01 ncand=3 fuzzPct=0.01
    //  errors = [990280, 3299, 1595, 3307, 1519]
    //  rates =  0.0033,0.0016,0.0033,0.0015]
    //  error% = 0.3299,0.1595,0.3307,0.1519]
    //margin= 0.01 ncand=3 fuzzPct=0.02
    //  errors = [980787, 6657, 3018, 6489, 3049]
    //  rates =  0.0067,0.0030,0.0065,0.0030]
    //  error% = 0.3329,0.1509,0.3245,0.1525]
    //margin= 0.01 ncand=3 fuzzPct=0.05
    //  errors = [951544, 16686, 7699, 16420, 7651]
    //  rates =  0.0167,0.0077,0.0164,0.0077]
    //  error% = 0.3337,0.1540,0.3284,0.1530]
    //error% for ncand 3 =  [273.02352600000006, 0.335694, 0.157996, 0.33183, 0.15095399999999998]
    // 1/3, 1/6, 1/3, 1/6 = 1/ncand, 1/(2*ncand), 1/ncand, 1/(2*ncand)
    //
    //contest votes = {0=4175, 1=4074, 3=1114, 2=637} margin= 0.01 ncand=4 fuzzPct=0.001
    //  errors = [999073, 367, 116, 355, 89]
    //  rates =  0.0004,0.0001,0.0004,0.0001]
    //  error% = 0.3670,0.1160,0.3550,0.0890]
    //margin= 0.01 ncand=4 fuzzPct=0.005
    //  errors = [995337, 1823, 505, 1837, 498]
    //  rates =  0.0018,0.0005,0.0018,0.0005]
    //  error% = 0.3646,0.1010,0.3674,0.0996]
    //margin= 0.01 ncand=4 fuzzPct=0.01
    //  errors = [990828, 3582, 1042, 3492, 1056]
    //  rates =  0.0036,0.0010,0.0035,0.0011]
    //  error% = 0.3582,0.1042,0.3492,0.1056]
    //margin= 0.01 ncand=4 fuzzPct=0.02
    //  errors = [981720, 7155, 2106, 6977, 2042]
    //  rates =  0.0072,0.0021,0.0070,0.0020]
    //  error% = 0.3578,0.1053,0.3489,0.1021]
    //margin= 0.01 ncand=4 fuzzPct=0.05
    //  errors = [954239, 18041, 5248, 17360, 5112]
    //  rates =  0.0180,0.0052,0.0174,0.0051]
    //  error% = 0.3608,0.1050,0.3472,0.1022]
    //error% for ncand 4 =  [273.07879599999995, 0.361674, 0.10629200000000001, 0.35352999999999996, 0.09970799999999999]
    // 4/11, 1/10, 4/11, 1/10
    //
    //contest votes = {3=2525, 1=2424, 2=2044, 4=2039, 0=968} margin= 0.01 ncand=5 fuzzPct=0.001
    //  errors = [999261, 328, 53, 315, 43]
    //  rates =  0.0003,0.0001,0.0003,0.0000]
    //  error% = 0.3280,0.0530,0.3150,0.0430]
    //margin= 0.01 ncand=5 fuzzPct=0.005
    //  errors = [996525, 1497, 242, 1473, 263]
    //  rates =  0.0015,0.0002,0.0015,0.0003]
    //  error% = 0.2994,0.0484,0.2946,0.0526]
    //margin= 0.01 ncand=5 fuzzPct=0.01
    //  errors = [992963, 3113, 498, 2918, 508]
    //  rates =  0.0031,0.0005,0.0029,0.0005]
    //  error% = 0.3113,0.0498,0.2918,0.0508]
    //margin= 0.01 ncand=5 fuzzPct=0.02
    //  errors = [986010, 6068, 991, 5929, 1002]
    //  rates =  0.0061,0.0010,0.0059,0.0010]
    //  error% = 0.3034,0.0496,0.2965,0.0501]
    //margin= 0.01 ncand=5 fuzzPct=0.05
    //  errors = [964920, 15268, 2570, 14830, 2412]
    //  rates =  0.0153,0.0026,0.0148,0.0024]
    //  error% = 0.3054,0.0514,0.2966,0.0482]
    //error% for ncand 5 =  [273.29224, 0.309492, 0.050429999999999996, 0.29889, 0.048948]
    // 3/10, 1/20, 3/10, 1/20
    //
    //contest votes = {0=2801, 1=2701, 5=1553, 3=1401, 2=1002, 4=542} margin= 0.01 ncand=6 fuzzPct=0.001
    //  errors = [999250, 329, 42, 334, 45]
    //  rates =  0.0003,0.0000,0.0003,0.0000]
    //  error% = 0.3290,0.0420,0.3340,0.0450]
    //margin= 0.01 ncand=6 fuzzPct=0.005
    //  errors = [996518, 1550, 245, 1474, 213]
    //  rates =  0.0016,0.0002,0.0015,0.0002]
    //  error% = 0.3100,0.0490,0.2948,0.0426]
    //margin= 0.01 ncand=6 fuzzPct=0.01
    //  errors = [992990, 3087, 468, 2984, 471]
    //  rates =  0.0031,0.0005,0.0030,0.0005]
    //  error% = 0.3087,0.0468,0.2984,0.0471]
    //margin= 0.01 ncand=6 fuzzPct=0.02
    //  errors = [986050, 6237, 888, 5912, 913]
    //  rates =  0.0062,0.0009,0.0059,0.0009]
    //  error% = 0.3119,0.0444,0.2956,0.0457]
    //margin= 0.01 ncand=6 fuzzPct=0.05
    //  errors = [965138, 15356, 2265, 14990, 2251]
    //  rates =  0.0154,0.0023,0.0150,0.0023]
    //  error% = 0.3071,0.0453,0.2998,0.0450]
    //error% for ncand 6 =  [273.291572, 0.313334, 0.0455, 0.30452, 0.045073999999999996]
    // 7/22, 1/22, 3/10, 1/22

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 1
        val test = MultiContestTestData(ncontests, 1, 50000)
        val contests: List<Contest> = test.makeContests()
        print("contest = ${contests.first()}")
        val cvrs = test.makeCvrsFromContests()
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(contests, cvrs, fuzzPct)
            println(" fuzzPct = $fuzzPct")
            val avgRates = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            contests.forEach { contest ->
                val contestUA = ContestUnderAudit(contest.info, cvrs).makeComparisonAssertions(cvrs)
                val minAssert = contestUA.minComparisonAssertion()
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.assorter
                    val samples = PrevSamplesWithRates(minAssort.noerror)
                    var ccount = 0
                    var count = 0
                    fcvrs.forEachIndexed { idx, fcvr ->
                        if (fcvr.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != fcvr) count++
                        }
                    }
                    val fuzz = count.toDouble() / ccount
                    println("$it ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
                    if (detail) {
                        println("  errors = ${samples.samplingErrors()}")
                        println("  rates =  ${samples.samplingErrors(ccount.toDouble())}")
                        println("  error% = ${samples.samplingErrors(ccount * fuzz)}")
                    }
                    samples.samplingErrors()
                        .forEachIndexed { idx, it -> avgRates[idx] = avgRates[idx] + it / ccount.toDouble() }
                }
            }
            val total = ntrials * ncontests
            println("  avgRates = ${avgRates.map { it / total }}")
            println("  error% = ${avgRates.map { it / (total * fuzzPct) }}")
        }
    }

    @Test
    fun testComparisonFuzzed() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit(it, it.Nc) }
        val cvrs = test.makeCvrsFromContests()
        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrs)
        }
        println("total ncvrs = ${cvrs.size}\n")

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = secureRandom.nextLong(), quantile=.50, fuzzPct = .01)

        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Pair<Int, Double>>()
            contestUA.comparisonAssertions.map { assertion ->
                val result: RunTestRepeatedResult = runWithComparisonFuzzSampler(auditConfig, contestUA, assertion, cvrs)
                val size = result.findQuantile(auditConfig.quantile)
                assertion.estSampleSize = size
                sampleSizes.add(Pair(size, assertion.margin))
                println(" ${assertion.assorter.assorter.desc()} margin=${df(assertion.assorter.margin)} estSize=${size}}")

            }
            val maxSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.map { it.first }.max() ?: 0
            val pair = if (sampleSizes.isEmpty()) Pair(0, 0.0) else sampleSizes.find{ it.first == maxSize }!!
            contestUA.estSampleSize = pair.first
            println("${contestUA.name} estSize=${contestUA.estSampleSize} margin=${df(pair.second)}")
        }
    }
}

private fun runWithComparisonFuzzSampler(
        auditConfig: AuditConfig,
        contestUA: ContestUnderAudit,
        assertion: ComparisonAssertion,
        cvrs: List<Cvr>, // (mvr, cvr)
        moreParameters: Map<String, Double> = emptyMap(),
    ): RunTestRepeatedResult {

    val assorter = assertion.assorter
    val sampler = ComparisonFuzzSampler(auditConfig.fuzzPct!!, cvrs, contestUA, assorter)

    return simulateSampleSizeBetaMart(
        auditConfig,
        sampler,
        assorter.margin,
        assorter.noerror,
        assorter.upperBound(),
        contestUA.ncvrs,
        contestUA.Nc,
        ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct!!),
        moreParameters
    )
}