package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.MvrManagerPollingIF
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestUnderAudit

class WorkflowTesterPolling(
    val auditConfig: AuditConfig,
    contestsToAudit: List<ContestIF>, // the contests you want to audit
    val mvrManager: MvrManagerPollingIF,
): AuditWorkflowIF {
    private val contestsUA: List<ContestUnderAudit> = contestsToAudit.map { ContestUnderAudit(it, isComparison=false, hasStyle=auditConfig.hasStyles) }
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.POLLING)
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runPollingAuditRound(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx, quiet)
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun mvrManager() = mvrManager
}