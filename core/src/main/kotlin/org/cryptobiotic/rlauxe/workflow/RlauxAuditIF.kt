package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sampleCheckLimits
import org.cryptobiotic.rlauxe.util.Stopwatch

interface RlauxAuditIF {
    fun auditConfig() : AuditConfig
    fun mvrManager() : MvrManager
    fun auditRounds(): MutableList<AuditRound>
    fun contestsUA(): List<ContestUnderAudit>

    // start new round and create estimate
    fun startNewRound(quiet: Boolean = true): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            // first time, create the rounds
            val contestRounds = contestsUA().map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        } else {
            // next time, create from previous round
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        if (!quiet) println("Estimate round ${roundIdx}")
        // 1. _Estimation_: for each contest, estimate how many samples are needed to satisfy the risk function,
        estimateSampleSizes(
            auditConfig(),
            auditRound,
        )

        // 2. _Choosing sample sizes_: the Auditor decides which contests and how many samples will be audited.
        // 3. _Random sampling_: The actual ballots to be sampled are selected randomly based on a carefully chosen random seed.
        sampleCheckLimits(
            auditConfig(),
            mvrManager(),
            auditRound,
            auditRounds.previousSamples(roundIdx),
            quiet)
        return auditRound
    }

    // 5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    // fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    // fun setMvrsForRound(mvrs: List<AuditableCard>)
    // AuditRecord.enterMvrs(mvrFile: String)

    // 6. _Run the audit_
    fun runAuditRound(auditRound: AuditRound, quiet: Boolean = true): Boolean  // return complete
}

// runs audit rounds until finished. return last audit round
// Can only use this if the MvrManager implements MvrManagerTest
// otherwise run one round at a time with PersistentAudit
fun runAudit(name: String, workflow: RlauxAuditIF, quiet: Boolean=true): AuditRound? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRound? = null
    var complete = false
    while (!complete) {
        nextRound = workflow.startNewRound(quiet=quiet)
        if (nextRound.samplePrns.isEmpty()) {
            complete = true

        } else {
            stopwatch.start()

            // workflow MvrManager must implement MvrManagerTest, else Exception
            (workflow.mvrManager() as MvrManagerTest).setMvrsBySampleNumber(nextRound.samplePrns)

            if (!quiet) println("\nrunAudit $name ${nextRound.roundIdx}")
            complete = workflow.runAuditRound(nextRound, quiet)
            nextRound.auditWasDone = true
            nextRound.auditIsComplete = complete
            if (!quiet) println(" runAudit $name ${nextRound.roundIdx} done=$complete samples=${nextRound.samplePrns.size}")
        }
    }

    return nextRound
}

/*
fun RlauxAuditIF.showResults() {
    println("Audit Rounds")
    auditRounds().forEach { println(it) }

    println("Audit results")
    this.contestsUA().forEach{ contest ->
        val minAssertion = contest.minAssertion()
        if (minAssertion == null) {
            println(" $contest has no assertions; status=${contest.status}")
        } else {
            // if (minAssertion.roundResults.size == 1) {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs} ${minAssertion.auditResult}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
           /* } else {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                minAssertion.roundResults.forEach { rr -> println("   $rr") }
            } */
        }
    }

    var maxBallotsUsed = 0
    this.contestsUA().forEach { contest ->
        contest.assertions.filter { it.auditResult != null }.forEach { assertion ->
            val lastRound = assertion.auditResult!!
            maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotIndexUsed)
        }
    }
    println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n")
}
 */