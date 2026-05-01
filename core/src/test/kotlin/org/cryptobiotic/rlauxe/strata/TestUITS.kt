package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUITS {

    @Test
    fun pointmass_simulations() { // from UI-TS
        //alt_grid = np.linspace(0.51, 0.75, 20)
        //#alt_grid = [0.505, 0.51, 0.52, 0.53, 0.55, 0.6, 0.65, 0.7, 0.75] // wtf?
        //delta_grid = [0, 0.5]

        // the different "alternate (not null)" etas
        val alt_grid = numpy_linspace(0.51, 0.75, 20)
        val delta_grid = listOf( 0.01, 0.05, 0.5)
        val n_bands_grid = listOf(10, 100, 500)

        val Nk = listOf(1000, 1500)

//
//for alt, delta, method, bet, allocation, n_bands in itertools.product(alt_grid, delta_grid, methods_list, bets_list, allocations_list, n_bands_grid):
        delta_grid.forEach {  delta ->
            alt_grid.forEach {  alt ->
                n_bands_grid.forEach {  n_bands -> // needed ?
                    val A_c = listOf(alt - 0.5*delta, alt + 0.5*delta)
                    val auditor = KPointAuditor(
                        Nk,
                        A_c,
                        n_bands = n_bands,
                        reps = 1,
                        show=false
                    )
                    val result = auditor.runAudit()
                    println("alt: ${df(alt)} +/- ${df(delta)} = A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
                }
                println()
            }
        }
    }

    @Test
    fun test_matrix() {
        var n_bands = 10

        repeat(12) { nwin ->
            repeat(10) { margin ->
                var Nk = listOf(1000, 1000 + nwin * 100)
                val delta = margin * .01
                var A_c = listOf(0.45 - delta, .65 + delta)

                var result = simulate_plurcomp(
                    Nk,
                    A_c,
                    bet = "inverse_eta",
                    selection = "round_robin",
                    inference = "ui-ts",
                    n_bands = n_bands,
                    reps = 1,
                )
                println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
            }
            println()
        }
    }

    @Test
    fun test_matrixK() {
        var n_bands = 10

        repeat(12) { nwin ->
            repeat(10) { margin ->
                var Nk = listOf(1000, 1000 + nwin * 100)
                val delta = margin * .01
                var A_c = listOf(0.45 - delta, .65 + delta)

                val auditor = KPointAuditor(
                    Nk,
                    A_c,
                    n_bands = n_bands,
                    reps = 1,
                    show=false
                )
                val result = auditor.runAudit()
                println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
            }
            println()
        }
    }

    @Test
    fun test_margins() {
        var n_bands = 100

        repeat(10) { margin ->
            var Nk = listOf(1000, 1800)
            val delta = margin * .01
            var A_c = listOf(0.45 - delta, .65 + delta)

            var result = simulate_plurcomp(
                Nk,
                A_c,
                bet = "inverse_eta",
                selection = "round_robin",
                inference = "ui-ts",
                n_bands = n_bands,
                reps = 1,
            )
            println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        }
        println()
    }

    @Test
    fun test_marginsK() {
        var n_bands = 100

        repeat(10) { margin ->
            var Nk = listOf(1000, 1800)
            val delta = margin * .01
            var A_c = listOf(0.45 - delta, .65 + delta)

            val auditor = KPointAuditor(
                Nk,
                A_c,
                n_bands = n_bands,
                reps = 1,
                show=false
            )
            val result = auditor.runAudit()
            println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        }
        println()
    }

    @Test
    fun test_dwin() {
        var n_bands = 10

        repeat(12) { dwin ->
            // repeat(10) { margin ->
                var Nk = listOf(1000, 1000 + dwin * 100)
                var A_c = listOf(0.4, .7)

                var result = simulate_plurcomp(
                    Nk,
                    A_c,
                    bet = "inverse_eta",
                    selection = "round_robin",
                    inference = "ui-ts",
                    n_bands = n_bands,
                    reps = 1,
                )
                println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        }
    }

    @Test
    fun test_dwinK() {
        var n_bands = 10

        repeat(12) { dwin ->
            // repeat(10) { margin ->
            var Nk = listOf(1000, 1000 + dwin * 100)
            var A_c = listOf(0.4, .7)

            val auditor = KPointAuditor(
                Nk,
                A_c,
                n_bands = n_bands,
                reps = 1,
                show=false
            )
            val result = auditor.runAudit()
            println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        }
    }

    @Test
    fun test_simulate_plurcomp() {
        var n_bands = 10
        var Nk = listOf(100, 100)
        var A_c = listOf(0.4, 0.7)

        var result = simulate_plurcomp(
            Nk,
            A_c,
            bet = "inverse_eta",
            selection = "round_robin",
            inference = "ui-ts",
            n_bands = n_bands,
            reps = 1,
        )
        println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        // Nk: [100, 100], A_c: [0.4, 0.7] n_bands=10 reps=1 result=(np.float64(94.0), np.float64(94.0))
    }

    @Test
    fun testBandedAuditor() {
        var n_bands = 6

        var Nk = listOf(100, 100)
        var A_c = listOf(0.4, 0.7)

        val auditor = BandedAuditor(
            Nk,
            A_c,
            n_bands = n_bands,
            reps = 1,
            show=true
        )
        val result = auditor.runBandAudit()
        println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        assertEquals(97, roundToClosest(result))
    }

    @Test
    fun testBandedAuditor2() {
        var n_bands = 6

        var Nk = listOf(100, 100)
        var A_c = listOf(0.4, 0.7)

        val auditor = BandedAuditor2(
            Nk,
            A_c,
            n_bands = n_bands,
            reps = 1,
            show=true
        )
        val result = auditor.runBandedAudit()
        println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        assertEquals(53, roundToClosest(result)) // TODO is there a factor of 2 im missing?
    }

    @Test
    fun testKPointAuditor() {
        var n_bands = 10

        var Nk = listOf(1200, 1000)
        var A_c = listOf(0.4, 0.7)

        val auditor = KPointAuditor(
            Nk,
            A_c,
            n_bands = n_bands,
            reps = 1,
            show=false
        )
        val result = auditor.runAudit()
        println("Nk: ${Nk}, A_c: [${show(A_c)}] n_bands: $n_bands  result: ${result}")
        // assertEquals(51, roundToClosest(result)) // TODO is there a factor of 2 im missing?
    }

    @Test
    fun testObjectiveAuditor() {
        var n_bands = 10

        var Nk = listOf(1000, 1700)
        var A_c = listOf(0.4, 0.7)

        val auditor = BOBYQAAuditor(
            Nk,
            A_c,
            reps = 1,
        )
        val result = auditor.runObjectiveOptimizer()
        println("Nk: ${Nk}, A_c: [${show(A_c)}] result: ${result}")
        assertEquals(104, roundToClosest(result))
    }

    @Test
    fun compareAll() {
        var Nk = listOf(200, 200)
        var alt = 0.55
        var delta = 0.2
        var A_c = listOf(alt - 0.5 * delta, alt + 0.5 * delta)
        var n_bands = 10

        compare(Nk, A_c, n_bands = n_bands)

        Nk = listOf(400, 400)
        compare(Nk, A_c, n_bands = n_bands)

        Nk = listOf(220, 200)
        compare(Nk, A_c, n_bands = n_bands)

        Nk = listOf(200, 400)
        compare(Nk, A_c, n_bands = n_bands)

        Nk = listOf(200, 200)
        delta = 0.5
        A_c = listOf(alt - 0.5 * delta, alt + 0.5 * delta)
        compare(Nk, A_c, n_bands = n_bands)

        delta = 0.0
        A_c = listOf(alt - 0.5 * delta, alt + 0.5 * delta)
        compare(Nk, A_c, n_bands = n_bands)

        n_bands = 100
        compare(Nk, A_c, n_bands = n_bands)
    }

    fun compare(
        Nk: List<Int>,   // a length-K list of the size of each stratum
        mu_k: List<Double>, // a length-K np.array of floats the reported assorter mean bar{A}_c in each stratum
        n_bands: Int = 100,
        p_2: DoubleArray =  doubleArrayOf(0.0, 0.0),  // a length-K np.array of floats the true rate of 2 vote overstatements in each stratum, defaults to none
        p_1: DoubleArray =  doubleArrayOf(0.0, 0.0), // a length-K np.array of floats the true rate of 1 vote overstatements in each stratum, defaults to none
    ) {

        val result1 = simulate_plurcomp(
            Nk,
            mu_k,
            bet = "inverse_eta",
            selection = "round_robin",
            inference = "ui-ts",
            n_bands = n_bands,
            reps = 1,
            show = false,
        )

        val auditor = BandedAuditor(
            Nk,
            mu_k,
            n_bands = n_bands,
            reps = 1,
            p_2 = p_2,
            p_1 = p_1,
            show = false,
        )
        val result2 = auditor.runBandAudit()

        println("Nk: ${Nk}, mu_k: [${show(mu_k)}] n_bands: $n_bands  result1: ${result1} result2: ${result2}")
        assertEquals(roundToClosest(result1), roundToClosest(result2))
    }
}

fun show(a: List<Double>) = buildString {
    a.forEach { append("${dfn(it, 3)}, ") }
}

fun showMin(a: List<Double>, minIndex:Int) = buildString {
    a.forEachIndexed { t, it ->
        val star = if (t==minIndex) "**" else ""
        append("${dfn(it, 3)}$star, ")
    }
}