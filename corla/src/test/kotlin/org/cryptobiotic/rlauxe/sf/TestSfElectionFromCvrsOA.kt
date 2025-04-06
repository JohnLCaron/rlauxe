package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvIterator
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSfElectionFromCvrsOA {

    // make a OneAudit from Dominion exprted CVRs, using CountingGroupId=1 as the pooled votes
    @Test
    fun createSF2024PoaCards() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/sf2024Pcards"
        createAuditableCards(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"
        println("that took $stopwatch")
    }

    @Test
    fun createSF2024PoaElectionFromCards() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val topDir = "/home/stormy/temp/sf2024Pcards"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCards(
            auditDir,
            "$sfDir/CVR_Export_20240322103409/ContestManifest.json",
            "$sfDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cards.csv",
            "$topDir/ballotPools.csv",
            listOf(1, 2)
        )

        // create sorted cvrs
        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
    }

    // make a OneAudit from Dominion exprted CVRs, using CountingGroupId=1 as the pooled votes
    @Test
    fun createSF2024PoaCvrs() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val zipFilename = "$sfDir/CVR_Export_20240322103409.zip"
        val manifestFile = "$sfDir/CVR_Export_20240322103409/ContestManifest.json"
        val topDir = "/home/stormy/temp/sf2024Poa"
        createSfElectionCvrsOA(topDir, zipFilename, manifestFile) // write to "$topDir/cvrs.csv"

        //  createSfElectionCvrsOA 8957 files totalCards=467063 group1=55810 group2=411253
        // countingContests
        //   1 total=176637, groupCount={2=155705, 1=20932}
        //   2 total=19175, groupCount={2=15932, 1=3243}
        //   3 total=4064, groupCount={2=3300, 1=764}
        //   4 total=1314, groupCount={2=1112, 1=202}
        //   5 total=919, groupCount={2=647, 1=272}
        //   6 total=1092, groupCount={2=889, 1=203}
        //   8 total=94083, groupCount={2=83709, 1=10374}
        //   9 total=72733, groupCount={2=64285, 1=8448}
        //   10 total=8818, groupCount={2=7304, 1=1514}
        //   11 total=10357, groupCount={2=8628, 1=1729}
        //   12 total=233465, groupCount={2=205536, 1=27929}
        //   13 total=233465, groupCount={2=205536, 1=27929}
        //   15 total=211793, groupCount={2=186075, 1=25718}
        //   17 total=21672, groupCount={2=19461, 1=2211}
        //   19 total=233598, groupCount={2=205717, 1=27881}
        //   21 total=127791, groupCount={2=112879, 1=14912}
        //   23 total=105807, groupCount={2=92838, 1=12969}
        //   24 total=233598, groupCount={2=205717, 1=27881}
        //   25 total=233598, groupCount={2=205717, 1=27881}
        //   26 total=233598, groupCount={2=205717, 1=27881}
        //   27 total=233598, groupCount={2=205717, 1=27881}
        //   28 total=233598, groupCount={2=205717, 1=27881}
        //   29 total=233598, groupCount={2=205717, 1=27881}
        //   30 total=233598, groupCount={2=205717, 1=27881}
        //   31 total=233598, groupCount={2=205717, 1=27881}
        //   32 total=233598, groupCount={2=205717, 1=27881}
        //   33 total=233598, groupCount={2=205717, 1=27881}
        // writing to /home/stormy/temp/sf2024Poa/ballotManifest.csv with 8957 batches
        // total ballotManifest = 467063
        // writing to /home/stormy/temp/sf2024Poa/ballotManifest.csv with 2086 pools
        // total cards in pools = 55810

        println("that took $stopwatch")
    }

    @Test
    fun createSF2024Poa() {
        // write sf2024P cvr
        val stopwatch = Stopwatch()
        val sfDir = "/home/stormy/temp/sf2024P"
        val topDir = "/home/stormy/temp/sf2024Poa"

        // create sf2024 election audit
        val auditDir = "$topDir/audit"
        createSfElectionFromCvrsOA(
            auditDir,
            "$sfDir/CVR_Export_20240322103409/ContestManifest.json",
            "$sfDir/CVR_Export_20240322103409/CandidateManifest.json",
            "$topDir/cvrs.csv",
            "$topDir/ballotPools.csv",
            listOf(1, 2)
        )

        // create sorted cvrs
        sortCvrs(auditDir, "$topDir/cvrs.csv", "$topDir/sortChunks")
        mergeCvrs(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCvrs.csv"
        // manually zip (TODO)
        println("that took $stopwatch")
    }

    @Test
    fun runSF2024Poa() {
        val auditDir = "/home/stormy/temp/sf2024Poa/audit"

        val workflow = PersistentAudit(auditDir)
        val contestRounds = workflow.contestsUA().map { ContestRound(it, 1) }

        //val contestUA = workflow.contestsUA().first()
        //val contestRound = ContestRound(contestUA, 1)
        //val assertionRound = contestRound.assertionRounds.first()
        // java.lang.ClassCastException: class org.cryptobiotic.rlauxe.core.ClcaAssertion cannot be cast to class org.cryptobiotic.rlauxe.core.ClcaAssorterIF (org.cryptobiotic.rlauxe.core.ClcaAssertion and org.cryptobiotic.rlauxe.core.ClcaAssorterIF are in unnamed module of loader 'app')
        //val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter

        val mvrManager = MvrManagerSingleRound("/home/stormy/temp/sf2024Poa/audit/sortedCvrs.csv")
        val cvrPairs = mvrManager.makeCvrPairsForRound() // same over all contests!
        //val sampler = ClcaWithoutReplacement(contestUA.id, true, cvrPairs, cassorter, allowReset = false)

        //     runClcaAudit(workflow.auditConfig(), contestRounds, workflow.mvrManager() as MvrManagerClcaIF, 1, auditor = auditor)
        val runner = OneAuditClcaAssertion()

        contestRounds.forEach { contestRound ->
            println("run contest ${contestRound.contestUA.contest}")
            contestRound.assertionRounds.forEach { assertionRound ->
                println("  run assertion ${assertionRound.assertion}")
                val cassorter = (assertionRound.assertion as ClcaAssertion).cassorter
                val sampler = ClcaWithoutReplacement(contestRound.contestUA.id, true, cvrPairs, cassorter, allowReset = false)

                val result: TestH0Result = runner.run(
                    workflow.auditConfig(),
                    contestRound.contestUA.contest,
                    assertionRound,
                    sampler,
                    1,
                )
                assertEquals(TestH0Status.StatRejectNull, result.status)
                println("    sampleCount = ${result.sampleCount} poolCount = ${sampler.poolCount()}\n")
            }
        }
    }
}


class MvrManagerSingleRound(val sortedCvrFile: String, val maxSamples: Int = 20000) : MvrManagerClcaIF {

    override fun Nballots(contestUA: ContestUnderAudit): Int {
        TODO("Not yet implemented")
    }

    override fun ballotCards(): Iterator<BallotOrCvr> {
        TODO("Not yet implemented")
    }

    override fun setMvrsForRound(mvrs: List<CvrUnderAudit>) {
        TODO("Not yet implemented")
    }

    override fun setMvrsForRoundIdx(roundIdx: Int): List<CvrUnderAudit> {
        TODO("Not yet implemented")
    }

    override fun makeCvrPairsForRound(): List<Pair<Cvr, Cvr>> {
        val cvrsUA = mutableListOf<CvrUnderAudit>()
        val cvrIter = cvrsUAiter()
        val count = 0
        while (cvrIter.hasNext() && count < maxSamples) {
            cvrsUA.add(cvrIter.next())
        }
        val cvrs = cvrsUA.map{ it.cvr }
        return cvrs.zip(cvrs)
    }

    private fun cvrsUAiter(): Iterator<CvrUnderAudit> = readCvrsCsvIterator(sortedCvrFile)
}