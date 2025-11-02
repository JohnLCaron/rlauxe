package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.ClcaSimulatedErrorRates
import org.cryptobiotic.rlauxe.rairejson.import
import org.cryptobiotic.rlauxe.rairejson.readRaireBallotsCsv
import org.cryptobiotic.rlauxe.rairejson.readRaireResultsJson
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df

class TestComparisonSamplerWithRaire {

    // @Test
    fun testComparisonSamplerForRaire() {
        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallotsCsv(cvrFile)
        val cvrs = raireCvrs.cvrs

        val ncs = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), it.ncvrs + 2) }
        val nps = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), 2) }
        val raireResults =
            readRaireResultsJson("src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
                .import(ncs, nps)
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        // TODO margin not set because its not present in RaireAssertions.json
        contestUA.addStandardAssertions()

        contestUA.clcaAssertions.forEach { assert ->
            run(cvrs, contestUA, assert.cassorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, cassorter: ClcaAssorter) {
        println("\n${cassorter.assorter().desc()}")

        val sampler = ClcaSimulatedErrorRates(cvrs, contestUA.contest, cassorter, ClcaErrorTable.standard)

        val orgCvrs = cvrs.map { cassorter.assorter().assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { cassorter.assorter().assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { cassorter.assorter().assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrs.map { cassorter.bassort(it, it) }.average()
        sampler.reset()
        val welford = Welford()
        repeat(cvrs.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(cassorter.noerror())} noerror=${df(before)} sampleMean = ${df(welford.mean)}")
    }

}