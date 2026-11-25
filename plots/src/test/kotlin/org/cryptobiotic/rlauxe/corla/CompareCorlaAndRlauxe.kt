package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.co.input.ContestMergedInfo
import org.cryptobiotic.rlauxe.corlaCounty.sampleStateCvrs
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.test.Test

// Uses the round's estimate samples from Estimate. Cant be used when there are no CVRs.

class CompareCorlaAndRlauxe {

    data class RlauxeData(val what: String, val welford: Welford)
    data class Data(val what: String, val corlaMvrs: Int, val rlauxe: List<RlauxeData>)

    @Test
    fun compareCorlaAndRlauxe() {
        val datas = mutableListOf<Data>()

        // datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2020", Statewide2020General()))
        datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2024", Statewide2024General()))
        // datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2026", Statewide2026General()))

        val table = buildString {
            appendLine("${trunc("Election", 20)}, CorlaMvrs, Rlauxe,    mean, stdev")

            datas.forEach { data ->
                var first = true
                append("${trunc(data.what, 20)},    ${nfn(data.corlaMvrs,5)}, ")
                data.rlauxe.forEach { rlauxe ->
                    if (!first) append("${trunc("", 20)}            ")
                    val mean = roundToClosest(rlauxe.welford.mean)
                    val stdev = roundToClosest(rlauxe.welford.stddev())
                    appendLine("${rlauxe.what}, ${nfn(mean, 5)}, ${nfn(stdev, 3)}")
                    first = false
                }
                appendLine()
            }
        }
        println(table)
    }

    fun compareCorlaAndRlauxe(topdir: String, input: StatewideInput): Data {
        val auditRecord = AuditRecord.read(topdir)!! as CountyAuditRecord
        val lastAuditRound = auditRecord.rounds.last()
        val contests = lastAuditRound.contestRounds.filter{ it.status == TestH0Status.InProgress }
        val auditRiskLimit = auditRecord.config.riskLimit

        val corlaMvrs = input.countyStrataMap.values.sumOf{ it.nmvrs }
        println("${input.name()} corlaMvrs = $corlaMvrs auditRiskLimit = $auditRiskLimit")

        val mcontestMap = input.mergedContestMap

        val rlauxe = mutableListOf<RlauxeData>()

        println("Important")
        setImportant(contests)
        rlauxe.add(sample("Important", auditRecord, contests, input.mergedContestMap))

        println("Add Targets")
        addTargets(contests)
        rlauxe.add(sample("AddTarget", auditRecord, contests, input.mergedContestMap))

        return Data(input.name(), corlaMvrs, rlauxe)
    }

    // I think when you dont have cvrs, the sampling is from the county pool. but margin assumes you have styles.
    // so best you can do is to add up the contests, which doesnt take into account a card having multiple contests
    fun sample(what: String, auditRecord: CountyAuditRecord, contests: List<ContestRound>, mcontestMap: Map<String, ContestMergedInfo>): RlauxeData {
        val wantNmvrs =  contests.filter { it.included }.map {
            val mcontest = mcontestMap[it.name]!!
            Pair(it.id, mcontest.rlauxeEstSamples)
        }.toMap()
        //println("wantNmvrs = ${wantNmvrs.size}")
        //wantNmvrs.toSortedMap().forEach { println(it) }

        val qdist = sampleStateCvrs(auditRecord, wantNmvrs, 10)!!

        val ddist = qdist.map { it.toDouble() }
        val welford = Welford()
        ddist.forEach { welford.update(it)}
        //println("   $welford")
        return RlauxeData(what, welford)
    }

    fun setImportant(contests: List<ContestRound>) {
        for (contest in contests) {
            val counties = contest.contestUA.contest.info().metadata["CanonicalCounties"]
            val ncounties = if (counties == null) 0 else counties.drop(1).dropLast(1).split(",".toRegex())
                .dropLastWhile { it.isEmpty() }.size

            contest.included = false
            if (ncounties > 55) contest.included = true // statewide
            if (contest.name.contains("Judicial District")) contest.included = true
            if (contest.name.contains("Passenger Rail")) contest.included = true
            if (contest.name.startsWith("Regent")) contest.included = true
            if (contest.name.startsWith("Regional")) contest.included = true
            if (contest.name.startsWith("Representative to the")) contest.included = true
            if (contest.name.startsWith("State")) contest.included = true
        }
    }

    fun addTargets(contests: List<ContestRound>) {
        for (contest in contests) {
            val reason = contest.contestUA.contest.info().metadata["CORLAauditReason"]
            val target = if (reason == null) false else reason.lowercase().startsWith("state") || reason.lowercase()
                .startsWith("county")
            if (target) contest.included = true
        }
    }

}
