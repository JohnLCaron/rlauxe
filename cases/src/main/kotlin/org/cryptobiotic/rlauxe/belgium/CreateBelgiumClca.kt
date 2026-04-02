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
    val contestd: DHondtContest,
    val mvrSource: MvrSource,
): ElectionBuilder {

    val infoMap: Map<Int, ContestInfo>
    val contestsUA: List<ContestWithAssertions>
    val allCvrs: List<Cvr>

    init {
        val contestUA = ContestWithAssertions(contestd, isClca=true).addAssertionsFromAssorters(contestd.assorters)
        contestsUA = listOf(contestUA)
        infoMap = contestsUA.associate { it.id to it.contest.info() }
        allCvrs = contestd.createSimulatedCvrs()
    }

    override fun electionInfo() = ElectionInfo(contestd.name, AuditType.CLCA, ncards(), contestsUA.size,
        cvrsContainUndervotes = true, mvrSource = mvrSource)
    override fun batches() = infoMap.values.map { Batch(it.name, it.id, intArrayOf(it.id), true)}
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = allCvrs.size
    override fun createUnsortedMvrsInternal() = allCvrs
    override fun createUnsortedMvrsExternal() = null

    fun createCards(): CloseableIterator<CardWithBatchName> {
        return CvrsToCardsWithBatchNameIterator(
            AuditType.CLCA,
            Closer(allCvrs.iterator()),
            makePhantomCvrs(contestsUA().map { it.contest }),
            batches(),
        )
    }
}

////////////////////////////////////////////////////////////////////
fun createBelgiumClca(
    topdir: String,
    contestd: DHondtContest,
    creation: AuditCreationConfig,
    round: AuditRoundConfig,
    clear: Boolean = true): Result<AuditRoundIF, ErrorMessages>
{
    val auditdir = "$topdir/audit"
    val stopwatch = Stopwatch()
    val election = BelgiumClca(contestd, MvrSource.testPrivateMvrs)

    createElectionRecord(election, auditDir = auditdir, clear = clear)
    println("createBelgiumClca took $stopwatch")

    val config = Config(election.electionInfo(), creation, round)
    createAuditRecord(config, election, auditDir = auditdir, externalSortDir=topdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info {"startFirstBoulderRound took $stopwatch" }

    return result
}


