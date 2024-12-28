package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.rlaplots.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import io.kotest.core.config.AbstractProjectConfig

class PlotSampleSizeEstimates : AbstractProjectConfig() {
    override val parallelism = 3

    @Test
    fun plotComparisonVsPoll() {
        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = 12356667890L,
            quantile = .80,
            fuzzPct = .000,
            ntrials = 100
        )
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val sim = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, N) // TODO
            print("margin = $margin ${sim.contest.votes}")
            val contestUAp = ContestUnderAudit(sim.contest, isComparison = false)

            // polling
            contestUAp.makePollingAssertions()
            val assort = contestUAp.minAssertion()!!.assorter
            tasks.add(PollingTask("Polling: margin = $margin", auditConfig, contestUAp, assort, N))

            // comparison
            val cvrs = sim.makeCvrs()
            val contestUAc = ContestUnderAudit(sim.contest, isComparison = true)
            contestUAc.makeComparisonAssertions(cvrs)
            val cassort = contestUAc.minComparisonAssertion()!!.cassorter
            tasks.add(ComparisonTask("Comparison: margin = $margin", auditConfig, contestUAc, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonVsPoll"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples(catfld = { if (it.isPolling) "polling" else "comparison" })
    }

    @Test
    fun plotComparisonVsStyleAndPoll() {
        val N = 10000
        val ntrials = 1000
        val tasks = mutableListOf<ConcurrentTask>()
        val margins = listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            // polling
            val auditConfigPolling = AuditConfig(AuditType.POLLING, hasStyles = true, seed = 12356667890L, quantile = .80, fuzzPct = .055, ntrials = ntrials)
            val simp = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
            print("margin = $margin ${simp.contest.votes}")
            val contestUAp = ContestUnderAudit(simp.contest, isComparison = false, hasStyle = true)
            contestUAp.makePollingAssertions()
            val assortP = contestUAp.minAssertion()!!.assorter
            tasks.add(PollingTask("Polling: margin = $margin", auditConfigPolling, contestUAp, assortP, N, moreParameters = mapOf("polling" to 1.0)))

            // with styles
            val simc = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
            val auditConfigStyles = AuditConfig(AuditType.CARD_COMPARISON, hasStyles = true, seed = 1235666890L, quantile = .80, fuzzPct = .05, ntrials = ntrials)
            val cvrs = simc.makeCvrs()
            print("margin = $margin ${simc.contest.votes}")
            val contestUAs = ContestUnderAudit(simc.contest, isComparison = true, hasStyle = true)
            contestUAs.makeComparisonAssertions(cvrs)
            val cassort = contestUAs.minComparisonAssertion()!!.cassorter
            tasks.add(ComparisonTask("Comparison with styles: margin = $margin", auditConfigStyles, contestUAs, cassort, cvrs, moreParameters = mapOf("hasStyles" to 1.0)))

            // no styles
            val auditConfigNo = AuditConfig(AuditType.CARD_COMPARISON, hasStyles = false, seed = 123569667890L, quantile = .80, fuzzPct = .05, ntrials = ntrials)
            val simNo = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
            val cvrsNo = simNo.makeCvrs()
            print("margin = $margin ${simNo.contest.votes}")
            val contestUAno = ContestUnderAudit(simNo.contest, isComparison = true, hasStyle = false)
            contestUAno.makeComparisonAssertions(cvrsNo)
            val cassortNo = contestUAno.minComparisonAssertion()!!.cassorter
            tasks.add(ComparisonTask("Comparison with styles: margin = $margin", auditConfigNo, contestUAno, cassortNo, cvrsNo))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonVsStyleAndPoll"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples(catfld = {
            if (it.hasStyles) "compare hasCSD" else if (it.isPolling) "polling hasCSD" else "compare noCSD" })
    }

    @Test
    fun plotVsFuzz() {
        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = 12356667890L,
            quantile = .80,
            fuzzPct = .01,
            ntrials = 100
        )
        val N = 10000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val simp = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
            print("margin = $margin ${simp.contest.votes}")
            val contestUA = ContestUnderAudit(simp.contest, isComparison = false)

            // polling
            contestUA.makePollingAssertions()
            val assort = contestUA.minAssertion()!!.assorter
            tasks.add(PollingTask("Polling (standard): margin = $margin", auditConfig, contestUA, assort, N))

            // alternative
            tasks.add(PollingTask("Polling fuzz=.01: margin = $margin", auditConfig, contestUA, assort, N))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirname = "/home/stormy/temp/estimate"
        val filename = "PollStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples(catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"
        }
        )
    }

    @Test
    fun compareVsFuzz() {
        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = 12356667890L,
            quantile = .80,
            fuzzPct = null,
            ntrials = 100
        )
        val N = 100000
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<ConcurrentTask>()
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        margins.forEach { margin ->
            val sim = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
            print("margin = $margin ${sim.contest.votes}")
            val contestUA = ContestUnderAudit(sim.contest)
            val cvrs = sim.makeCvrs()

            contestUA.makeComparisonAssertions(cvrs)
            val cassort = contestUA.minComparisonAssertion()!!.cassorter
            tasks.add(ComparisonTask("Comparison (standard): margin = $margin", auditConfig, contestUA, cassort, cvrs))

            // alternative
            val configAlt1 = AuditConfig(
                AuditType.POLLING,
                hasStyles = true,
                seed = 12356667890L,
                quantile = .80,
                fuzzPct = .01,
                ntrials = 100
            )
            tasks.add(ComparisonTask("Comparison fuzz=.01: margin = $margin", configAlt1, contestUA, cassort, cvrs))

            val configAlt2 = AuditConfig(
                AuditType.POLLING,
                hasStyles = true,
                seed = 12356667890L,
                quantile = .80,
                fuzzPct = .001,
                ntrials = 100
            )
            tasks.add(ComparisonTask("Comparison fuzz=.001: margin = $margin", configAlt2, contestUA, cassort, cvrs))
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"
        val writer = SRTcsvWriter("$dirname/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples(catfld = {
            if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}"
        })
    }

    @Test
    fun readCompareVsFuzz() {
        val dirname = "/home/stormy/temp/estimate"
        val filename = "ComparisonStandardVsFuzz"

        val plotter = PlotSampleSizes(dirname, filename)
        plotter.showSamples(catfld = { if (it.fuzzPct == 0.0) "standard" else "fuzz=${it.fuzzPct}" })
    }

    @Test
    fun plotPollingFuzz() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(
            AuditType.POLLING,
            hasStyles = true,
            seed = 12356667890L,
            quantile = .80,
            fuzzPct = .01,
            ntrials = 1000
        )
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<AlphaTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val sim = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
                val cvrs = sim.makeCvrs()

                print("fuzzPct = $fuzzPct, margin = $margin ${sim.contest.votes}")
                val contestUA = ContestUnderAudit(sim.contest, isComparison = false)
                contestUA.makePollingAssertions()
                val minAssort = contestUA.minAssertion()!!.assorter
                val sampleFn = PollingFuzzSampler(fuzzPct, cvrs, contestUA.contest as Contest, minAssort)

                val otherParameters = mapOf("fuzzPct" to fuzzPct)
                tasks.add(
                    AlphaTask(
                        "fuzzPct = $fuzzPct, margin = $margin", auditConfig,
                        sampleFn, margin, minAssort.upperBound(), N, N, otherParameters,
                    )
                )
            }
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirName = "/home/stormy/temp/estimate"
        val filename = "PollingFuzzed"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showFuzzedSamples()
    }

    @Test
    fun plotComparisonFuzz() {
        val N = 10000
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)

        val auditConfig = AuditConfig(
            AuditType.CARD_COMPARISON,
            hasStyles = true,
            seed = 12356667890L,
            quantile = .80,
            fuzzPct = .01,
            ntrials = 1000
        )
        println("ntrials = ${auditConfig.ntrials} quantile = ${auditConfig.quantile} N=${N}")

        val tasks = mutableListOf<BettingTask>()
        fuzzPcts.forEach { fuzzPct ->
            margins.forEach { margin ->
                val sim = ContestSimulation.make2wayTestContest(margin, 0.0, 0.0, Nc=N) // TODO
                val cvrs = sim.makeCvrs()

                print("fuzzPct = $fuzzPct, margin = $margin ${sim.contest.votes}")
                val contestUA = ContestUnderAudit(sim.contest)

                // comparison; regen mvrs each repition to smoothe things out
                contestUA.makeComparisonAssertions(cvrs)
                val minAssort = contestUA.minComparisonAssertion()!!.cassorter
                val sampleFn = ComparisonFuzzSampler(fuzzPct, cvrs, contestUA.contest as Contest, minAssort)

                val otherParameters = mapOf("fuzzPct" to fuzzPct)
                tasks.add(
                    BettingTask(
                        "fuzzPct = $fuzzPct, margin = $margin", auditConfig,
                        sampleFn, margin, minAssort.noerror, minAssort.upperBound, N, N,
                        ComparisonErrorRates.getErrorRates(contestUA.ncandidates, fuzzPct),
                        otherParameters,
                    )
                )
            }
        }
        // run tasks concurrently
        val results: List<RunTestRepeatedResult> = ConcurrentTaskRunner().run(tasks)
        val srts = results.map { it.makeSRT(0.0, 0.0) }

        val dirName = "/home/stormy/temp/estimate"
        val filename = "ComparisonFuzzed"
        val writer = SRTcsvWriter("$dirName/${filename}.cvs")
        writer.writeCalculations(srts)
        writer.close()

        val plotter = PlotSampleSizes(dirName, filename)
        plotter.showFuzzedSamples()
    }
}

class BettingTask(val name: String,
                  val auditConfig: AuditConfig,
                  val sampleFn: Sampler,
                  val margin: Double,
                  val noerror: Double,
                  val upperBound: Double,
                  val maxSamples: Int,
                  val Nc: Int,
                  val errorRates: List<Double>,
                  val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        //  this uses auditConfig p1,p4 to set apriori error rates. should be based on fuzzPct i think
        return simulateSampleSizeBetaMart(auditConfig, sampleFn, margin, noerror, upperBound, Nc=Nc,
            errorRates, moreParameters=otherParameters)
    }
}

// we have an assorter
class PollingTask(
    val name: String,
    val auditConfig: AuditConfig,
    val contestUA: ContestUnderAudit,
    val assort: AssorterFunction,
    val Nc: Int,
    val moreParameters: Map<String, Double> = emptyMap(),
) : ConcurrentTask {
    override fun name() = name
    override fun run(): RunTestRepeatedResult {
        return simulateSampleSizePollingAssorter(auditConfig, contestUA.contest as Contest, assort, moreParameters=moreParameters)
    }
}

// we dont have an assorter
class AlphaTask(val name: String,
                val auditConfig: AuditConfig,
                val sampleFn: Sampler,
                val margin: Double,
                val upperBound: Double,
                val maxSamples: Int,
                val Nc: Int,
                val otherParameters: Map<String, Double>,
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return simulateSampleSizeAlphaMart(auditConfig, sampleFn, margin, upperBound, Nc=Nc, moreParameters=otherParameters)
    }
}

class ComparisonTask(val name: String,
                     val auditConfig: AuditConfig,
                     val contestUA: ContestUnderAudit,
                     val cassort: ComparisonAssorter,
                     val cvrs: List<Cvr>,
                     val moreParameters: Map<String, Double> = emptyMap(),
): ConcurrentTask {
    override fun name() = name
    override fun run() : RunTestRepeatedResult {
        return simulateSampleSizeComparisonAssorter(auditConfig, contestUA.contest as Contest, cassort, cvrs, moreParameters=moreParameters)
    }
}

