package org.cryptobiotic.rlauxe.strata

import kotlin.test.Test

class TestUITS {

    @Test
    fun test_simulate_plurcomp() {
        val Nk = listOf(200, 200)
        val alt = 0.55
        val delta = 0.2
        val A_c = listOf(alt - 0.5*delta, alt + 0.5*delta)


        // fun simulate_plurcomp(
        //    Nk: List<Int>,   // a length-K list of the size of each stratum
        //    A_c: List<Double>, // a length-K np.array of floats the reported assorter mean bar{A}_c in each stratum
        //    p_1: DoubleArray =  doubleArrayOf(0.0, 0.0), // a length-K np.array of floats the true rate of 1 vote overstatements in each stratum, defaults to none
        //    p_2: DoubleArray =  doubleArrayOf(0.0, 0.0),  // a length-K np.array of floats the true rate of 2 vote overstatements in each stratum, defaults to none
        //    bet: String, // callable, a function from class Bets the function for setting the bets (lambda_{ki}) for each stratum / time
        //    selection: String, // how to select which strata to sample
        //    inference: String, //  either "ui-ts" or "lcbs" the inference method for testing the global null
        //    n_bands: Int = 100,
        //    alpha:Double = 0.05, // the significance level of the test
        //    WOR: Boolean = false, // sampling without replacement
        //    reps: Int = 30, // the number of simulations of the audit to run
        //)
        val x = simulate_plurcomp(Nk, A_c, bet = "inverse_eta", selection="round_robin", inference="ui-ts", n_bands=10, reps = 1,  )
        print("sample size = $x")

    }
}