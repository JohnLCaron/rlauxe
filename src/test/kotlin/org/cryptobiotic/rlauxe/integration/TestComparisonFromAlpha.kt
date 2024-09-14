package org.cryptobiotic.rlauxe.integration

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.ComparisonNoErrors
import org.cryptobiotic.rlauxe.core.SampleFromArrayWithoutReplacement
import org.cryptobiotic.rlauxe.core.makeComparisonAudit
import org.cryptobiotic.rlauxe.core.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.doubleIsClose
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.plots.SRT
import org.cryptobiotic.rlauxe.plots.plotSRS
import org.cryptobiotic.rlauxe.plots.makeSRT
import kotlin.test.Test
import kotlin.test.assertEquals

// from https://github.com/pbstark/alpha/blob/main/Code/alpha.ipynb
class TestComparisonFromAlpha {

    // see # set up simulations
    fun setupSimulations() {
        // # set up simulations
        //# first set: uniform mixed with a pointmass at 1
        //
        //reps = int(10**4)
        //alpha = 0.05
        //mixtures = [.99, .9, .75, .5, .25, .1, .01]  # mass at 1
        //zero_mass = [0, 0.001] # mass at 0
        val reps = 10000
        val alpha = .05
        val mixtures = listOf(.99, .9, .75, .5, .25, .1, .01) // mass at 1
        val zero_mass = listOf(0, 0.001) // mass at 0

        //
        //al = {}  # alpha martingale
        //kw = {}  # Kaplan-Wald
        //kk = {}  # Kaplan-Kolmogorov
        //apk = {} # a priori Kelly
        //sqk = {} # square Kelly
        //thetas = {}
        //
        //methods = [al, kw, kk, apk, sqk, thetas]
        //
        //g_kol = [0.01, 0.1, 0.2]  # for the Kaplan-Kolmogorov method
        //g_wald = 1 - np.array(g_kol) # for Kaplan-Wald method
        val g_kol = listOf(0.01, 0.1, 0.2) // for the Kaplan-Kolmogorov method
        val g_wald = g_kol.map { 1.0 - it } // or Kaplan-Wald method

        //
        //D = 10 # for APK
        //beta = 1
        //dl = [10, 100]        # for alpha
        //c_base = 0.5          # for alpha. larger c since there is no particular expectation about error rates
        //etal = [.99, .9, .75, .55]
        //Nl = [10000, 100000, 500000]
        val D = 10
        val beta = 1
        val dl = listOf(10, 100) // for alpha
        val c_base = 0.5 // for alpha. larger c since there is no particular expectation about error rates
        val etal = listOf(.99, .9, .75, .55)
        val Nl = listOf(0, 100)

        //
        //
        //for m in mixtures:
        //    for meth in methods:
        //        meth[m] = {}
        //        for N in Nl:
        //            meth[m][N] = {}
        //
        //zm = zero_mass[1]

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
    }

    // See ## Simulation of a comparison audit
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
        val u_b = 1
        val assorter_mean = (9000*theta + 1000*.5)/N // contest has 51% for winner in 9000 valid votes, and 1000 non-votes
        val assorter_margin = 2*assorter_mean - 1

        //reps = int(10**2)
        //
        //u = 2/(2-assorter_margin)
        //dl = [10, 100, 1000, 10000, 100000]
        //c = 1/2
        //etal = [0.9, 1, u, 2, 2*u]
        //al = {}
        val reps = 100
        val u = 2.0/(2-assorter_margin)
        assertEquals(1.009081735, u, doublePrecision)
        val dl = listOf(10, 100, 1000, 10000)
        val etal = listOf(0.9, 1.0, u, 2.0, 2.0 * u) // should be .9, 1, 1.009, 2, 2.018

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
                val mart: AlphaMartRepeatedResult = runAlphaMartRepeated(
                    drawSample = SampleFromArrayWithoutReplacement(x),
                    maxSamples = N,
                    eta0 = eta,
                    d = d,
                    ntrials = reps,
                    upperBound = u,
                )
                srs.add(makeSRT(N, theta, 0.0, d, rr=mart))
            }
        }

        val title = " nsamples, ballot comparison, theta = $theta, assortTheta=$assorter_mean, N=$N, error-free\n d (col) vs eta0Factor (row)"
        plotSRS(srs, title, true,
            colFld = { srt: SRT -> srt.d.toDouble() },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
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
        // \begin{table}
        //\begin{tabular}{r|rr}
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

        // actual
        // nsamples, ballot comparison, theta = 0.51, assortTheta=0.509, N=10000, error-free
        // d (col) vs eta0 (row)
        //      ,     10,    100,   1000,  10000, 100000,
        // 0.900,   7459,   2892,    496,    414,    407,
        // 1.000,   7344,   1821,    384,    332,    327,
        // 1.009,   7333,   1749,    376,    327,    326,
        // 2.000,   5414,    355,    326,    326,    326,
        // 2.018,   5355,    353,    326,    326,    326,

        //  nsamples, ballot comparison, theta = 0.51, assortTheta=0.509, N=10000, error-free
        // d (col) vs eta0 (row)
        //      ,     10,    100,   1000,  10000,
        // 0.900,   7499,   3001,    507,    421,
        // 1.000,   7387,   1900,    392,    338,
        // 1.009,   7377,   1825,    384,    332,
        // 2.500,   3823,    328,    326,    326,
        // 5.000,   1006,    326,    326,    326,
        // 7.500,    539,    326,    326,    326,
        //10.000,    400,    326,    326,    326,
        //15.000,    331,    326,    326,    326,
        //20.000,    326,    326,    326,    326,
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

    // replicate same result using ComparisonAssertion
    @Test
    fun comparisonReplication() {
        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val dl = listOf(10, 100, 1000, 10000)

        val d = 100
        val N = 10000
        val reps = 100

        /* what to make of this ??
        val assorter_mean = (9000 * thetas.last() + 1000 * .5) / N // contest has 51% for winner in 9000 valid votes, and 1000 non-votes
        val assorter_margin = 2 * assorter_mean - 1
        val u = 2.0 / (2 - assorter_margin) // use this as the upper bound for comparisons?
        assertEquals(1.009081735, u, doublePrecision)
         */

        val etas = listOf(0.9, 1.0, 1.5, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0) // should be .9, 1, 1.009, 2, 2.018

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        val srs = mutableListOf<SRT>()
        for (theta in thetas) {
            val cvrs = makeCvrsByExactMean(N, theta)
            val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
            val compareAssertions = compareAudit.assertions[contest]
            require(compareAssertions!!.size == 1)
            val compareAssertion = compareAssertions.first()
            val compareAssorter = compareAssertion.assorter
            println("compareAssertion.upperBound() = ${compareAssorter.upperBound()}")

            for (eta in etas) {
                val mart: AlphaMartRepeatedResult = runAlphaMartRepeated(
                    drawSample = ComparisonNoErrors(cvrs, compareAssertion.assorter),
                    maxSamples = N,
                    eta0 = eta,
                    d = d,
                    ntrials = reps,
                    upperBound = compareAssorter.upperBound(),
                )
                srs.add(makeSRT(N, theta, 0.0, d, rr=mart))
            }
        }

        val title = " nsamples, ballot comparison, N=$N, d = $d, error-free\n theta (col) vs eta0Factor (row)"
        plotSRS(srs, title, true, colf = "%6.3f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.eta0Factor },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )
    }

    @Test
    fun comparisonNvsTheta() {
        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)

        val d = 10000
        val ntrials = 100
        // val eta0 = 1.0

        /* what to make of this ??
        val assorter_mean = (9000 * thetas.last() + 1000 * .5) / N // contest has 51% for winner in 9000 valid votes, and 1000 non-votes
        val assorter_margin = 2 * assorter_mean - 1
        val u = 2.0 / (2 - assorter_margin) // use this as the upper bound for comparisons?
        assertEquals(1.009081735, u, doublePrecision)
         */
        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        val srs = mutableListOf<SRT>()
        for (theta in thetas) {
            for (N in nlist) {
                val cvrs = makeCvrsByExactMean(N, theta)
                val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
                val compareAssertion = compareAudit.assertions[contest]!!.first()

                val margin = compareAssertion.assorter.margin
                val compareUpper = 2.0/(2-margin) // TODO does this matter ? doesnt seem to
                val drawSample = ComparisonNoErrors(cvrs, compareAssertion.assorter)
                val etaActual = drawSample.truePopulationMean()
                val etaExpect =  1.0/(2-margin)
                val same = doubleIsClose(etaActual, etaExpect, doublePrecision)
                // println(" theta=$theta N=$N etaActual=$etaActual same=$same ")

                val mart: AlphaMartRepeatedResult = runAlphaMartRepeated(
                    drawSample = drawSample,
                    maxSamples = N,
                    eta0 = etaExpect,
                    d = d,
                    ntrials = ntrials,
                    upperBound = compareUpper,
                )
                srs.add(makeSRT(N, theta, 0.0, d, rr=mart))
            }
        }

        val title = " nsamples, ballot comparison, eta0=etaExpect, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(srs, title, true, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> srt.nsamples.toDouble() }
        )

        val titlePct = " pct nsamples, ballot comparison, eta0=etaExpect, d = $d, error-free\n theta (col) vs N (row)"
        plotSRS(srs, titlePct, false, colf = "%6.3f", rowf = "%6.0f",
            colFld = { srt: SRT -> srt.reportedMean },
            rowFld = { srt: SRT -> srt.N.toDouble() },
            fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
        )
    }
    //  nsamples, ballot comparison, eta0=20.0, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,    951,    777,    631,    527,    450,    258,    138,     94,     71,     57,     38,     29,     19,     14,
    //  5000,   2253,   1294,    904,    695,    564,    290,    147,     98,     73,     59,     39,     29,     19,     14,
    // 10000,   2588,   1390,    949,    721,    581,    294,    148,     98,     74,     59,     39,     29,     19,     14,
    // 20000,   2781,   1442,    973,    734,    589,    296,    148,     99,     74,     59,     39,     29,     19,     14,
    // 50000,   2907,   1475,    988,    742,    595,    298,    149,     99,     74,     59,     39,     29,     19,     14,
    //
    // pct nsamples, ballot comparison, eta0=20.0, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,  95.10,  77.70,  63.10,  52.70,  45.00,  25.80,  13.80,   9.40,   7.10,   5.70,   3.80,   2.90,   1.90,   1.40,
    //  5000,  45.06,  25.88,  18.08,  13.90,  11.28,   5.80,   2.94,   1.96,   1.46,   1.18,   0.78,   0.58,   0.38,   0.28,
    // 10000,  25.88,  13.90,   9.49,   7.21,   5.81,   2.94,   1.48,   0.98,   0.74,   0.59,   0.39,   0.29,   0.19,   0.14,
    // 20000,  13.91,   7.21,   4.87,   3.67,   2.95,   1.48,   0.74,   0.50,   0.37,   0.30,   0.20,   0.15,   0.10,   0.07,
    // 50000,   5.81,   2.95,   1.98,   1.48,   1.19,   0.60,   0.30,   0.20,   0.15,   0.12,   0.08,   0.06,   0.04,   0.03,

    //
    // eta0=1.0, d = 100
    // nsamples, ballot comparison, eta0=1.0, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,   1000,    998,    992,    980,    956,    668,    274,    157,    108,     83,     53,     39,     27,     21,
    //  5000,   4988,   4930,   4775,   4453,   3925,   1297,    328,    172,    115,     86,     54,     40,     27,     21,
    // 10000,   9951,   9720,   9127,   7994,   6410,   1468,    336,    174,    116,     87,     54,     40,     27,     21,
    // 20000,  19804,  18904,  16766,  13274,   9377,   1571,    341,    175,    116,     87,     54,     40,     27,     21,
    // 50000,  48786,  43654,  33681,  21985,  12982,   1640,    343,    175,    116,     87,     54,     40,     27,     21,
    //
    // pct nsamples, ballot comparison, eta0=1.0, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000, 100.00,  99.80,  99.20,  98.00,  95.60,  66.80,  27.40,  15.70,  10.80,   8.30,   5.30,   3.90,   2.70,   2.10,
    //  5000,  99.76,  98.60,  95.50,  89.06,  78.50,  25.94,   6.56,   3.44,   2.30,   1.72,   1.08,   0.80,   0.54,   0.42,
    // 10000,  99.51,  97.20,  91.27,  79.94,  64.10,  14.68,   3.36,   1.74,   1.16,   0.87,   0.54,   0.40,   0.27,   0.21,
    // 20000,  99.02,  94.52,  83.83,  66.37,  46.89,   7.86,   1.71,   0.88,   0.58,   0.44,   0.27,   0.20,   0.14,   0.11,
    // 50000,  97.57,  87.31,  67.36,  43.97,  25.96,   3.28,   0.69,   0.35,   0.23,   0.17,   0.11,   0.08,   0.05,   0.04,
    //
    // eta0=1.0, d = 1000
    // nsamples, ballot comparison, eta0=1.0, d = 1000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,    995,    904,    761,    636,    538,    295,    153,    104,     79,     65,     45,     35,     25,     20,
    //  5000,   4259,   2279,   1394,    980,    751,    341,    164,    109,     82,     66,     46,     35,     25,     20,
    // 10000,   6986,   2758,   1541,   1046,    787,    348,    165,    110,     83,     67,     46,     35,     25,     20,
    // 20000,  10205,   3076,   1626,   1082,    806,    351,    166,    110,     83,     67,     46,     35,     25,     20,
    // 50000,  14057,   3301,   1681,   1105,    818,    353,    167,    110,     83,     67,     46,     35,     25,     20,
    //
    // pct nsamples, ballot comparison, eta0=1.0, d = 1000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,  99.50,  90.40,  76.10,  63.60,  53.80,  29.50,  15.30,  10.40,   7.90,   6.50,   4.50,   3.50,   2.50,   2.00,
    //  5000,  85.18,  45.58,  27.88,  19.60,  15.02,   6.82,   3.28,   2.18,   1.64,   1.32,   0.92,   0.70,   0.50,   0.40,
    // 10000,  69.86,  27.58,  15.41,  10.46,   7.87,   3.48,   1.65,   1.10,   0.83,   0.67,   0.46,   0.35,   0.25,   0.20,
    // 20000,  51.03,  15.38,   8.13,   5.41,   4.03,   1.76,   0.83,   0.55,   0.42,   0.34,   0.23,   0.18,   0.13,   0.10,
    // 50000,  28.11,   6.60,   3.36,   2.21,   1.64,   0.71,   0.33,   0.22,   0.17,   0.13,   0.09,   0.07,   0.05,   0.04,
    //
    //
    // eta0=1.0, d = 10000
    //  nsamples, ballot comparison, eta0=1.0, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,    960,    794,    647,    540,    462,    266,    144,    100,     77,     63,     44,     34,     25,     20,
    //  5000,   2466,   1376,    949,    723,    585,    300,    154,    104,     80,     65,     45,     35,     25,     20,
    // 10000,   2913,   1491,   1000,    752,    603,    304,    155,    105,     80,     65,     45,     35,     25,     20,
    // 20000,   3185,   1554,   1027,    767,    613,    307,    155,    105,     80,     65,     45,     35,     25,     20,
    // 50000,   3368,   1594,   1044,    777,    619,    308,    156,    105,     80,     65,     45,     35,     25,     20,
    //
    // pct nsamples, ballot comparison, eta0=1.0, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,  96.00,  79.40,  64.70,  54.00,  46.20,  26.60,  14.40,  10.00,   7.70,   6.30,   4.40,   3.40,   2.50,   2.00,
    //  5000,  49.32,  27.52,  18.98,  14.46,  11.70,   6.00,   3.08,   2.08,   1.60,   1.30,   0.90,   0.70,   0.50,   0.40,
    // 10000,  29.13,  14.91,  10.00,   7.52,   6.03,   3.04,   1.55,   1.05,   0.80,   0.65,   0.45,   0.35,   0.25,   0.20,
    // 20000,  15.93,   7.77,   5.14,   3.84,   3.07,   1.54,   0.78,   0.53,   0.40,   0.33,   0.23,   0.18,   0.13,   0.10,
    // 50000,   6.74,   3.19,   2.09,   1.55,   1.24,   0.62,   0.31,   0.21,   0.16,   0.13,   0.09,   0.07,   0.05,   0.04,
    //
    //  nsamples, ballot comparison, eta0=etaExpect, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,   1000,   1000,    998,    995,    992,    968,    883,    769,    652,    545,    347,    230,    117,     69,
    //  5000,   4992,   4967,   4926,   4870,   4800,   4285,   2998,   1998,   1362,    966,    481,    282,    129,     73,
    // 10000,   9967,   9869,   9709,   9493,   9230,   7498,   4282,   2497,   1576,   1069,    505,    290,    130,     73,
    // 20000,  19868,  19480,  18867,  18070,  17140,  11993,   5449,   2853,   1711,   1130,    518,    294,    131,     74,
    // 50000,  49180,  46871,  43471,  39462,  35280,  18733,   6513,   3120,   1804,   1169,    526,    297,    132,     74,
    //
    // pct nsamples, ballot comparison, eta0=etaExpect, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000, 100.00, 100.00,  99.80,  99.50,  99.20,  96.80,  88.30,  76.90,  65.20,  54.50,  34.70,  23.00,  11.70,   6.90,
    //  5000,  99.84,  99.34,  98.52,  97.40,  96.00,  85.70,  59.96,  39.96,  27.24,  19.32,   9.62,   5.64,   2.58,   1.46,
    // 10000,  99.67,  98.69,  97.09,  94.93,  92.30,  74.98,  42.82,  24.97,  15.76,  10.69,   5.05,   2.90,   1.30,   0.73,
    // 20000,  99.34,  97.40,  94.34,  90.35,  85.70,  59.97,  27.25,  14.27,   8.56,   5.65,   2.59,   1.47,   0.66,   0.37,
    // 50000,  98.36,  93.74,  86.94,  78.92,  70.56,  37.47,  13.03,   6.24,   3.61,   2.34,   1.05,   0.59,   0.26,   0.15,
    //
    //  nsamples, ballot comparison, eta0=compareUpper, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,   1000,    998,    992,    980,    955,    656,    259,    145,     98,     74,     45,     32,     20,     14,
    //  5000,   4988,   4930,   4773,   4444,   3902,   1247,    307,    157,    103,     76,     46,     32,     20,     15,
    // 10000,   9951,   9719,   9119,   7965,   6346,   1403,    314,    159,    104,     77,     46,     32,     20,     15,
    // 20000,  19804,  18900,  16738,  13190,   9239,   1497,    318,    160,    105,     77,     46,     32,     20,     15,
    // 50000,  48785,  43631,  33569,  21754,  12716,   1560,    320,    160,    105,     77,     46,     33,     20,     15,
    //
    // pct nsamples, ballot comparison, eta0=compareUpper, d = 100, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000, 100.00,  99.80,  99.20,  98.00,  95.50,  65.60,  25.90,  14.50,   9.80,   7.40,   4.50,   3.20,   2.00,   1.40,
    //  5000,  99.76,  98.60,  95.46,  88.88,  78.04,  24.94,   6.14,   3.14,   2.06,   1.52,   0.92,   0.64,   0.40,   0.30,
    // 10000,  99.51,  97.19,  91.19,  79.65,  63.46,  14.03,   3.14,   1.59,   1.04,   0.77,   0.46,   0.32,   0.20,   0.15,
    // 20000,  99.02,  94.50,  83.69,  65.95,  46.20,   7.49,   1.59,   0.80,   0.53,   0.39,   0.23,   0.16,   0.10,   0.08,
    // 50000,  97.57,  87.26,  67.14,  43.51,  25.43,   3.12,   0.64,   0.32,   0.21,   0.15,   0.09,   0.07,   0.04,   0.03,
    //
    //  nsamples, ballot comparison, eta0=compareUpper, d = 1000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,    994,    903,    759,    632,    534,    289,    147,     98,     74,     59,     39,     29,     19,     14,
    //  5000,   4255,   2268,   1384,    971,    742,    334,    157,    102,     76,     60,     39,     29,     19,     14,
    // 10000,   6972,   2742,   1529,   1035,    777,    340,    159,    103,     76,     60,     39,     29,     19,     14,
    // 20000,  10173,   3055,   1611,   1070,    796,    343,    159,    103,     76,     60,     40,     29,     19,     14,
    // 50000,  13993,   3278,   1665,   1092,    807,    345,    160,    103,     76,     60,     40,     29,     19,     14,
    //
    // pct nsamples, ballot comparison, eta0=compareUpper, d = 1000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,  99.40,  90.30,  75.90,  63.20,  53.40,  28.90,  14.70,   9.80,   7.40,   5.90,   3.90,   2.90,   1.90,   1.40,
    //  5000,  85.10,  45.36,  27.68,  19.42,  14.84,   6.68,   3.14,   2.04,   1.52,   1.20,   0.78,   0.58,   0.38,   0.28,
    // 10000,  69.72,  27.42,  15.29,  10.35,   7.77,   3.40,   1.59,   1.03,   0.76,   0.60,   0.39,   0.29,   0.19,   0.14,
    // 20000,  50.87,  15.28,   8.06,   5.35,   3.98,   1.72,   0.80,   0.52,   0.38,   0.30,   0.20,   0.15,   0.10,   0.07,
    // 50000,  27.99,   6.56,   3.33,   2.18,   1.61,   0.69,   0.32,   0.21,   0.15,   0.12,   0.08,   0.06,   0.04,   0.03,
    //
    //  nsamples, ballot comparison, eta0=compareUpper, d = 10000, error-free
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
    //
    //  nsamples, ballot comparison, eta0=etaExpect, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000,   1000,   1000,    998,    995,    992,    968,    883,    769,    652,    545,    347,    230,    117,     69,
    //  5000,   4992,   4967,   4926,   4870,   4800,   4285,   2998,   1998,   1362,    966,    481,    282,    129,     73,
    // 10000,   9967,   9869,   9709,   9493,   9230,   7498,   4282,   2497,   1576,   1069,    505,    290,    130,     73,
    // 20000,  19868,  19480,  18867,  18070,  17140,  11993,   5449,   2853,   1711,   1130,    518,    294,    131,     74,
    // 50000,  49180,  46871,  43471,  39462,  35280,  18733,   6513,   3120,   1804,   1169,    526,    297,    132,     74,
    //
    // pct nsamples, ballot comparison, eta0=etaExpect, d = 10000, error-free
    // theta (col) vs N (row)
    //      ,  0.501,  0.502,  0.503,  0.504,  0.505,  0.510,  0.520,  0.530,  0.540,  0.550,  0.575,  0.600,  0.650,  0.700,
    //  1000, 100.00, 100.00,  99.80,  99.50,  99.20,  96.80,  88.30,  76.90,  65.20,  54.50,  34.70,  23.00,  11.70,   6.90,
    //  5000,  99.84,  99.34,  98.52,  97.40,  96.00,  85.70,  59.96,  39.96,  27.24,  19.32,   9.62,   5.64,   2.58,   1.46,
    // 10000,  99.67,  98.69,  97.09,  94.93,  92.30,  74.98,  42.82,  24.97,  15.76,  10.69,   5.05,   2.90,   1.30,   0.73,
    // 20000,  99.34,  97.40,  94.34,  90.35,  85.70,  59.97,  27.25,  14.27,   8.56,   5.65,   2.59,   1.47,   0.66,   0.37,
    // 50000,  98.36,  93.74,  86.94,  78.92,  70.56,  37.47,  13.03,   6.24,   3.61,   2.34,   1.05,   0.59,   0.26,   0.15,

    @Test
    fun testNvsTheta() {
        val thetas = listOf(.501, .502, .503, .504, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val nlist = listOf(50000, 20000, 10000, 5000, 1000)
        val theta = .510
        val N = 10000
        val factors = listOf(1.0, 2.0)

        val d = 10000
        val ntrials = 1

        val contest = AuditContest("contest0", 0, listOf(0, 1), listOf(0))

        for (factor in factors) {
            val srs = mutableListOf<SRT>()
            val cvrs = makeCvrsByExactMean(N, theta)
            val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
            val compareAssertion = compareAudit.assertions[contest]!!.first()

            val margin = compareAssertion.assorter.margin
            val compareUpper = 2.0 / (2 - margin) // TODO does this matter ? doesnt seem to
            val drawSample = ComparisonNoErrors(cvrs, compareAssertion.assorter)
            val etaActual = drawSample.truePopulationMean()
            val eta0 = factor / (2 - margin)
            println(" theta=$theta N=$N etaActual=$etaActual eta0=$eta0 ")

            val mart: AlphaMartRepeatedResult = runAlphaMartRepeated(
                drawSample = drawSample,
                maxSamples = N,
                eta0 = eta0,
                d = d,
                ntrials = ntrials,
                upperBound = compareUpper,
            )
            srs.add(makeSRT(N, theta, 0.0, d, rr=mart))

            val title = " nsamples, ballot comparison, eta0=eta0, d = $d, error-free\n theta (col) vs N (row)"
            plotSRS(srs, title, true, colf = "%6.3f", rowf = "%6.0f",
                colFld = { srt: SRT -> srt.reportedMean },
                rowFld = { srt: SRT -> srt.N.toDouble() },
                fld = { srt: SRT -> srt.nsamples.toDouble() }
            )

            val titlePct =
                " pct nsamples, ballot comparison, eta0=eta0, d = $d, error-free\n theta (col) vs N (row)"
            plotSRS(srs, titlePct, false, colf = "%6.3f", rowf = "%6.0f",
                colFld = { srt: SRT -> srt.reportedMean },
                rowFld = { srt: SRT -> srt.N.toDouble() },
                fld = { srt: SRT -> 100.0 * srt.nsamples / srt.N }
            )
        }
    }
}