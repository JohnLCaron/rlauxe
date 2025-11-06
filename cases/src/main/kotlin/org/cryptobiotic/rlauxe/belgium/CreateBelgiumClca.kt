package org.cryptobiotic.rlauxe.belgium

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.sequences.plus

private val logger = KotlinLogging.logger("BelgiumClca")

class BelgiumClca (
    contestd: DHondtContest,
    hasStyle: Boolean,
): CreateElectionIF {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestUnderAudit>
    val cvrs: List<Cvr>

    init {
        val contestUA = ContestUnderAudit(contestd, isClca=true, hasStyle=hasStyle).addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        cvrs = contestd.createSimulatedCvrs()
    }

    override fun cardPools() = null
    override fun contestsUA() = contestsUA

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvrHasStyle(cvr, idx, isClca=true) }.asSequence()
        val cardSeq = CvrToCardAdapter(Closer(cvrs.iterator())).asSequence()
        val allCardsIter = (cardSeq + phantomSeq).iterator()
        return Pair(null, Closer( allCardsIter))
    }
}

////////////////////////////////////////////////////////////////////
fun createBelgiumClca(
    topdir: String,
    contestd: DHondtContest,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.CLCA, hasStyle = true, removeCutoffContests = false, riskLimit = .05, nsimEst=10, minRecountMargin=0.0,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
    }
    val election = BelgiumClca(contestd, config.hasStyle)

    CreateAudit("belgiumClca", topdir, config, election, clear = clear)
    println("createBelgiumClca took $stopwatch")
}


