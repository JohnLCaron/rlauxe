package org.cryptobiotic.rlauxe.alpha


import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.ComparisonNoErrors
import org.cryptobiotic.rlauxe.sampling.ComparisonWithErrors
import org.cryptobiotic.rlauxe.sampling.ArrayAsGenSampleFn
import org.cryptobiotic.rlauxe.sampling.SampleFromArrayWithoutReplacement
import org.cryptobiotic.rlauxe.sampling.generateUniformSample
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.core.doOneAlphaMartRun
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals

class TestComparisonMasses {
    // trying to understand what this paragraph from ALPHA p 21 means:
    // "To assess the relative performance of these supermartingales for comparison audits, they
    // were applied to pseudo-random samples from nonnegative populations that had mass 0.001
    // at zero (corresponding to errors that overstate the margin by the maximum possible, e.g.,
    // that erroneously interpreted a vote for the loser as a vote for the winner), mass m ∈
    // {0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99} at 1, and the remaining mass uniformly distributed on [0, 1]."
    //
    // caption on table 7
    //   mass 0.001 at zero, mass m at 1, and mass 1 − m − 0.001
    //   uniformly distributed on [0, 1], for values of m between 0.99 and 0.5

    // replicate results from alpha.ipynb, "# set up simulations"
    // also see TestComparisonFromAlpha, testSetupSimulations
    // TODO fix this now that I understand it better
    @Test
    fun compareAlphaPaperMasses() {
        val mixtures = listOf(.1, .05, .01) // mass at 1

        val zero_mass = listOf(0.0, 0.001) // mass at 0
        val zm = zero_mass[1]
        val zm1 = 1.0 - zm

        val N = 10000

        val thetas = mutableListOf<Double>()
        for (m in mixtures) {
            var t = 0.0
            var xp: List<Double> = listOf(0.0) // random x with mean > .5
            while (t < 0.5) {
                val x = generateUniformSample(N) // random_state.uniform(0.0, 1.0, size) : prob uniform dist on 0, 1
                val y = generateUniformSample(N)
                xp = x.mapIndexed { idx, it ->
                    if (y[idx] < m) 1.0
                    else if (y[idx] >= zm1) 0.0
                    else it
                }
                t = xp.average()
            }
            val sampleFn = SampleFromArrayWithoutReplacement(xp.toDoubleArray())
            val theta = sampleFn.sampleMean()
            println(" testMass m=$m N=$N theta=$theta")
            thetas.add(theta)
        }

        val expected = listOf(
            0.9946234445107308, 0.9491996903839541, 0.872444371417956,
            0.7438389502041729, 0.6236597364242038, 0.5499456507451624, 0.5059920323745111
        )

        /* within 1%
        thetas.forEachIndexed { idx, it ->
            assertEquals(expected[idx], it, .01)
        }

         */

        // our results
        // testMass m=0.99 N=10000 theta=0.9941992429721956
        // testMass m=0.9 N=10000 theta=0.944582290094878
        // testMass m=0.75 N=10000 theta=0.8741689022240599
        // testMass m=0.5 N=10000 theta=0.7498653712253592
        // testMass m=0.25 N=10000 theta=0.6263512529540856
        // testMass m=0.1 N=10000 theta=0.5521977213321936
        // testMass m=0.01 N=10000 theta=0.5051887823907698

        // python results
        // 	m=0.99 N=10000 t=0.9946234445107308
        //	m=0.9 N=10000 t=0.9491996903839541
        //	m=0.75 N=10000 t=0.872444371417956
        //	m=0.5 N=10000 t=0.7438389502041729
        //	m=0.25 N=10000 t=0.6236597364242038
        //	m=0.1 N=10000 t=0.5499456507451624
        //	m=0.01 N=10000 t=0.5059920323745111

        // whats the equivilent CVR with errors ?
        //  bassort in [0.0, 0.25252525252525254, 0.5050505050505051, 0.7575757575757576, 1.0101010101010102]
        //      where margin = 0.020000000000000018 noerror=0.5050505050505051

        val cvrMean = 0.55
        val cvrMeanDiff = -0.025
        val theta = cvrMean + cvrMeanDiff  // the true mean of the MVRs
        println("\nN=$N cvrMean=$cvrMean cvrMeanDiff=$cvrMeanDiff theta=${theta}")

        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val compareAssorter = contestUA.comparisonAssertions.first().assorter

        // sanity checks
        val sampler = ComparisonWithErrors(cvrs, compareAssorter, theta, withoutReplacement = true)
        val actualMvrMean = sampler.mvrs.map{ it.hasMarkFor(0,0) }.average()
        assertEquals(theta, actualMvrMean, doublePrecision)
        println("flippedVotes = ${sampler.flippedVotes} = ${100.0 * abs(sampler.flippedVotes)/N}%  actualMvrMean=$actualMvrMean")

        val bassortAvg = sampler.sampleMean()
        val bvalues = DoubleArray(N) { sampler.sample() }
        println("bassortAvg = ${bvalues.average()}")
        assertEquals(bassortAvg, bvalues.average(), doublePrecision) // wont be exact unless its withoutReplacement

        println()
    }

    // is it true we can normalize the assorter valeus and get the same result?
    @Test
    fun compareNormalizing() {
        val N = 100
        val cvrMean = .55
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val compareAssorter = contestUA.comparisonAssertions.first().assorter

        val sampler = ComparisonNoErrors(cvrs, compareAssorter)
        val assorterMean = sampler.sampleMean()

        val d = 100

        println("N=$N cvrMean=$cvrMean assorterMean=$assorterMean noerror=${compareAssorter.noerror}, u=${compareAssorter.upperBound}")
        println("sampler mean=${sampler.sampleMean()}, count=${sampler.sampleCount()}")

        val resultNotNormalized = doOneAlphaMartRun(sampler, maxSamples = N, eta0 = assorterMean, d = d, u = compareAssorter.upperBound)
        println("resultNotNormalized = $resultNotNormalized}")
        println("resultNotNormalized = ${resultNotNormalized.sampleCount}")
        println()

        val normalizedValues = cvrs.map{ compareAssorter.bassort(it, it) / compareAssorter.noerror / 2 } // makes everything 1/2 when no errors
        val samplerN = ArrayAsGenSampleFn(normalizedValues.toDoubleArray())
        println("samplerN mean=${samplerN.sampleMean()}, count=${samplerN.sampleCount()}")

        val resultNormalized = doOneAlphaMartRun(samplerN, maxSamples = N, eta0 = assorterMean, d = d, u = 1.0)
        println("resultNormalized = $resultNormalized}")
        println("\nresultNormalized = ${resultNormalized.sampleCount}")

        // it would seem that the idea of normalizing is wrong, the algorithm relies on alternative mean > 1/2.
        // probably violating the conditions of an assorter, namely B̄ ≡ Sum(B(bi, ci)) / N > 1/2
    }



}