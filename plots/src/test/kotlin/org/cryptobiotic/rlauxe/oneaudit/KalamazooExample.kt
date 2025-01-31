import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.mean2margin
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.pow

import kotlin.test.Test
import kotlin.test.assertEquals

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
        // whitmer=20699, schuette=5569, assorter_mean_all=0.5468806477264513,
        assertEquals(20699, whitmer)
        assertEquals(5569, schuette)
        assertEquals(0.5468806477264513, assorterMeanAll, doublePrecision)

        val u = 1.0
        val v = 2 * assorterMeanAll - 1
        val uB = 2 * u / (2 * u - v) // upper bound on the overstatement assorter
        val noerror = u / (2 * u - v) // alternative value when cvr == mvr
        println("margin=${df(v)}, uB=${df(uB)}, noerror=${df(noerror)}")
        // assorter_mean_poll=0.5682996602896477, eta=0.5245932724032007, v=0.09376129545290257, u_b=1.0491865448064015,
        assertEquals(0.5682996602896477, assorterMeanPoll, doublePrecision)
        assertEquals(0.09376129545290257, v, doublePrecision)
        assertEquals(0.5245932724032007, noerror, doublePrecision)
        assertEquals(1.0491865448064015, uB, doublePrecision)

        // these are the assorter values - directly calculated
        val sam = DoubleArray(nCvr) { noerror } +
                DoubleArray(votesAudPoll.filterKeys { it in listOf("Butkovich", "Gelineau", "Kurland", "Schleiger") }
                    .values.sum()) { 1.0 / 2 } +
                DoubleArray(votesAudPoll["Schuette"]!!) { (1 - assorterMeanPoll) / (2 - v) } +
                DoubleArray(votesAudPoll["Whitmer"]!!) { (2 - assorterMeanPoll) / (2 - v) }
        println("sam=${sam.joinToString(",")}")
        val expectedSam = listOf(0.52459327, 0.52459327, 0.52459327, 0.52459327, 0.52459327,
            0.52459327, 0.52459327, 0.52459327, 0.5       , 0.22646709,
            0.22646709, 0.22646709, 0.22646709, 0.22646709, 0.22646709,
            0.22646709, 0.22646709, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037)
        assertTrue(doublesAreClose(expectedSam, sam.toList()))
        println()

        //%%
        val reps = 10000 // 10.0.pow(5).toInt()
        val alpha = 0.05
        val pv = mutableListOf<Double>()

        repeat(reps) {
            sam.shuffle() // random shuffles so results wont exactly match
            val mart = sprtMart(sam, N = N, mu = 1.0 / 2, eta = 0.99 * uB, u = uB, randomOrder = true)
            val found = minOf(1 / mart.maxOrNull()!!, 1.0)
            pv.add(found)
        }
        println("reps $reps mean p: ${pv.average()}; sd p: ${pv.standardDeviation()}, 90th percentile p: ${pv.percentile(90)}, fraction of SUITE: ${(pv.average()) / .0374}")
        // mean p: 0.020145370436527022; sd p: 0.010403092547269111, 90th percentile p: 0.03212303145449312, fraction of SUITE: 0.5386462683563374
        assertEquals(0.020145370436527022, pv.average(), .001)
        assertEquals(0.010403092547269111, pv.standardDeviation(), .001)
        println()

        val eta = noerror
        val c = (eta - 1 / 2) / 2
        val d = 2
        sam.shuffle()
        val martSprt = sprtMart(sam, N = N, mu = 1.0 / 2, eta = eta, u = uB, randomOrder = true)
        println("martSprt=${martSprt.contentToString()}")
        println("KK=${sam.cumulativeProduct().contentToString()}")

        val expectedMartSprt = listOf(1.02248561, 1.02473944, 1.04779161, 1.02210432, 1.02435733,
            1.02661546, 1.0014549 , 1.02397427, 0.9988792 , 1.00107941,
            0.97655364, 0.99850341, 1.02095567, 1.02320619, 0.99812979,
            0.99812983, 1.02057377, 1.04353172, 1.06701567, 1.09103788,
            1.1156109 , 1.14074758, 1.14327148, 1.16904291, 1.19540598,
            1.22237451, 1.24996266, 1.27818491, 1.24675376, 1.27490254,
            1.24355305, 1.27162855, 1.30034957, 1.30323919, 1.3326873 ,
            1.36281303, 1.3292679 , 1.35931526, 1.3623404 , 1.39314907)
        val actualMartSprt = listOf(1.0224856054347498, 0.9974532784938619, 1.0198725730879747,
            0.9949049740443163, 1.0172661763536113, 1.040119824645172, 1.0634964107308147, 1.0374440531146072,
            1.0607794834821263, 1.0846292282147685, 1.1090251161322875, 1.1090266860632607, 1.1339916295384642,
            1.1595185518650055, 1.1310764524535908, 1.1335765485635532, 1.1360801548244055, 1.108212898115588,
            1.1331707531532729, 1.1586793784121971, 1.1847728330110194, 1.211464761315976, 1.2387691313830678,
            1.2667002427609761, 1.2952727344849935, 1.2981442736457829, 1.301024303581053, 1.3303960451578154,
            1.2976940651557767, 1.2657851853944315, 1.2943614839722948, 1.323570002555765, 1.3534497711332032,
            1.3201719056233299, 1.323105172048572, 1.3260425955674262, 1.35599045606135, 1.3866158929582317,
            1.3896970357177454, 1.3927873035321539)
        // assertTrue(doublesAreClose(expectedMartSprt, martSprt.toList())) fail

        val expectedKK = listOf(1.50212073, 1.57600486, 2.36734958, 1.07225356, 1.12499401, 1.18032857,
            0.53461116, 0.80305051, 0.36372903, 0.38161961, 0.17284857, 0.25963942,
            0.39000975, 0.40919298, 0.18533749, 0.18533749, 0.27839929, 0.41818934,
            0.62817088, 0.9435885,   1.41738385, 2.12908167, 2.23380384, 3.35544306,
            5.04028058, 7.57110996, 11.37272124, 17.08320036, 7.73756548, 11.62275753,
            5.26434424, 7.90768063, 11.87829102, 12.46254311, 18.72024439, 28.12006722,
            12.73653981, 19.13182051, 20.07284865, 30.15184212)
        val actualKK = listOf(0.7510603663094279, 0.17009045850624255, 0.12774820207143706, 0.028930764074863814,
            0.02172875026367885, 0.016319603132484716, 0.012257007106708457, 0.0027758087794442373, 0.0020847999586943145,
            0.001565810620658832, 0.001176018298323215, 5.880091491616075E-4, 4.4163036696261187E-4, 3.316910651843063E-4,
            7.51171116069508E-5, 3.9405931391366775E-5, 2.067208650069311E-5, 4.681547354790117E-6, 3.516124671183598E-6,
            2.6408218835287697E-6, 1.983416651201071E-6, 1.4896656365952951E-6, 1.1188288186998293E-6, 8.403079824102382E-7,
            6.311220210817698E-7, 3.3108236632500747E-7, 1.7368358198543096E-7, 1.304468547079113E-7, 2.9541920094908518E-8,
            6.690272792303906E-9, 5.02479873409777E-9, 3.7739271778626206E-9, 2.834447128630605E-9, 6.41909004051823E-10,
            3.367411450206253E-10, 1.766521392191706E-10, 1.3267642039129433E-10, 9.964800089970916E-11, 5.227467088041552E-11,
            2.7422940660957484E-11)
        // assertTrue(doublesAreClose(expectedKK, sam.cumulativeProduct().toList())) fail

        // alphaMart where the bet is always .99*u
        val martAlpha = alphaMart(
            sam, N = N, mu = 1.0 / 2, eta = eta, u = uB,
            estim = { _, _, _, _, u -> DoubleArray(N) { 0.99 * u } }
        )
        println("martAlpha=${martAlpha.contentToString()}")

        val expectedMartAlpha = listOf(1.49252803,  1.56456573,  2.33520443,  1.08213862,  1.13436882,
            1.18912203,  0.55103326,  0.82243428,  0.38111139,  0.399499  ,
            0.18512245,  0.27629563,  0.41237922,  0.4322832 ,  0.20031765,
            0.2003178 ,  0.29898015,  0.44624465,  0.66605718,  0.99416356,
            1.48392517,  2.21500135,  2.32211864,  3.46621108,  5.17408362,
            7.72359901, 11.52958915, 17.21138361,  7.97702107, 11.90808691,
            5.51907497,  8.2388549 , 12.29915447, 12.89530411, 19.25079207,
            28.73911963, 13.32048566, 19.88585287, 20.85048455, 31.12783607)
        // assertTrue(doublesAreClose(expectedMartAlpha, martAlpha.toList())) fail

        //%%
        println(pv.average() / .0374) // should be 0.5386462683563374
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