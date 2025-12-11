package org.cryptobiotic.rlauxe.belgium

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("BelgiumClca")

class BelgiumClca (
    contestd: DHondtContest,
    val hasStyle: Boolean,
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
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return CvrsWithStylesToCardManifest(AuditType.CLCA, hasStyle,
            Closer(cvrs.iterator()),
            makePhantomCvrs(contestsUA().map { it.contest }),
            null,
        )
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
            AuditType.CLCA, hasStyle = true, removeCutoffContests = false, riskLimit = .05, nsimEst=10, minRecountMargin=0.0, simFuzzPct = 0.001, // auditSampleLimit=1000,
        )
    }
    val election = BelgiumClca(contestd, config.hasStyle)

    CreateAudit("belgiumClca", config, election, topdir, clear = clear)
    println("createBelgiumClca took $stopwatch")
}


