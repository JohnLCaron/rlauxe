package org.cryptobiotic.rlauxe.shangrla


import org.cryptobiotic.rlauxe.core.SampleFromArrayWithoutReplacement
import org.cryptobiotic.rlauxe.core.generateUniformSample
import org.cryptobiotic.rlauxe.plots.SRT
import org.junit.jupiter.api.Test

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
    @Test
    fun compareAlphaPaperMasses() {
        val ntrials = 100
        val mixtures = listOf(.99, .9, .75, .5, .25, .1, .01) // mass at 1

        val zero_mass = listOf(0.0, 0.001) // mass at 0
        val zm = zero_mass[1]
        val zm1 = 1.0 - zm

        val al = mutableListOf<SRT>()
        val dl = listOf(10, 100) // for alpha
        val c_base = 0.5 // for alpha. larger c since there is no particular expectation about error rates
        val etal = listOf(.99, .9, .75, .55)

        val N = 100

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
            val theta = sampleFn.truePopulationMean()
            println(" testMass m=$m N=$N theta=$theta")
            thetas.add(theta)
            println("  xp = $xp")

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
        // testMass m=0.99 N=10000 theta=0.9947844780071875
        // testMass m=0.9 N=10000 theta=0.9474056076403589
        // testMass m=0.75 N=10000 theta=0.873438390209044
        // testMass m=0.5 N=10000 theta=0.7523821325977146
        // testMass m=0.25 N=10000 theta=0.621023803723251
        // testMass m=0.1 N=10000 theta=0.5537632739421577
        // testMass m=0.01 N=10000 theta=0.5043223724038491

        // python results
        // 	m=0.99 N=10000 t=0.9946234445107308
        //	m=0.9 N=10000 t=0.9491996903839541
        //	m=0.75 N=10000 t=0.872444371417956
        //	m=0.5 N=10000 t=0.7438389502041729
        //	m=0.25 N=10000 t=0.6236597364242038
        //	m=0.1 N=10000 t=0.5499456507451624
        //	m=0.01 N=10000 t=0.5059920323745111

        // whats the equivilent CVR ?
        //  bassort in [0.0, 0.25252525252525254, 0.5050505050505051, 0.7575757575757576, 1.0101010101010102]
        //      where margin = 0.020000000000000018 noerror=0.5050505050505051
        // it seems likely that he is working in normlized space, so divide everything by noerror, so possible values are
        //   bassort in [0.0, 1/4, 1/2, 3/4, 1]

        // A. "nonnegative populations that had mass 0.001 at zero, corresponding to errors that overstate the margin
        // by the maximum possible, e.g., that erroneously interpreted a vote for the loser as a vote for the winner"
        // 0 means "flipped vote from loser to winner" in the cvr
        // So 1/1000 ballots has loser flipped to winner in the cvr (so opposite in mvr)
        //
        // B. "mass m ∈ {0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99} at 1"
        // 1 means "flipped vote from winner to loser" in the cvr
        // but why would there be such a high percentage?
        // seems like this should have the same probability as A?
        //
        // C. "mass (1 − m − 0.001) uniformly distributed on [0, 1]"
        // I guess to simulate random errors?

        // It looks to me that theres a factor of 2 error when he switched to normalized values.
        // the only realistic meaning of a large mass is for the "no error" case.
        // in which case those should be at 1/2, not 1.



    }
}