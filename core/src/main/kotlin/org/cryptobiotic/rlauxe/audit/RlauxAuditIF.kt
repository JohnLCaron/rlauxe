package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sampleCheckLimits

// used in ConsistentSampling
interface RlauxAuditProxy {
    fun auditConfig() : AuditConfig
    fun mvrManager() : MvrManager
}

interface RlauxAuditIF: RlauxAuditProxy {
    fun auditRounds(): MutableList<AuditRound>
    fun contestsUA(): List<ContestUnderAudit>

    // start new round and create estimate
    fun startNewRound(quiet: Boolean = true): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA().map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, sampleNumbers = emptyList(), sampledBorc = emptyList())
        } else {
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
        sampleCheckLimits(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    // 5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    // fun setMvrsBySampleNumber(sampleNumbers: List<Long>)

    // 6. _Run the audit_5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    fun runAuditRound(auditRound: AuditRound, quiet: Boolean = true): Boolean  // return complete
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