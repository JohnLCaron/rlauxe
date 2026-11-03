package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import org.cryptobiotic.rlauxe.util.calcProbabilityMassFunction
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.test.Test

class TestConsistenCountySampling {

    @Test
    fun sampleCountySampling() {
        val topdir = "$cases/corla/2020"

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val wantNmvrs = mapOf(380 to 0,649 to 0,444 to 0,561 to 1,541 to 1,154 to 5,33 to 22,300 to 0,301 to 0,117 to 0,118 to 0,35 to 14,16 to 0,17 to 0,14 to 0,15 to 0,387 to 0,381 to 0,382 to 0,383 to 0,384 to 0,385 to 0,386 to 0)

        // fun sampleCountyCvrs(countyAudit: CountyAuditRecord, county: String, wantNmvrs: Map<Int, Int>, maxSamples: Int, ntrials: Int): List<Int>
        println("asking for ${wantNmvrs.values.sum()} nmvrs over ${wantNmvrs.size} contests")
        val dist = sampleCountyCvrs(countyAudit, county = "Bent", wantNmvrs,  100)
        if (dist == null) println("failed") else {
            //println(dist)
            println()
            val ddataset = dist.map { it.toDouble()}
            val welford = Welford()
            ddataset.forEach { welford.update(it)}
            println("This will require ${roundToClosest(welford.mean())} samples on average (+/- ${dfn(welford.stddev(), 1)} standard deviation)")
            // println(calcDecilesFromInt(dist))
            // println("Probability distribution:")
            val pds = calcProbabilityMassFunction(dist, 10, show = false)
            pds.forEach { (key, probability) ->
                println("  sample range [${nfn(key.first, 4)} to ${nfn(key.second, 4)}) has probability = ${dfn(probability, 2)}")
            }
        }
    }

    @Test
    fun sampleElectionSampling() {
        val topdir = "$cases/corla/2020"

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val lastRound = countyAudit.rounds.first()
        val wantNmvrs = lastRound.contestRounds.filter { it.contestUA.preAuditStatus == TestH0Status.InProgress}
            .map { Pair(it.contestUA.id, it.estMvrs) }.sortedBy{ it.first }.toMap()
        println(wantNmvrs)
        println("262 = ${wantNmvrs[262]}")

        // fun sampleCountyCvrs(countyAudit: CountyAuditRecord, county: String, wantNmvrs: Map<Int, Int>, maxSamples: Int, ntrials: Int): List<Int>
        println("asking for ${wantNmvrs.values.sum()} nmvrs over ${wantNmvrs.size} contests")
        val dist = sampleStateCvrs(countyAudit, wantNmvrs,  1)
        if (dist == null) println("failed") else {
            println()
            val ddataset = dist.map { it.toDouble()}
            val welford = Welford()
            ddataset.forEach { welford.update(it)}
            println("This will require ${roundToClosest(welford.mean())} samples on average (+/- ${dfn(welford.stddev(), 1)} standard deviation)")
            // println(calcDecilesFromInt(dist))
            // println("Probability distribution:")
            val pds = calcProbabilityMassFunction(dist, 10, show = false)
            pds.forEach { (key, probability) ->
                println("  sample range [${nfn(key.first, 4)} to ${nfn(key.second, 4)}) has probability = ${dfn(probability, 2)}")
            }
        }
    }


}