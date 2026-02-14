package org.cryptobiotic.rlauxe.belgium

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*

private val logger = KotlinLogging.logger("BelgiumClca")

class BelgiumClca (
    contestd: DHondtContest,
): CreateElectionIF {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestWithAssertions>
    val cvrs: List<Cvr>

    init {
        val contestUA = ContestWithAssertions(contestd, isClca=true).addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        cvrs = contestd.createSimulatedCvrs()
    }

    override fun populations() = null
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun cards() = createCardManifest()
    override fun ncards() = cvrs.size

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return CvrsToCardsAddStyles(
            AuditType.CLCA,
            Closer(cvrs.iterator()),
            makePhantomCvrs(contestsUA().map { it.contest }),
            null,
        )
    }
}

////////////////////////////////////////////////////////////////////
fun createBelgiumClca(
    auditdir: String,
    contestd: DHondtContest,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.CLCA, removeCutoffContests = false, riskLimit = .05, nsimEst=10, minRecountMargin=0.0, simFuzzPct = 0.0,
            quantile=0.5
        )
    }
    val election = BelgiumClca(contestd)

    CreateAudit("belgiumClca", config, election, auditdir, clear = clear)
    println("createBelgiumClca took $stopwatch")
}


