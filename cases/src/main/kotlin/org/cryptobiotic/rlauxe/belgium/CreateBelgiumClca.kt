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
): CreateElectionIF {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestUnderAudit>
    val cvrs: List<Cvr>

    init {
        val contestUA = ContestUnderAudit(contestd, isComparison=true, hasStyle=true, addAssertions=false)
        contestUA.addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        cvrs = contestd.createSimulatedCvrs()
    }

    override fun cardPools() = null
    override fun contestsUA() = contestsUA

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()
        val cardSeq = CvrToCardAdapter(Closer(cvrs.iterator())).asSequence()
        val allCardsIter = (cardSeq + phantomSeq).iterator()
        return Pair(null, Closer( allCardsIter))
    }
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createBelgiumClca(
    topdir: String,
    contestd: DHondtContest,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()
    val election = BelgiumClca(contestd)

    val auditConfig = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.CLCA, hasStyles = true, contestSampleCutoff = 20000, riskLimit = .05, nsimEst=10, minRecountMargin=0.0,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
    }

    CreateAudit("belgiumClca", topdir, auditConfig, election, clear = clear)
    println("createBelgiumClca took $stopwatch")
}


