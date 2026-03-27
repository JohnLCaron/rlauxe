package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.util.OnlyTask

class WorkflowTesterPolling(
    val auditConfig: Config,
    contestsToAudit: List<ContestIF>, // the contests you want to audit
    val mvrManager: MvrManager,
): AuditWorkflow() {
    private val contestsUA: List<ContestWithAssertions> = contestsToAudit.map { ContestWithAssertions(it, isClca=false).addStandardAssertions() }
    private val auditRounds = mutableListOf<AuditRoundIF>()

    init {
        require (auditConfig.auditType == AuditType.POLLING)
    }

    override fun runAuditRound(auditRound: AuditRound, onlyTask: OnlyTask?, quiet: Boolean): Boolean  {
        val complete = runPollingAuditRound2(auditConfig, auditRound, mvrManager, auditRound.roundIdx)
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun config() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = contestsUA
    override fun mvrManager() = mvrManager
}