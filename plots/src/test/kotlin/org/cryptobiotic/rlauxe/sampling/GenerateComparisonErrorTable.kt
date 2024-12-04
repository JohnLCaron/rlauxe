package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.junit.jupiter.api.Test

class GenerateComparisonErrorTable {
    @Test
    fun generateErrorTable() {
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, quantile=.80, fuzzPct = null, ntrials = 1000)
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
                val contest = fcontest.makeContest(true) // TODO useStyle matters?
                // print("contest votes = ${contest.votes} ")

                val avgRatesForNcand = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
                fuzzPcts.forEach { fuzzPct ->
                    // println("margin= $margin ncand=$ncand fuzzPct=$fuzzPct")

                    repeat(auditConfig.ntrials) {
                        val cvrs = fcontest.makeCvrs()
                        val contestUA = ContestUnderAudit(contest, N)
                        contestUA.makeComparisonAssertions(cvrs)
                        val minAssert = contestUA.minComparisonAssertion()!!
                        val minAssort = minAssert.cassorter

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
}