package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.co.auditcenter.ContestSelection
import org.cryptobiotic.rlauxe.co.corla.pValueApproximation
import org.cryptobiotic.rlauxe.co.input.ContestMergedInfo
import org.cryptobiotic.rlauxe.corlaCounty.sampleStateCvrs
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.estimate.chooseSamples
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.noerror
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.test.Test

// Uses the round's estimate samples from Estimate. Cant be used when there are no CVRs.

class CompareCorlaAndRlauxe {

    data class RlauxeData(val what: String, val target:Int, val success: Int, val welford: Welford)
    data class Data(val what: String, val corlaMvrs: Int, val corlaTarget: Int, val corlaSuccess: Int, val rlauxe: List<RlauxeData>)

    @Test
    fun compareCorlaAndRlauxe() {
        val datas = mutableListOf<Data>()

        datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2020", Statewide2020General()))
        //datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2024", Statewide2024General()))
        // datas.add(compareCorlaAndRlauxe("/home/joncifer/rla/cases/corla/2026", Statewide2026General()))

        val table = buildString {
            appendLine("${trunc("Election", 20)}, CorlaMvrs, target, success, Rlauxe,    target, success, mean, stdev")

            datas.forEach { data ->
                var first = true
                append("${trunc(data.what, 20)},     ${nfn(data.corlaMvrs, 5)},  ${nfn(data.corlaTarget, 5)},   ${nfn(data.corlaSuccess, 5)}, ")
                data.rlauxe.forEach { rlauxe ->
                    if (!first) append("${trunc("", 20)}                              ")
                    val mean = roundToClosest(rlauxe.welford.mean)
                    val stdev = roundToClosest(rlauxe.welford.stddev())
                    appendLine("${rlauxe.what},  ${nfn(rlauxe.target, 5)},  ${nfn(rlauxe.success, 5)}, ${nfn(mean, 5)}, ${nfn(stdev, 3)}")
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
        val auditRiskLimit = auditRecord.config.riskLimit

        val selected: Map<String, ContestSelection> = ContestSelection.readFromFile(input.contestSelectionFile)
        val corlaTargets = selected.size

        val contests = lastAuditRound.contestRounds.filter{ it.status == TestH0Status.InProgress }
        val mcontestMap = input.mergedContestMap
        val corlaMvrs = input.countyStrataMap.values.sumOf{ it.nmvrs }
        val corlaSuccess = contests.filter {
            corlaRisk(mcontestMap[it.name]) <= auditRiskLimit
        }.count()
        println("${input.name()} corlaMvrs = $corlaMvrs auditRiskLimit = $auditRiskLimit success = $corlaSuccess")

        val rlauxe = mutableListOf<RlauxeData>()

        println("Important")
        setImportant(contests)
        rlauxe.add(sample("Important", auditRecord, contests, mcontestMap, lastAuditRound))

        println("Add Targets")
        addTargets(contests)
        rlauxe.add(sample("AddTarget", auditRecord, contests, mcontestMap, lastAuditRound))

        return Data(input.name(), corlaMvrs, corlaTargets, corlaSuccess, rlauxe)
    }

    fun corlaRisk(mcontest: ContestMergedInfo?): Double {
        if (mcontest == null) return 1.0

        val margin = mcontest.marginInVotes / mcontest.corlaPop.toDouble()
        var pe = mcontest.errors
        if (pe.isEmpty()) pe = listOf(0,0,0,0)
        return pValueApproximation(
            n = mcontest.strata!!.nmvrs, // n = auditedBallots the number of ballots audited for this strata
            dilutedMargin = margin, // probably minimum margin of the contest's assertions,
            gamma = 1.03905,  // “inflator” γ > 1; same purpose as MaxRisk I think. Corla uses 1.03905.
            pe[0], pe[1], pe[2], pe[3],
        )
    }

    // I think when you dont have cvrs, the sampling is from the county pool. but margin assumes you have styles.
    // so best you can do is to add up the contests, which doesnt take into account a card having multiple contests
    fun sample(what: String, auditRecord: CountyAuditRecord, contests: List<ContestRound>, mcontestMap: Map<String, ContestMergedInfo>,
               auditRound: AuditRound): RlauxeData {

        val wantNmvrs = mutableMapOf<Int, Int>()
        contests.map { contestRound ->
            val mcontest = mcontestMap[contestRound.name]!!
            if (contestRound.included) {
                wantNmvrs[contestRound.id] = mcontest.rlauxeEstSamples
            }
        }
        val qdist = sampleStateCvrs(auditRecord, wantNmvrs, 10)!!

        // in order to get "opportunistic risk", we have to run the actual sampler
        // THIS WILL MODIFY THE AUDIT ROUND
        // fun consistentSampling(
        //    auditRound: AuditRoundIF,
        //    samplingCards: CloseableIterable<SamplingCardIF>,
        //    previousSamples: Set<Long> = emptySet(),
        //)
        val samplingCards = PersistedMvrManager(auditRecord).samplingCards()
        consistentSampling(auditRound, samplingCards, emptySet<Long>())

        var success = 0
        contests.map { contestRound -> // for each contest, included or not
            val mcontest = mcontestMap[contestRound.name]!!
            val rlauxeMargin = mcontest.marginInVotes / mcontest.corlaNc.toDouble() // min_margin is from the min assertion
            val rlauxeNoerror = noerror(rlauxeMargin, 1.0)// min_margin is from the min assertion
            val rlauxeEstRisk = estRiskStandardBet(mcontest.corlaNc, rlauxeNoerror, contestRound.haveSampleSize)
            val isSuccess = (rlauxeEstRisk <= mcontest.riskLimit)
            if (isSuccess) success++
        }

        val ddist = qdist.map { it.toDouble() }
        val welford = Welford()
        ddist.forEach { welford.update(it)}
        //println("   $welford")
        return RlauxeData(what, wantNmvrs.size, success, welford)
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
