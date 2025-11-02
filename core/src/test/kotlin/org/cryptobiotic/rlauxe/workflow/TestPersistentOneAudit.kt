package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.CvrToAuditableCardPolling
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.io.path.Path

class TestPersistentOneAudit {
    // topdir = "/home/stormy/rla/persist/testPersistentOneAudit"
    val topdir = kotlin.io.path.createTempDirectory().toString()

    // @Test
    fun testPersistentWorkflow() {
        val auditDir = "$topdir/audit"
        clearDirectory(Path(auditDir))

        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, contestSampleCutoff = 20000, nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        clearDirectory(Path(auditDir))
        val election = TestOneAuditElection(0.0)

        CreateAudit("TestPersistentOneAudit2", topdir = topdir, auditConfig, election, clear = false)

        val publisher = Publisher(auditDir)
        writeSortedCardsInternalSort(publisher, auditConfig.seed)

        val testMvrsUA = AuditableCardCsvReader(publisher.sortedMvrsFile()).iterator().asSequence().toList()
        val mvrManager = MvrManagerFromRecord(auditDir)
        val oaWorkflow = WorkflowTesterOneAudit(auditConfig, election.contestsUA(), mvrManager)
        var round = 1
        var done = false
        var workflow : AuditWorkflow = oaWorkflow
        while (!done) {
            // why doesnt mvrManager read in the mvrs?
            done = runPersistentWorkflowStage(round, workflow, auditDir, testMvrsUA, Publisher(auditDir))
            workflow = PersistedWorkflow(auditDir, useTest = false)
            round++
        }
        println("------------------ ")
    }

    class TestOneAuditElection(fuzzMvrs: Double): CreateElectionIF {
        val contestsUA = mutableListOf<ContestUnderAudit>()
        val allCardPools = mutableListOf<CardPoolIF>()
        val allCvrs: List<Cvr>
        val testMvrs: List<Cvr>

        init {
            val N = 5000
            val (contestOA, cardPools, testCvrs) = makeOneContestUA(
                N + 100,
                N - 100,
                cvrFraction = .95,
                undervoteFraction = .0,
                phantomFraction = .0
            )

            // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
            // includes the pools votes

            contestsUA.add(contestOA)
            val infos = mapOf(contestOA.contest.info().id to contestOA.contest.info())
            allCardPools.addAll(cardPools)

            val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
            allCvrs = testCvrs + phantoms

            val cvrTabs = tabulateCvrs(allCvrs.iterator(), infos)
            println("allCvrs = ${cvrTabs}")

            val allContests = contestsUA.map { it.contest }
            println("contests")
            allContests.forEach { println("  $it") }
            println()

            testMvrs = if (fuzzMvrs == 0.0) allCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(allContests, allCvrs, fuzzMvrs)
            println("nmvrs = ${testMvrs.size} fuzzed at ${fuzzMvrs}")
        }

        override fun cardPools() = allCardPools
        override fun contestsUA() = contestsUA

        override fun allCvrs() = Pair(
            CvrToAuditableCardPolling(Closer(allCvrs.iterator())),
            CvrToAuditableCardPolling(Closer(testMvrs.iterator()))
        )
    }
}
