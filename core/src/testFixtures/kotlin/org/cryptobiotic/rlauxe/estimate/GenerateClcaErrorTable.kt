package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.ClcaErrorRatesCumul
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.test.Test

class GenerateClcaErrorTable {
    val showRates = false

    @Test
    fun generateErrorTableFromContestTestDataBuilder() {
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyle = true, seed = 12356667890L, nsimEst = 10)
        val N = 100000

        // TODO how much do the rates depend on the margin?
        val margin = .05
        val ncands = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        val underVotePcts = listOf(0.01, .05, .1, .2, .5)
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)

        underVotePcts.forEach { underVotePct ->

            val result = mutableMapOf<Int, List<Double>>()
            println("underVotePct=$underVotePct N=$N ntrials = ${auditConfig.nsimEst}")
            println("| ncand | r2o    | r1o    | r1u    | r2u    |")
            println("|-------|--------|--------|--------|--------|")
            ncands.forEach { ncand ->
                val fcontest = ContestTestDataBuilder(0, ncand, margin, underVotePct, 0.0)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                // print("contest votes = ${contest.votes} ")
                val sim = ContestSimulation(contest, contest.Nc)

                val sumRForNcand = mutableListOf(0.0, 0.0, 0.0, 0.0)
                fuzzPcts.forEach { fuzzPct ->
                    val sumRForPct = mutableListOf(0.0, 0.0, 0.0, 0.0)

                    repeat(auditConfig.nsimEst) {
                        val cvrs = sim.makeCvrs()
                        val contestUA = ContestUnderAudit(contest, true, hasStyle = true).addStandardAssertions()
                        val minAssert = contestUA.minClcaAssertion()!!
                        val minAssort = minAssert.cassorter

                        val tracker = PluralityErrorTracker(minAssort.noerror())
                        val sampler = ClcaFuzzSampler(fuzzPct, cvrs, contestUA.contest as Contest, minAssort)
                        while (sampler.hasNext()) {
                            tracker.addSample(sampler.next())
                        }
                        tracker.pluralityErrorRatesList()
                            .forEachIndexed { idx, rate -> sumRForNcand[idx] = sumRForNcand[idx] + (rate / fuzzPct) }
                        tracker.pluralityErrorRatesList()
                            .forEachIndexed { idx, rate -> sumRForPct[idx] = sumRForPct[idx] + (rate / fuzzPct) }
                    }
                    if (showRates) {
                        print("   $fuzzPct = [")
                        sumRForPct.forEach { R -> print(" ${df(R / auditConfig.nsimEst)},") }
                        println("]")
                    }
                }
                val avgRforNcand = sumRForNcand.map { it / (auditConfig.nsimEst * fuzzPcts.size) }
                print("| $ncand | ")
                avgRforNcand.forEach { avgR -> print(" ${df(avgR)} |") }
                println()

                result[ncand] = avgRforNcand
            }
            val code = buildString {
                result.toSortedMap().forEach { (key, value) ->
                    append("rrates[$key] = listOf(")
                    value.forEach { append("${dfn(it, 7)}, ") }
                    append(")\n")
                }
            }
            println(code)
        }
    }

    @Test
    fun testFuzzedCardsErrors() {
        val show = false
        val ncontests = 11
        val phantomPct = 0.02
        val test = MultiContestTestData(ncontests, 1, 50000, phantomPctRange=phantomPct..phantomPct)
        val contestsUA = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val cards = test.makeCardsFromContests()
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)

        println(" testFuzzedCardsErrors phantomPct = $phantomPct")
        println("              ${ClcaErrorRatesCumul.header()}")

        fuzzPcts.forEach { fuzzPct ->
            val fcards = makeFuzzedCardsFrom(contestsUA.map { it.contest.info() }, cards, fuzzPct)
            val testPairs = fcards.zip(cards)

            val avgErrorRates = ClcaErrorRatesCumul()
            contestsUA.forEach { contestUA ->
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val samples = PluralityErrorTracker(cassorter.noerror())
                    if (show) println("  contest = ${contestUA.id} assertion = ${cassorter.shortName()}")

                    testPairs.forEach { (fcard, card) ->
                        if (card.hasContest(contestUA.id)) {
                            samples.addSample(cassorter.bassort(fcard.cvr(), card.cvr()))
                        }
                    }
                    if (show) println("    errorCounts = ${samples.pluralityErrorCounts()}")
                    if (show) println("    errorRates =  ${samples.errorRates()}")

                    avgErrorRates.add(samples.pluralityErrorRates())
                }
            }
            println("fuzzPct ${dfn(fuzzPct,3)}: ${avgErrorRates}")
        }
        println()
    }

    @Test
    fun generateErrorTable() {
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyle = true, seed = 12356667890L, nsimEst = 1000)
        val N = 100000

        // TODO how much do the rates depend on the margin?
        val margin = .05
        val ncands = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        val underVotePcts = listOf(0.01, .05, .1, .2, .5)
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)

        underVotePcts.forEach { underVotePct ->

            val result = mutableMapOf<Int, List<Double>>()
            println("underVotePct=$underVotePct N=$N ntrials = ${auditConfig.nsimEst}")
            println("| ncand | r2o    | r1o    | r1u    | r2u    |")
            println("|-------|--------|--------|--------|--------|")
            ncands.forEach { ncand ->
                val fcontest = ContestTestDataBuilder(0, ncand, margin, underVotePct, 0.0)
                fcontest.ncards = N
                val contest = fcontest.makeContest()
                // print("contest votes = ${contest.votes} ")
                val sim = ContestSimulation(contest, contest.Nc)

                val sumRForNcand = mutableListOf(0.0, 0.0, 0.0, 0.0)
                fuzzPcts.forEach { fuzzPct ->
                    val sumRForPct = mutableListOf(0.0, 0.0, 0.0, 0.0)

                    repeat(auditConfig.nsimEst) {
                        val cvrs = sim.makeCvrs()
                        val contestUA = ContestUnderAudit(contest, true, hasStyle = true).addStandardAssertions()
                        val minAssert = contestUA.minClcaAssertion()!!
                        val minAssort = minAssert.cassorter

                        val tracker = PluralityErrorTracker(minAssort.noerror())
                        val sampler = ClcaFuzzSampler(fuzzPct, cvrs, contestUA.contest as Contest, minAssort)
                        while (sampler.hasNext()) {
                            tracker.addSample(sampler.next())
                        }
                        tracker.pluralityErrorRatesList()
                            .forEachIndexed { idx, rate -> sumRForNcand[idx] = sumRForNcand[idx] + (rate / fuzzPct) }
                        tracker.pluralityErrorRatesList()
                            .forEachIndexed { idx, rate -> sumRForPct[idx] = sumRForPct[idx] + (rate / fuzzPct) }
                    }
                    if (showRates) {
                        print("   $fuzzPct = [")
                        sumRForPct.forEach { R -> print(" ${df(R / auditConfig.nsimEst)},") }
                        println("]")
                    }
                }
                val avgRforNcand = sumRForNcand.map { it / (auditConfig.nsimEst * fuzzPcts.size) }
                print("| $ncand | ")
                avgRforNcand.forEach { avgR -> print(" ${df(avgR)} |") }
                println()

                result[ncand] = avgRforNcand
            }
            val code = buildString {
                result.toSortedMap().forEach { (key, value) ->
                    append("rrates[$key] = listOf(")
                    value.forEach { append("${dfn(it, 7)}, ") }
                    append(")\n")
                }
            }
            println(code)
        }
    }

}