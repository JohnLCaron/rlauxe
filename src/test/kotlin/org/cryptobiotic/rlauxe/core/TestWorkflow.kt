package org.cryptobiotic.rlauxe.core

import kotlin.test.Test

class TestWorkflow {

    val showContests = false

    data class SR(val d: Int, val N: Int, val margin:Double,
                  val sampleCountAvg:Double, val speedup:Double, val pctVotes: Double) {
    }

    fun makeSR(d: Int, N: Int, margin:Double, speedup: Double, rr: AlphaMartRepeatedResult): SR {
        val pctVotes = (100.0 * rr.sampleCountAvg() / N)
        return SR(d, N, margin, rr.sampleCountAvg().toDouble(), speedup, pctVotes)
    }

    fun showSRSbyMargins(srs: List<SR>, ns: List<Int>, margins:List<Double>) {
        margins.forEach{ showSRSmargin(srs, ns, it) }
    }

    fun showSRSmargin(srs: List<SR>, ns: List<Int>, margin : Double) {
        println()
        println("pctVotes sampled for margin = ${(100*margin).toInt()}% and d (row) vs total votes (col)")
        print("     d, ")
        ns.forEach{ print("${"%6d".format(it)}, ")}
        println()

        val mmap = mutableMapOf<Int, MutableMap<Int, Double>>() // d, n -> pct
        srs.filter { it.margin == margin }.forEach {
            val dmap = mmap.getOrPut(it.d) { mutableMapOf() }
            dmap[it.N] = it.pctVotes
        }

        mmap.toSortedMap().forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, nmap ->
                print("${"%6.2f".format(nmap)}, ")
            }
            println()
        }
    }

    fun showSRSbyNBallots(srs: List<SR>, ns: List<Int>, margins:List<Double>) {
        ns.forEach{ showSRSnballots(srs, it, margins) }
    }

    fun showSRSnballots(srs: List<SR>, ns: Int, margins : List<Double>) {
        println()
        println("nVotes sampled for population size = ${ns} and d (row) vs theta (col)")
        print("     d, ")
        val theta = margins.sorted().map { .5 + it * .5 }
        theta.forEach{ print("${"%6.3f".format(it)}, ")}
        println()

        val mmap = mutableMapOf<Int, MutableMap<Double, Int>>() // d, m -> pct
        srs.filter { it.N == ns }.forEach {
            val dmap = mmap.getOrPut(it.d) { mutableMapOf() }
            dmap[it.margin] = (it.pctVotes * ns / 100).toInt()
        }

        mmap.toSortedMap().forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, nmap ->
                print("${"%6d".format(nmap)}, ")
            }
            println()
        }
    }

    //
    fun showSRSnVt(srs: List<SR>, ns: List<Int>, margins : List<Double>) {
        println()
        println("nvotes sampled vs N; various theta = winning percent")
        print("     N, ")
        val theta = margins.sorted().map { .5 + it * .5 }
        theta.forEach{ print("${"%6.3f".format(it)}, ")}
        println()

        val mmap = mutableMapOf<Int, MutableMap<Double, Int>>() // N, m -> pct
        srs.forEach {
            val dmap = mmap.getOrPut(it.N) { mutableMapOf() }
            dmap[it.margin] = (it.pctVotes * it.N / 100).toInt()
        }

        mmap.toSortedMap().forEach { dkey, dmap ->
            print("${"%6d".format(dkey)}, ")
            dmap.toSortedMap().forEach { nkey, nmap ->
                print("${"%6d".format(nmap)}, ")
            }
            println()
        }
    }

    @Test
    fun testPollingWorkflow() {
        val dl = listOf(100) // 10, 100, 500, 1000, 2000)
        val margins = listOf(.4, .2, .1, .08, .06, .04, .02, .01) // winning percent: 70, 60, 55, 54, 53, 52, 51, 50.5
        val Nlist = listOf(1000, 5000, 10000, 20000, 50000)
        val srs = mutableListOf<SR>()
        val show = false

        if (show) println("d, N, margin, eta0, without, with, speedup, pctVotes, failWith, sampleSumOver")

        dl.forEach { d ->
            Nlist.forEach { N ->
                margins.forEach { margin ->
                    val cvrs = makeCvrsByExactMargin(N, margin)
                    val resultWithout =
                        testPollingWorkflow(margin, withoutReplacement = true, cvrs, d, silent = true).first()
                    val resultWith =
                        testPollingWorkflow(margin, withoutReplacement = false, cvrs, d, silent = true).first()
                    if (show) print("$d, ${cvrs.size}, $margin, ${resultWithout.eta0}, ")
                    val speedup = resultWith.sampleCountAvg().toDouble() / resultWithout.sampleCountAvg().toDouble()
                    val pct = (100.0 * resultWithout.sampleCountAvg().toDouble() / N).toInt()

                    if (show) print("${resultWithout.sampleCountAvg().toDouble()}, ${resultWith.sampleCountAvg().toDouble()}, ${"%5.2f".format(speedup)}, ")
                    if (show) println("${pct}, ${resultWith.failPct}, ${resultWithout.status}")
                    srs.add(makeSR(d, N, margin, speedup, resultWithout))
                }
                if (show) println()
            }
        }
        showSRSnVt(srs, Nlist, margins)
        // showSRSbyNBallots(srs, Nlist, margins)
    }

    fun testPollingWorkflow(margin: Double, withoutReplacement: Boolean, cvrs: List<Cvr>, d: Int, silent: Boolean = true): List<AlphaMartRepeatedResult> {
        val N = cvrs.size
        if (!silent) println(" d= $d, N=${cvrs.size} margin=$margin ${if (withoutReplacement) "withoutReplacement" else "withReplacement"}")

        // count actual votes
        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        if (!silent && showContests) {
            votes.forEach { key, cands ->
                println("contest ${key} ")
                cands.forEach { println("  ${it} ${it.value.toDouble() / cvrs.size}") }
            }
        }

        // make contests from cvrs
        val contests: List<AuditContest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))
        if (!silent && showContests) {
            println("Contests")
            contests.forEach { println("  ${it}") }
        }

        // Polling Audit
        val audit = PollingAudit(auditType = AuditType.POLLING, contests = contests)

        // this has to be run separately for each assorter, but we want to combine them in practice
        val results = mutableListOf<AlphaMartRepeatedResult>()
        audit.assertions.map { (contest, assertions) ->
            if (!silent && showContests) println("Assertions for Contest ${contest.id}")
            assertions.forEach {
                if (!silent && showContests) println("  ${it}")

                val cvrSampler = if (withoutReplacement) PollWithoutReplacement(cvrs, it.assorter) else PollWithReplacement(cvrs, it.assorter)
                val result = runAlphaMartRepeated(
                    drawSample = cvrSampler,
                    maxSamples = N,
                    theta = cvrSampler.truePopulationMean(),
                    eta0 = margin2theta(margin),
                    d = d,
                    nrepeat = 10,
                    withoutReplacement = withoutReplacement,
                )
                if (!silent) println(result)
                results.add(result)
            }
        }
        return results // TODO only one
    }

}

// 8/31/2024
// compares well with table 3 of ALPHA
// eta0 = theta, no divergence of sample from true. 100 repetitions
//
// nVotes sampled for population size = 20000 and d (row) vs theta (col),
//     d,  0.505,  0.510,  0.520,  0.550,  0.600,  0.700,
//    10,  14278,   9584,   4215,    836,    164,     47,
//   100,  15038,   9337,   4007,    648,    161,     42,
//   500,  14356,   8336,   3359,    557,    154,     37,
//  1000,  13308,   8573,   3126,    553,    154,     37,
//  2000,  14750,   8216,   3251,    600,    138,     45,