package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
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
    val allCvrs: List<Cvr>

    init {
        val contestUA = ContestWithAssertions(contestd, isClca=true).addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        allCvrs = contestd.createSimulatedCvrs()
    }

    override fun electionInfo() = ElectionInfo(AuditType.CLCA, ncards(), contestsUA.size, cvrsContainUndervotes = true, poolsHaveOneCardStyle = null)
    override fun populations() = null
    override fun makeCardPools() = null
    override fun contestsUA() = contestsUA
    override fun cards() = createCardManifest()
    override fun ncards() = allCvrs.size
    override fun createUnsortedMvrs() = allCvrs

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return CvrsToCardsAddStyles(
            AuditType.CLCA,
            Closer(allCvrs.iterator()),
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
    clear: Boolean = true): Result<AuditRoundIF, ErrorMessages>
{
    val auditdir = "$topdir/audit"
    val stopwatch = Stopwatch()
    val election = BelgiumClca(contestd)

    createElectionRecord("belgiumClca", election, auditDir = auditdir, clear = clear)
    println("createBelgiumClca took $stopwatch")

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.CLCA, removeCutoffContests = false, riskLimit = .05, nsimEst=10, minRecountMargin=0.0,
            simFuzzPct = 0.0, quantile=0.5,
            clcaConfig = ClcaConfig(fuzzMvrs=0.0)
        )
    }

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"startFirstBoulderRound took $stopwatch" }

    return result
}


