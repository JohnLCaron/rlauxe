package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*

private val logger = KotlinLogging.logger("ColoradoPolling")

// // Create polling audits where precincts are used to calculate Nb and simulated mvrs
class CreateColoradoPolling (
    countyElection: CountyContestBuilder,
    auditdir: String,
    pollingMode: PollingMode,
): CreateCorlaElection(countyElection, AuditType.POLLING, auditdir, pollingMode=pollingMode, hasStyle = true) {

    val contestsPolling: List<ContestWithAssertions>

    init {
        contestsPolling = contestsUA.map { contestClca ->
            ContestWithAssertions(contestClca.contest, isClca=false, NpopIn=contestClca.Npop, hasStyle=true).addStandardAssertions()
        }
    }

    override fun contestsUA() = contestsPolling

    override fun cardStyles(): List<StyleIF>? {
        val allContests = contestsUA().map { it.id }.sorted().toIntArray()
        return when {
            (auditType.isPolling() && pollingMode!!.withoutBatches()) -> listOf(CardStyle("OneBatch", 0, allContests, false))
            else -> this.countyPools
        }
    }

}

