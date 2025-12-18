package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit

// TODO add the Nbs
class WorkflowTesterClca(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    raireContests: List<RaireContestUnderAudit>,
    val mvrManager: MvrManager,
    Npops : Map<Int, Int> = emptyMap(), // TODO retrofit Npop
): AuditWorkflow() {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        val regularContests = contestsToAudit.map {
            val cua = ContestUnderAudit(it, true, NpopIn=Npops[it.id])
            if (it is DHondtContest) {
                cua.addAssertionsFromAssorters(it.assorters)
            } else {
                cua.addStandardAssertions()
            }
        }

        contestsUA = regularContests + raireContests
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

fun makeClcaNoErrorSampler(contestId: Int, cvrs : List<Cvr>, cassorter: ClcaAssorter): Sampling {
    val cards = cvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, 0) }
    val cvrPairs = cvrs.zip(cards)
    return ClcaSampling(contestId, cvrPairs, cassorter, true)
}
