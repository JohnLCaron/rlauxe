package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards

class WorkflowTesterOneAudit(
    val config: AuditConfig,
    val contestsUA: List<ContestUnderAudit>,
    val mvrManager: MvrManager,
): AuditWorkflow() {
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (config.auditType == AuditType.ONEAUDIT)
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
            auditor = OneAuditAssertionAuditor()
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.config
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA

    override fun mvrManager() = mvrManager

    companion object {

        // you already have the Nbs and the pools
        fun makeUAs( config: AuditConfig,
                    contestsToAudit: List<Contest>, // the contests you want to audit
                    mvrManager: MvrManager,
                    Nbs : Map<Int, Int> = emptyMap(),
                    pools: List<CardPoolIF>): WorkflowTesterOneAudit {

            val contestsUA = contestsToAudit.map {
                val cua = ContestUnderAudit(it, true, hasStyle = config.hasStyle, Nbin=Nbs[it.id]).addStandardAssertions()
                if (it is DHondtContest) {
                    cua.addAssertionsFromAssorters(it.assorters)
                } else {
                    cua.addStandardAssertions()
                }
            }
            // TODO ??
            addOAClcaAssortersFromMargin(contestsUA, pools, hasStyle=true)

            return WorkflowTesterOneAudit(config, contestsUA, mvrManager)
        }

        // TODO trying hard to keep things consistent. but attacks are inconsistent.
        fun makeUAandPools(
            config: AuditConfig,
            infos: Map<Int, ContestInfo>, // the contests you want to audit
            contestsToAudit: List<Contest>, // the contests you want to audit
            mvrManager: MvrManager,
            cardStyles: List<CardStyleIF>,
            cards: List<AuditableCard>,
            mvrs: List<Cvr>,
        ): WorkflowTesterOneAudit {
            // The Nbs come from the cards
            val manifestTabs = tabulateAuditableCards(Closer(cards.iterator()), infos)
            val Nbs = manifestTabs.mapValues { it.value.ncards }

            val contestsUA = contestsToAudit.map {
                val cua = ContestUnderAudit(it, true, hasStyle = config.hasStyle, Nbin=Nbs[it.id]).addStandardAssertions()
                if (it is DHondtContest) {
                    cua.addAssertionsFromAssorters(it.assorters)
                } else {
                    cua.addStandardAssertions()
                }
            }
            println("tabulateAuditableCards")
            manifestTabs.forEach { (id, tab) ->
                println(" $tab")
            }
            println()

            // The styles have the name, id, and contest list
            val poolsFromCvrs = cardStyles.map { style ->
                val poolFromCvr = CardPoolFromCvrs(style.name(), style.id(), infos)
                style.contests().forEach { poolFromCvr.contestTabs[it]  = ContestTabulation( infos[it]!!) }
                poolFromCvr
            }.associateBy { it.poolId }

            // The pool counts come from the mvrs
            mvrs.filter{ it.poolId != null }.forEach {
                val pool = poolsFromCvrs[it.poolId]
                if (pool != null) pool.accumulateVotes(it)
            }

            println("tabulatePooledMvrs")
            poolsFromCvrs.forEach { (id, pool) ->
                println(pool)
                pool.contestTabs.forEach {
                    println(" $it")
                }
                println()
            }

            // The OA assort averages come from the mvrs
            addOAClcaAssortersFromMargin(contestsUA, poolsFromCvrs.values.toList(), hasStyle=true)

            return WorkflowTesterOneAudit(config, contestsUA, mvrManager)
        }
    }
}

