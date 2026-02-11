package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.ContestWithAssertions

class WorkflowTesterOneAudit(
    val config: AuditConfig,
    val contestsUA: List<ContestWithAssertions>,
    val mvrManager: MvrManager,
): AuditWorkflow() {
    private val auditRounds = mutableListOf<AuditRoundIF>()

    init {
        require (config.auditType == AuditType.ONEAUDIT)
    }

    override fun runAuditRound(auditRound: AuditRound, onlyTask: String?, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(config, auditRound, mvrManager, auditRound.roundIdx,
            auditor = OneAuditAssertionAuditor(mvrManager.oapools()!!)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.config
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = contestsUA

    override fun mvrManager() = mvrManager
}

