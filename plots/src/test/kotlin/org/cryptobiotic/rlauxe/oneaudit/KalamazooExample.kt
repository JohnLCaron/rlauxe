import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.pow

import kotlin.test.Test

class KalamazooExample {

    // kalamazooReplication in oneaudit oa_polling.ipynb
    @Test
    fun testKalamazooFromPython() {
        // reported results
        val stratum = listOf("CVR", "polling")
        val stratumSizes = listOf(5294, 22372) // CVR, polling
        val N = stratumSizes[0] + stratumSizes[1]
        val candidates = mapOf(
            "Schuette" to listOf(1349, 4220),
            "Whitmer" to listOf(3765, 16934),
            "Gelineau" to listOf(56, 462),
            "Schleiger" to listOf(19, 116),
            "Kurland" to listOf(23, 284),
            "Butkovich" to listOf(6, 66)
        )
        // wtf?
        val votesAudPoll = mapOf(
            "Butkovich" to 0,
            "Gelineau" to 1,
            "Kurland" to 0,
            "Schleiger" to 0,
            "Schuette" to 8,
            "Whitmer" to 23
        )

        var errors = 0 // CVRs all matched the cards
        val nCvr = 8
        val nPoll = 32

        println("Stratum")
        for (s in 0..1) {
            val vs = candidates.values.sumOf { it[s] }
            println("  ${stratum[s]} has $vs votes out of ${stratumSizes[s]} = ${stratumSizes[s] - vs} undervotes/phantoms")
        }
        println()

        //%%
        val assorterMeanCvr = (candidates["Whitmer"]!![0] - candidates["Schuette"]!![0]).toDouble() / stratumSizes[0]
        val assorterMeanPoll = (candidates["Whitmer"]!![1] - candidates["Schuette"]!![1]).toDouble() / stratumSizes[1]
        val whitmer = candidates["Whitmer"]!![0] + candidates["Whitmer"]!![1]
        val schuette = candidates["Schuette"]!![0] + candidates["Schuette"]!![1]
        val assorterMeanAll = (whitmer - schuette).toDouble() / N
        println("whitmer=$whitmer, schuette=$schuette, assorterMean cvr=${df(assorterMeanCvr)}, poll=${df(assorterMeanPoll)}, all=${df(assorterMeanAll)}")

        val u = 1.0
        val v = 2 * assorterMeanAll - 1
        val uB = 2 * u / (2 * u - v) // upper bound on the overstatement assorter
        val noerror = u / (2 * u - v) // alternative value when cvr == mvr
        println("margin=${df(v)}, uB=${df(uB)}, noerror=${df(noerror)}")

        val sam = DoubleArray(nCvr) { noerror } +
                DoubleArray(votesAudPoll.filterKeys { it in listOf("Butkovich", "Gelineau", "Kurland", "Schleiger") }
                    .values.sum()) { 1.0 / 2 } +
                DoubleArray(votesAudPoll["Schuette"]!!) { (1 - assorterMeanPoll) / (2 - v) } +
                DoubleArray(votesAudPoll["Whitmer"]!!) { (2 - assorterMeanPoll) / (2 - v) }
        println("sam=${sam.joinToString(",")}")
        println()

        //%%
        val reps = 1000 // 10.0.pow(5).toInt()
        val alpha = 0.05
        val pv = mutableListOf<Double>()

        repeat(reps) {
            sam.shuffle()
            val mart = sprtMart(sam, N = N, mu = 1.0 / 2, eta = 0.99 * uB, u = uB, randomOrder = true)
            val found = minOf(1 / mart.maxOrNull()!!, 1.0)
            pv.add(found)
        }

        println("reps $reps mean p: ${pv.average()}; sd p: ${pv.standardDeviation()}, 90th percentile p: ${pv.percentile(90)}, fraction of SUITE: ${(pv.average()) / .0374}")
        println()
        //%%
        val eta = noerror
        val c = (eta - 1 / 2) / 2
        val d = 2
        sam.shuffle()
        val martSprt = sprtMart(sam, N = N, mu = 1.0 / 2, eta = eta, u = uB, randomOrder = true)
        println("martSprt=${martSprt.contentToString()}")
        println("KK=${sam.cumulativeProduct().contentToString()}")
        val martAlpha = alphaMart(
            sam, N = N, mu = 1.0 / 2, eta = eta, u = uB,
            estim = { _, _, _, _, u -> DoubleArray(N) { 0.99 * u } }
        )
        println("martAlpha=${martAlpha.contentToString()}")
        //%%
        println(pv.average() / .0374)
    }

    // Helper extension functions
    fun List<Double>.standardDeviation(): Double {
        val mean = this.average()
        return kotlin.math.sqrt(this.sumOf { (it - mean).pow(2) } / (this.size - 1))
    }

    fun List<Double>.percentile(percentile: Int): Double {
        return this.sorted()[percentile * this.size / 100]
    }

    fun DoubleArray.cumulativeProduct(): DoubleArray {
        val result = DoubleArray(this.size)
        var product = 1.0
        this.forEachIndexed { index, value ->
            product *= value
            result[index] = product
        }
        return result
    }

    // redo Kalamazoo using our own framework
    // The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest
    // the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.
    // The sample included 32 cards were drawn from the no-CVR stratum and 8 from the CVR stratum.
    @Test
    fun testKalamazoo() {
        // the candidates
        val info = ContestInfo("Kalamazoo", 0, mapOf(
                "Butkovich" to 0,
                "Gelineau" to 1,
                "Kurland" to 2,
                "Schleiger" to 3,
                "Schuette" to 4,
                "Whitmer" to 5,
            ),
            SocialChoiceFunction.PLURALITY,
            nwinners = 1,
        )

        // reported results for the two strata
        val candidates = mapOf(     // candidateName -> [votes(cvr), votes(nocvr)]
            "Schuette" to listOf(1349, 4220),
            "Whitmer" to listOf(3765, 16934),
            "Gelineau" to listOf(56, 462),
            "Schleiger" to listOf(19, 116),
            "Kurland" to listOf(23, 284),
            "Butkovich" to listOf(6, 66)
        )

        // The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest
        // the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.
        val stratumNames = listOf("CVR", "noCvr")
        val stratumSizes = listOf(5294, 22372) // CVR, noCvr
        val Nc = stratumSizes[0] + stratumSizes[1]


        val contest = Contest.makeWithCandidateNames( info, mapOf(
                    "Butkovich" to 6,
                    "Gelineau" to 56,
                    "Kurland" to 23,
                    "Schleiger" to 19,
                    "Schuette" to 1349,
                    "Whitmer" to 3765,
                ),
                Nc = Nc,
                Np = 0, // assume all undervotes I guess
            )

        // The sample included 32 cards were drawn from the no-CVR stratum and 8 from the CVR stratum.


        // these are the SUITE sampling amounts.
        // diluted margin: 54.69%, estSampleSize = 40; bit of a softball, eh mate?
        // "The sample included 32 cards were drawn from the no-CVR stratum and 8 from the CVR stratum."
        // see https://github.com/kellieotto/mirla18/blob/master/code/kalamazoo_SUITE.ipynb
        val votesAudPoll = mapOf(
            "Butkovich" to 0,
            "Gelineau" to 1,
            "Kurland" to 0,
            "Schleiger" to 0,
            "Schuette" to 8,
            "Whitmer" to 23
        )
        var errors = 0 // CVRs all matched the cards
        val nCvr = 8
        val nPoll = 32

        println("Stratum")
        for (s in 0..1) {
            val vs = candidates.values.sumOf { it[s] }
            println("  ${stratumNames[s]} has $vs votes out of ${stratumSizes[s]} = ${stratumSizes[s] - vs} undervotes/phantoms")
        }
        println()

        //
        val assorterMeanCvr = (candidates["Whitmer"]!![0] - candidates["Schuette"]!![0]).toDouble() / stratumSizes[0]
        val assorterMeanPoll = (candidates["Whitmer"]!![1] - candidates["Schuette"]!![1]).toDouble() / stratumSizes[1]
        val whitmer = candidates["Whitmer"]!![0] + candidates["Whitmer"]!![1]
        val schuette = candidates["Schuette"]!![0] + candidates["Schuette"]!![1]
        val assorterMeanAll = (whitmer - schuette).toDouble() / Nc
        println("whitmer=$whitmer, schuette=$schuette, assorterMean cvr=${df(assorterMeanCvr)}, poll=${df(assorterMeanPoll)}, all=${df(assorterMeanAll)}")

        val u = 1.0
        val v = 2 * assorterMeanAll - 1
        val uB = 2 * u / (2 * u - v) // upper bound on the overstatement assorter
        val noerror = u / (2 * u - v) // alternative value when cvr == mvr
        println("margin=${df(v)}, uB=${df(uB)}, noerror=${df(noerror)}")

        // assort values for "assert that Whitmer is winner and Schuette is loser"
        val sam = DoubleArray(nCvr) { noerror } +  // assume no errors on the cvr, the assorter value = noerror
                DoubleArray(
                    votesAudPoll.filterKeys { it in listOf("Butkovich", "Gelineau", "Kurland", "Schleiger") }
                    .values.sum()) { 1.0 / 2 } +
                DoubleArray(votesAudPoll["Schuette"]!!) { (1 - assorterMeanPoll) / (2 - v) } +
                DoubleArray(votesAudPoll["Whitmer"]!!) { (2 - assorterMeanPoll) / (2 - v) }
        println("sam=${sam.joinToString(",")}")
        println()

        //%%
        val reps = 10 // 10.0.pow(5).toInt()
        val alpha = 0.05
        val pv = mutableListOf<Double>()

        repeat(reps) {
            sam.shuffle()
            val mart = sprtMart(sam, N = Nc, mu = 1.0 / 2, eta = 0.99 * uB, u = uB, randomOrder = true)
            val found = minOf(1 / mart.maxOrNull()!!, 1.0)
            pv.add(found)
        }

        println("mean p: ${pv.average()}; sd p: ${pv.standardDeviation()}, 90th percentile p: ${pv.percentile(90)}, fraction of SUITE: ${(pv.average()) / .0374}")
        //%%
        val eta = noerror
        val c = (eta - 1 / 2) / 2
        val d = 2
        sam.shuffle()
        val martSprt = sprtMart(sam, N = Nc, mu = 1.0 / 2, eta = eta, u = uB, randomOrder = true)
        println("martSprt=${martSprt.contentToString()}")
        println("KK=${sam.cumulativeProduct().contentToString()}")
        val martAlpha = alphaMart(
            sam, N = Nc, mu = 1.0 / 2, eta = eta, u = uB,
            estim = { _, _, _, _, u -> DoubleArray(Nc) { 0.99 * u } }
        )
        println("martAlpha=${martAlpha.contentToString()}")
        //%%
        println(pv.average() / .0374)
    }
}