package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.MvrManagerClcaIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

// TODO can this be merged with ClcaContestAuditTaskGenerator ?
class ClcaAuditTester(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>,
    val mvrManager: MvrManagerClcaIF,
): RlauxAuditIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
        contestsUA = regularContests + raireContests

        // TODO dont raire contests already have assertions added ??
        contestsUA.forEach { contest ->
            contest.addClcaAssertionsFromReportedMargin()
        }
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
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