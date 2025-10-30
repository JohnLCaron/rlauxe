package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.MvrManagerClcaIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

class WorkflowTesterClca(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>,
    val mvrManager: MvrManagerClcaIF,
): AuditWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map {
            if (it is DHondtContest) {
                val cua = ContestUnderAudit(it, isComparison = true, hasStyle = auditConfig.hasStyles, addAssertions = false)
                cua.addAssertionsFromAssorters(it.assorters)
                cua
            } else {
                ContestUnderAudit(it, isComparison = true, hasStyle = auditConfig.hasStyles)
            }
        }
        contestsUA = regularContests + raireContests

        // TODO dont raire contests already have assertions added ??
        contestsUA.forEach { contest ->
            contest.addClcaAssertionsFromReportedMargin()
        }
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
            auditor = ClcaAssertionAuditor(quiet)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun mvrManager() = mvrManager
}