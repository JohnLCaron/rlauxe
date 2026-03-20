package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.OnlyTask

class WorkflowTesterOneAudit(
    val config: Config,
    val contestsUA: List<ContestWithAssertions>,
    val mvrManager: MvrManager,
): AuditWorkflow() {
    private val auditRounds = mutableListOf<AuditRoundIF>()

    init {
        require (config.auditType == AuditType.ONEAUDIT)
    }

    override fun runAuditRound(auditRound: AuditRound, onlyTask: OnlyTask?, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(config, auditRound, mvrManager, auditRound.roundIdx,
            auditor = OneAuditAssertionAuditor(mvrManager.pools()!!)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun config() =  this.config
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = contestsUA

    override fun mvrManager() = mvrManager
}

