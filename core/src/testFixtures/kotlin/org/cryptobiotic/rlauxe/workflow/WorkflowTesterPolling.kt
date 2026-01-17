package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestWithAssertions

class WorkflowTesterPolling(
    val auditConfig: AuditConfig,
    contestsToAudit: List<ContestIF>, // the contests you want to audit
    val mvrManager: MvrManager,
): AuditWorkflow() {
    private val contestsUA: List<ContestWithAssertions> = contestsToAudit.map { ContestWithAssertions(it, isClca=false).addStandardAssertions() }
    private val auditRounds = mutableListOf<AuditRoundIF>()

    init {
        require (auditConfig.auditType == AuditType.POLLING)
    }

    override fun runAuditRound(auditRound: AuditRoundIF, quiet: Boolean): Boolean  {
        val complete = runPollingAuditRound(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx, quiet)
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = contestsUA
    override fun mvrManager() = mvrManager
}