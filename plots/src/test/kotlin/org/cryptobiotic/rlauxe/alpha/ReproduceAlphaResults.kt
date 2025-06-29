package org.cryptobiotic.rlauxe.alpha

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.rlaplots.SRT
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.rlaplots.makeSRT
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.estimate.runTestRepeated
import org.cryptobiotic.rlauxe.audit.makeClcaNoErrorSampler
import kotlin.test.Test
import kotlin.test.assertEquals

// from https://github.com/pbstark/alpha/blob/main/Code/alpha.ipynb
class ReproduceAlphaResults {

    // see # set up simulations
    @Test
    fun testSetupSimulations() {
        //# first set: uniform mixed with a pointmass at 1
        //
        //reps = int(10**4)
        //alpha = 0.05
        //mixtures = [.99, .9, .75, .5, .25, .1, .01]  # mass at 1
        //zero_mass = [0, 0.001] # mass at 0
        val reps = 1000
        val mixtures = listOf(.99, .9, .75, .5, .25, .1, .01) // mass at 1
        val zero_mass = listOf(0.0, 0.001) // mass at 0

        //
        //al = {}  # alpha martingale
        //kw = {}  # Kaplan-Wald
        //kk = {}  # Kaplan-Kolmogorov
        //apk = {} # a priori Kelly
        //sqk = {} # square Kelly
        //thetas = {}
        val al = mutableListOf<SRT>()

        //
        //methods = [al, kw, kk, apk, sqk, thetas]
        //
        //g_kol = [0.01, 0.1, 0.2]  # for the Kaplan-Kolmogorov method
        //g_wald = 1 - np.array(g_kol) # for Kaplan-Wald method
        //val g_kol = listOf(0.01, 0.1, 0.2) // for Kaplan-Kolmogorov method
        //val g_wald = g_kol.map { 1.0 - it } // for Kaplan-Wald method

        //
        //D = 10 # for APK
        //beta = 1
        //dl = [10, 100]        # for alpha
        //c_base = 0.5          # for alpha. larger c since there is no particular expectation about error rates
        //etal = [.99, .9, .75, .55]
        //Nl = [10000, 100000, 500000]
        val dl = listOf(10, 100) // for alpha
        val c_base = 0.5 // for alpha. larger c since there is no particular expectation about error rates
        val etal = listOf(.99, .9, .75, .55)
        val Nl = listOf(10000)
        println("ntrials=$reps")
        //
        //
        //for m in mixtures:
        //    for meth in methods:
        //        meth[m] = {}
        //        for N in Nl:
        //            meth[m][N] = {}
        //
        //zm = zero_mass[1]
        val zm = zero_mass[1]
        val zm1 = 1.0 - zm
        mixtures.forEach { m ->
            Nl.forEach { N ->
                etal.forEach { eta ->
                }

                //for m in mixtures:
                //    print(f'{m=}')
                //    for N in Nl:
                //        print(f'\t{N=}')
                //        sqk[m][N]=0
                //        thetas[m][N]=0
                //        for eta in etal:
                //            apk[m][N][eta] = 0
                //            al[m][N][eta] = {}
                //            for d in dl:
                //                al[m][N][eta][d] = 0
                //        for g in g_kol:
                //            kk[m][N][g] = 0
                //        for g in g_wald:
                //            kw[m][N][g] = 0
                //        t = 0
                //        while t <= 0.5:
                //            x = sp.stats.uniform.rvs(size=N)
                //            y = sp.stats.uniform.rvs(size=N)
                //            x[y<=m] = 1
                //            x[y>=(1-zm)] = 0
                //            t = np.mean(x)
                //        thetas[m][N] = t
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

                val assorter_margin = 2*theta - 1
                val u = 2.0/(2-assorter_margin)
                val upperBound = 2.0 / (3 - 2 * theta) // python has 1.0
                require(u == upperBound)
                println("test N=$N m=$m theta=$theta upperBound=$upperBound")

                //        for i in range(reps):
                //            np.random.shuffle(x)
                //            mart = sqKelly_martingale(x, m=1/2, N=N, D=D, beta=beta)
                //            sqk[m][N] += np.argmax(mart >= 1/alpha)
                //            for g in g_kol:
                //                mart = kaplan_kolmogorov(x, N, t=1/2, g=g)
                //                kk[m][N][g] += np.argmax(mart >= 1/alpha)
                //            for g in g_wald:
                //                mart = kaplan_wald(x, N, t=1/2, g=g)
                //                kw[m][N][g] += np.argmax(mart >= 1/alpha)
                //            for eta in etal:
                //                mart = apriori_Kelly_martingale(x, m=0.5, N=N, n_A=int(N*eta), n_B=N-int(N*eta))
                //                apk[m][N][eta] += np.argmax(mart >= 1/alpha)
                //                c = c_base*(eta-1/2)
                //                for d in dl:
                //                    mart = alpha_mart(x, N, mu=1/2, eta=eta, u=1, \
                //                                estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,1,c=c,d=d))
                //                    al[m][N][eta][d] += np.argmax(mart >= 1/alpha)

                for (eta0 in etal) {
                    val c = c_base*(eta0 - 0.5) // TODO wtf?

                    for (d in dl) {
                        val trunc = TruncShrinkage(N = N, upperBound = upperBound, d = d, eta0 = eta0, c = c)
                        val alpha = AlphaMart(estimFn = trunc, N = N, upperBound=upperBound)

                        print("  eta0=$eta0 d=$d")
                        val result =  runTestRepeated(
                            drawSample = sampleFn,
                            // maxSamples = N,
                            ntrials = reps,
                            testFn = alpha,
                            testParameters = mapOf("eta0" to eta0, "d" to d.toDouble()),
                            margin = mean2margin(eta0),
                            Nc=N,
                        )
                        println("  avgSamplesNeeded = ${result.avgSamplesNeeded()}")
                        al.add(result.makeSRT(theta, 0.0))
                    }
                }
            }
        }

        /* TODO
        val sqk = mutableMapOf<>()
        val thetas = mutableMapOf<>()

        mixtures.forEach { m ->
            println("m=$m")
            Nl.forEach { N ->
                println("N=$N")

                var t = 0.0
                while (t < 0.5) {
                    val x = sp.stats.uniform.rvs(size=N)
                    val y = sp.stats.uniform.rvs(size=N)
                    x[y<=m] = 1
                    x[y>=(1-zm)] = 0
                    t = np.mean(x)
            }
        }

         */
        // for m in mixtures:
        //    for N in Nl:
        //        sqk[m][N] = sqk[m][N]/reps + 1
        //        for eta in etal:
        //            apk[m][N][eta] = apk[m][N][eta]/reps + 1
        //            for d in dl:
        //                al[m][N][eta][d] = al[m][N][eta][d]/reps + 1
        //        for g in g_kol:
        //            kk[m][N][g] = kk[m][N][g]/reps + 1
        //        for g in g_wald:
        //            kw[m][N][g] =  kw[m][N][g]/reps +1

        // file_prefix = '../Ms/Results/'
        //file_stems = ['al', 'kw', 'kk', 'apk', 'sqk', 'thetas']
        //for fs in file_stems:
        //    with open(file_prefix+fs+'.json','w') as file:
        //        file.write(json.dumps(eval(fs), cls=NpEncoder, indent = 4))

        // print('\\begin{tabular}{lll|rrr} \n mass at 1 & method & params & $N=$10,000 &  $N=$100,000 & $N=$500,000 \\\\')
        //for m in mixtures:
        //    print(f'''\\hline {m :.2f} & sqKelly & {" ".join(([f"& {sqk[m][N] :,.0f} " for N in Nl]))} \\\\''')
        //    for eta in etal:
        //        print('\\cline{2-6} &' + f''' apKelly & $\\eta=${eta} {" ".join(([f"& {apk[m][N][eta] :,.0f} " for N in Nl]))} \\\\''')
        //        print('\\cline{2-6}')
        //        for d in dl:
        //            print(f'''& ALPHA & $\\eta=${eta} $d=${d} {" ".join(([f"& {al[m][N][eta][d] :,.0f} " for N in Nl]))} \\\\''')
        //    print('\\cline{2-6}')
        //    for g in g_kol:
        //        print(f''' & Kaplan-Kolmogorov & {g=} {" ".join(([f"& {kk[m][N][g] :,.0f} " for N in Nl]))} \\\\''')
        //    print('\\cline{2-6}')
        //    for g in g_wald:
        //        print(f''' & Kaplan-Wald & {g=} {" ".join(([f"& {kw[m][N][g] :,.0f} " for N in Nl]))} \\\\''')

        // print('\\end{tabular} \n')
        //print('\\caption{\\protect \\label{tab:comparison-1} Mean sample sizes to reject the hypothesis that ' +
        //      f'the mean is less than $1/2$ at significance level ${alpha :.2f}$ for various methods, in {reps :,.0f} ' +
        //      f' simulations with mass {zm :.3f} zero, mass $m$ at 1, and mass $1-m-{zm :0.3f}$ uniformly ' +
        //      ' distributed on $[0, 1]$, for values of $m$ between 0.99 and 0.5. The smallest mean sample size ' +
        //      'for each combination of $m$ and $N$ is in bold font.}')
        //
        // ntrials=10000
        //test N=10000 m=0.99 theta=0.993959078301467
        //  eta0=0.99 d=10  avgSamplesNeeded = 5
        //  eta0=0.99 d=100  avgSamplesNeeded = 5
        //  eta0=0.9 d=10  avgSamplesNeeded = 5
        //  eta0=0.9 d=100  avgSamplesNeeded = 6
        //  eta0=0.75 d=10  avgSamplesNeeded = 7
        //  eta0=0.75 d=100  avgSamplesNeeded = 8
        //  eta0=0.55 d=10  avgSamplesNeeded = 11
        //  eta0=0.55 d=100  avgSamplesNeeded = 19
        //test N=10000 m=0.9 theta=0.9485003205258108
        //  eta0=0.99 d=10  avgSamplesNeeded = 5
        //  eta0=0.99 d=100  avgSamplesNeeded = 5
        //  eta0=0.9 d=10  avgSamplesNeeded = 6
        //  eta0=0.9 d=100  avgSamplesNeeded = 6
        //  eta0=0.75 d=10  avgSamplesNeeded = 8
        //  eta0=0.75 d=100  avgSamplesNeeded = 8
        //  eta0=0.55 d=10  avgSamplesNeeded = 12
        //  eta0=0.55 d=100  avgSamplesNeeded = 21
        //test N=10000 m=0.75 theta=0.8791139193134786
        //  eta0=0.99 d=10  avgSamplesNeeded = 7
        //  eta0=0.99 d=100  avgSamplesNeeded = 7
        //  eta0=0.9 d=10  avgSamplesNeeded = 8
        //  eta0=0.9 d=100  avgSamplesNeeded = 8
        //  eta0=0.75 d=10  avgSamplesNeeded = 10
        //  eta0=0.75 d=100  avgSamplesNeeded = 10
        //  eta0=0.55 d=10  avgSamplesNeeded = 15
        //  eta0=0.55 d=100  avgSamplesNeeded = 26
        //test N=10000 m=0.5 theta=0.7473513681447918
        //  eta0=0.99 d=10  avgSamplesNeeded = 16
        //  eta0=0.99 d=100  avgSamplesNeeded = 15
        //  eta0=0.9 d=10  avgSamplesNeeded = 16
        //  eta0=0.9 d=100  avgSamplesNeeded = 14
        //  eta0=0.75 d=10  avgSamplesNeeded = 19
        //  eta0=0.75 d=100  avgSamplesNeeded = 18
        //  eta0=0.55 d=10  avgSamplesNeeded = 28
        //  eta0=0.55 d=100  avgSamplesNeeded = 43
        //test N=10000 m=0.25 theta=0.6261105699927882
        //  eta0=0.99 d=10  avgSamplesNeeded = 59
        //  eta0=0.99 d=100  avgSamplesNeeded = 69
        //  eta0=0.9 d=10  avgSamplesNeeded = 56
        //  eta0=0.9 d=100  avgSamplesNeeded = 52
        //  eta0=0.75 d=10  avgSamplesNeeded = 61
        //  eta0=0.75 d=100  avgSamplesNeeded = 48
        //  eta0=0.55 d=10  avgSamplesNeeded = 82
        //  eta0=0.55 d=100  avgSamplesNeeded = 100
        //test N=10000 m=0.1 theta=0.5432493788831303
        //  eta0=0.99 d=10  avgSamplesNeeded = 564
        //  eta0=0.99 d=100  avgSamplesNeeded = 1004
        //  eta0=0.9 d=10  avgSamplesNeeded = 513
        //  eta0=0.9 d=100  avgSamplesNeeded = 651
        //  eta0=0.75 d=10  avgSamplesNeeded = 491
        //  eta0=0.75 d=100  avgSamplesNeeded = 394
        //  eta0=0.55 d=10  avgSamplesNeeded = 557
        //  eta0=0.55 d=100  avgSamplesNeeded = 518
        //test N=10000 m=0.01 theta=0.5045508021773442
        //  eta0=0.99 d=10  avgSamplesNeeded = 8026
        //  eta0=0.99 d=100  avgSamplesNeeded = 9019
        //  eta0=0.9 d=10  avgSamplesNeeded = 7903
        //  eta0=0.9 d=100  avgSamplesNeeded = 8744
        //  eta0=0.75 d=10  avgSamplesNeeded = 7744
        //  eta0=0.75 d=100  avgSamplesNeeded = 7988
        //  eta0=0.55 d=10  avgSamplesNeeded = 7778
        //  eta0=0.55 d=100  avgSamplesNeeded = 7640
    }

    // See ## Simulation of a comparison audit, line starting with "overstatement_assorter ="
    @Test
    fun comparisonSimulation() {
        // overstatement_assorter = lambda overstatement_in_votes, assorter_margin :\
        //                         (1-(overstatement_in_votes/2))/(2-assorter_margin)
        fun overstatement_assorter(overstatement_in_votes: Int, assorter_margin: Double): Double {
            return (1 - (overstatement_in_votes / 2)) / (2 - assorter_margin)
        }

        //# contest
        //alpha = 0.05
        //N = 10000  # ballot cards containing the contest
        //u_b = 1    # upper bound on assorter for social choice function
        //assorter_mean = (9000*0.51*1 + 1000*.5)/N  # contest has 51% for winner in 9000 valid votes, and 1000 non-votes
        //assorter_margin = 2*assorter_mean - 1
        val theta = 0.51
        val N = 10000
        val assorter_mean = (9000*theta + 1000*.5)/N // contest has 51% for winner in 9000 valid votes, and 1000 non-votes
        val assorter_margin = 2*assorter_mean - 1

        //reps = int(10**2)
        //
        //u = 2/(2-assorter_margin)
        //dl = [10, 100, 1000, 10000, 100000]
        //c = 1/2
        //etal = [0.9, 1, u, 2, 2*u]
        //al = {}
        val reps = 1000
        val u = 2.0/(2-assorter_margin)
        assertEquals(1.009081735, u, doublePrecision)
        val dl = listOf(10, 100, 1000, 10000, 100000)
        val etal = listOf(0.9, 1.0, u) // eta cant be larger than u , 2.0, 2.0 * u) // should be .9, 1, 1.009, 2, 2.018

        // TODO check you get same result
        //x = np.full(N, overstatement_assorter(0, assorter_margin))  # error-free in this simulation, wi = 0
        val x = DoubleArray(N) {
            overstatement_assorter(0, assorter_margin)
        }
        //
        //for eta in etal:
        //    al[eta] = {}
        //    for d in dl:
        //        al[eta][d] = 0
        //
        //for i in range(reps):
        //    np.random.shuffle(x)
        //    for eta in etal:
        //        for d in dl:
        //            mart = alpha_mart(x, N, mu=1/2, eta=eta, u=u, \
        //                    estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,u,c=c,d=d))
        //            al[eta][d] += np.argmax(mart >= 1/alpha)

        // fun runAlphaMartRepeated(
        //    drawSample: SampleFn,
        //    maxSamples: Int,
        //    theta: Double,
        //    eta0: Double,
        //    d: Int = 500,
        //    nrepeat: Int = 1,
        //    showDetail: Boolean = false

        val srs = mutableListOf<SRT>()
        for (eta in etal) {
            for (d in dl) {
                val mart: RunTestRepeatedResult = runAlphaMartRepeated(
                    drawSample = SampleFromArrayWithoutReplacement(x),
                    // maxSamples = N,
                    eta0 = eta,
                    d = d,
                    ntrials = reps,
                    upperBound = u,
                )
                srs.add(mart.makeSRT(reportedMean=theta, reportedMeanDiff=0.0))
            }
        }

        println("TestComparisonFromAlpha.comparisonSimulation ntrials=$reps")
        val title = " nsamples, ballot comparison, theta = $theta, assortTheta=$assorter_mean, N=$N, error-free\n d (col) vs eta0 (row)"
        plotSRS(srs, title, true, colf="%6.0f", rowf="%6.3f",
            colFld = { srt: SRT -> srt.d.toDouble() },
            rowFld = { srt: SRT -> srt.eta0 },
            fld = { srt: SRT -> srt.nsamples}
        )

        /*
        repeat(reps) {
            x.shuffle(Random)
            for (eta in etal) {
                for (d in dl) {
                    val mart = alpha_mart(x, N, mu=1/2, eta=eta, u=u,
                                        estim=lambda x, N, mu, eta, u: shrink_trunc(x,N,mu,eta,u,c=c,d=d))
                }
            }
        }

         */

        //
        //for eta in etal:
        //    for d in dl:
        //        al[eta][d] = al[eta][d]/reps

        // print('\\begin{table} \n\\begin{tabular}{r|rr} \n \eta & d=100 & d=1,000 & d=10,000 & d=100,000\\\\ \\hline')
        //for eta in etal:
        //    print(f''' {eta :.3f} {" ".join(([f"& {al[eta][d] :,.0f} " for d in dl]))} \\\\''')

        // expected - python output
        //   eta      10,     100,   1000,  10000, 100000,
        // 0.900,   9892,    9885,   9752,    598,    558  \\
        // 1.000,   9892,    9882,    566,    349,    338  \\
        // 1.009,   9892,    9882,    519,    336,    326  \\
        // 2.000,   9889,    9846,    325,    325,    325  \\
        // 2.018,   9889,    9846,    325,    325,    325  \\

        // fix bug in python code

        //         10,    100,   1000,  10000, 100000,
        // 0.900 & 7225  & 3000  & 506  & 420  & 413  \\
        // 1.000 & 7161  & 1899  & 391  & 337  & 332  \\
        // 1.009 & 7155  & 1824  & 383  & 331  & 326  \\
        // 2.000 & 5904  & 357   & 325  & 325  & 325  \\
        // 2.018 & 5870  & 355   & 325  & 325  & 325  \\

        // ours
        //  nsamples, ballot comparison, theta = 0.51, assortTheta=0.509, N=10000, error-free
        // d (col) vs eta0 (row)
        //       :    10,    100,   1000,  10000, 100000,
        // 0.900,   7499,   3001,    507,    421,    414,
        // 1.000,   7387,   1900,    392,    338,    333,
        // 1.009,   7377,   1825,    384,    332,    327,
        // 2.000,   5514,    358,    326,    326,    326,
        // 2.018,   5457,    356,    326,    326,    326,
    }

    // does significantly better....
    //       ,  0.510,
    // 0.900,   2347,
    // 1.000,   1461,
    // 1.009,   1403,
    // 2.500,    294,
    // 5.000,    294,
    // 7.500,    294,
    //10.000,    294,
    //15.000,    294,
    //20.000,    294,

    //  nsamples, ballot comparison, assortTheta=0.509, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   7549,   2347,    480,    227,    144,    104,     60,     42,     25,     18,
    // 1.000,   6468,   1461,    324,    163,    106,     78,     47,     33,     21,     15,
    // 1.009,   6354,   1403,    314,    159,    104,     77,     46,     32,     20,     14,
    // 2.500,    710,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 5.000,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 7.500,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //10.000,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //15.000,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //20.000,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,

    //  nsamples, ballot comparison, N=10000, d = 100, error-free
    // theta (col) vs eta0 (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    // 0.900,   9955,   9766,   9336,   8571,   7461,   2257,    464,    221,    140,    101,     59,     41,     25,     18,
    // 1.000,   9951,   9718,   9115,   7957,   6336,   1400,    314,    159,    104,     77,     46,     32,     20,     14,
    // 1.500,   9916,   9014,   5954,   3189,   1827,    418,    153,     98,     74,     59,     39,     29,     19,     14,
    // 2.000,   9825,   6722,   2923,   1498,    937,    309,    148,     98,     74,     59,     39,     29,     19,     14,
    // 5.000,   5173,   1620,    962,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 7.500,   3310,   1393,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //10.000,   2765,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //15.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    //20.000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,

    @Test
    fun comparisonNvsTheta() {
        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 1
        // val eta0 = 1.0

        val info = ContestInfo(
            "contest0",
            0,
            listToMap("A", "B"),
            choiceFunction = SocialChoiceFunction.PLURALITY
        )
        val contest = makeFakeContest(info, 100)

        val srs = mutableListOf<SRT>()
        for (theta in thetas) {
            for (N in nlist) {
                val cvrs = makeCvrsByExactMean(N, theta)
                val contest = makeContestsFromCvrs(cvrs).first()
                val contestUA = ContestUnderAudit(contest).makeClcaAssertionsFromReportedMargin()
                val assorter = contestUA.minClcaAssertion()!!.cassorter

                val margin = assorter.assorter().reportedMargin()
                val compareUpper = 2.0/(2-margin)
                val drawSample = makeClcaNoErrorSampler(contest.id, true, cvrs, assorter)
                val etaActual =  assorter.noerror()
                val etaExpect =  1.0/(2-margin)
                val same = doubleIsClose(etaActual, etaExpect)
                // println(" theta=$theta N=$N etaActual=$etaActual same=$same ")

                val mart: RunTestRepeatedResult = runAlphaMartRepeated(
                    drawSample = drawSample,
                    // maxSamples = N,
                    eta0 = compareUpper - eps,
                    d = d,
                    ntrials = ntrials,
                    upperBound = compareUpper,
                )
                srs.add(mart.makeSRT(theta, 0.0))
            }
        }

        val title = " nsamples, ballot comparison, eta0=compareUpper, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(srs, title, true, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.Nc.toDouble() },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val titlePct = " pct nsamples, ballot comparison, eta0=compareUpper, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(srs, titlePct, false, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.Nc.toDouble() },
            fld = { srt: SRT -> 100.0 * srt.nsamples / srt.Nc }
        )
    }

    //  nsamples, ballot comparison, eta0=etaExpect, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,      0,      0,    998,    995,    992,    968,    883,    769,    652,    545,    347,    230,    117,     69,
    //  5000,   4992,   4967,   4926,   4870,   4800,   4285,   2998,   1998,   1362,    966,    481,    282,    129,     73,
    // 10000,   9967,   9869,   9709,   9493,   9230,   7498,   4282,   2497,   1576,   1069,    505,    290,    130,     73,
    // 20000,  19868,  19480,  18867,  18070,  17140,  11993,   5449,   2853,   1711,   1130,    518,    294,    131,     74,
    // 50000,  49180,  46871,  43471,  39462,  35280,  18733,   6513,   3120,   1804,   1169,    526,    297,    132,     74,
    //
    // pct nsamples, ballot comparison, eta0=etaExpect, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,   0.00,   0.00,  99.80,  99.50,  99.20,  96.80,  88.30,  76.90,  65.20,  54.50,  34.70,  23.00,  11.70,   6.90,
    //  5000,  99.84,  99.34,  98.52,  97.40,  96.00,  85.70,  59.96,  39.96,  27.24,  19.32,   9.62,   5.64,   2.58,   1.46,
    // 10000,  99.67,  98.69,  97.09,  94.93,  92.30,  74.98,  42.82,  24.97,  15.76,  10.69,   5.05,   2.90,   1.30,   0.73,
    // 20000,  99.34,  97.40,  94.34,  90.35,  85.70,  59.97,  27.25,  14.27,   8.56,   5.65,   2.59,   1.47,   0.66,   0.37,
    // 50000,  98.36,  93.74,  86.94,  78.92,  70.56,  37.47,  13.03,   6.24,   3.61,   2.34,   1.05,   0.59,   0.26,   0.15,

    // nsamples, ballot comparison, eta0=compareUpper, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,    960,    792,    645,    537,    458,    261,    139,     95,     71,     57,     38,     29,     19,     14,
    //  5000,   2462,   1371,    943,    718,    579,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 10000,   2907,   1485,    994,    746,    597,    298,    149,     99,     74,     59,     39,     29,     19,     14,
    // 20000,   3178,   1548,   1021,    761,    607,    301,    149,     99,     74,     59,     39,     29,     19,     14,
    // 50000,   3360,   1587,   1037,    770,    612,    302,    150,     99,     74,     59,     39,     29,     19,     14,
    //
    // pct nsamples, ballot comparison, eta0=compareUpper, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,  96.00,  79.20,  64.50,  53.70,  45.80,  26.10,  13.90,   9.50,   7.10,   5.70,   3.80,   2.90,   1.90,   1.40,
    //  5000,  49.24,  27.42,  18.86,  14.36,  11.58,   5.88,   2.96,   1.96,   1.48,   1.18,   0.78,   0.58,   0.38,   0.28,
    // 10000,  29.07,  14.85,   9.94,   7.46,   5.97,   2.98,   1.49,   0.99,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14,
    // 20000,  15.89,   7.74,   5.11,   3.81,   3.04,   1.51,   0.75,   0.50,   0.37,   0.30,   0.20,   0.15,   0.10,   0.07,
    // 50000,   6.72,   3.17,   2.07,   1.54,   1.22,   0.60,   0.30,   0.20,   0.15,   0.12,   0.08,   0.06,   0.04,   0.03,

    @Test
    fun testNvsThetaFactors() {
        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val theta = .510
        val N = 10000
        val factors = listOf(1.0, 2.0)

        val d = 10000
        val ntrials = 100

        val info = ContestInfo("contest0", 0, listToMap("A","B"), choiceFunction = SocialChoiceFunction.PLURALITY)

        for (factor in factors) {
            val srs = mutableListOf<SRT>()
            val cvrs = makeCvrsByExactMean(N, theta)
            val contestUA = makeContestUAfromCvrs(info, cvrs).makeClcaAssertionsFromReportedMargin()
            val compareAssorter = contestUA.minClcaAssertion()!!.cassorter
            val margin = compareAssorter.assorter().reportedMargin()
            val drawSample = makeClcaNoErrorSampler(info.id, true, cvrs, compareAssorter)
            val etaActual = compareAssorter.noerror()
            val eta0 = factor / (2 - margin)
            println(" theta=$theta N=$N etaActual=$etaActual eta0=$eta0 ")

            val mart: RunTestRepeatedResult = runAlphaMartRepeated(
                drawSample = drawSample,
                // maxSamples = N,
                eta0 = eta0,
                d = d,
                ntrials = ntrials,
                upperBound = compareAssorter.upperBound(),
            )
            srs.add(mart.makeSRT(theta, 0.0))

            val title = " nsamples, ballot comparison, eta0=eta0, d = $d, error-free\n theta (col) vs N (row)"
            plotSRS(srs, title, true, colf = "%6.3f", rowf = "%6.0f",
                colFld = { srt: SRT -> srt.reportedMean },
                rowFld = { srt: SRT -> srt.Nc.toDouble() },
                fld = { srt: SRT -> srt.nsamples }
            )

            val titlePct =
                " pct nsamples, ballot comparison, eta0=eta0, d = $d, error-free\n theta (col) vs N (row)"
            plotSRS(srs, titlePct, false, colf = "%6.3f", rowf = "%6.0f",
                colFld = { srt: SRT -> srt.reportedMean },
                rowFld = { srt: SRT -> srt.Nc.toDouble() },
                fld = { srt: SRT -> 100.0 * srt.nsamples / srt.Nc }
            )
        }

        //  theta=0.51 N=10000 etaActual=0.5050505050504227 eta0=0.5050505050505051
        // nsamples, ballot comparison, eta0=eta0, d = 10000, error-free
        // theta (col) vs N (row)
        //      ,  0.510,
        // 10000,   7498,
        //
        // pct nsamples, ballot comparison, eta0=eta0, d = 10000, error-free
        // theta (col) vs N (row)
        //      ,  0.510,
        // 10000,  74.98,
        //
        // theta=0.51 N=10000 etaActual=0.5050505050504227 eta0=1.0101010101010102
        // nsamples, ballot comparison, eta0=eta0, d = 10000, error-free
        // theta (col) vs N (row)
        //      ,  0.510,
        // 10000,    298,
        //
        // pct nsamples, ballot comparison, eta0=eta0, d = 10000, error-free
        // theta (col) vs N (row)
        //      ,  0.510,
        // 10000,   2.98,
    }
}