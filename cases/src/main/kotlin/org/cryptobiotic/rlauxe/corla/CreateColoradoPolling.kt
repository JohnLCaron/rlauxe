package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*

private val logger = KotlinLogging.logger("ColoradoPolling")

// // Create polling audits where precincts are used to calculate Nb and simulated mvrs
class CreateColoradoPolling (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditdir: String,
    hasExactContests: Boolean,
    pollingMode: PollingMode,
): CreateColoradoElection(
            electionDetailXmlFile,
            contestRoundFile,
            precinctFile,
            AuditType.POLLING,
            auditdir,
            hasExactContests=hasExactContests,
            pollingMode=pollingMode) {

    val contestsPolling: List<ContestWithAssertions>

    init {
        contestsPolling = contestsUA.map { contestClca ->
            ContestWithAssertions(contestClca.contest, isClca=false, NpopIn=contestClca.Npop).addStandardAssertions()
        }
    }

    override fun contestsUA() = contestsPolling

    override fun cardStyles(): List<StyleIF>? {
        val allContests = contestsUA().map { it.id }.sorted().toIntArray()
        return when {
            (auditType.isPolling() && pollingMode!!.withoutBatches()) -> listOf(CardStyle("OneBatch", 0, allContests, false))
            else -> batches
        }
    }

}

