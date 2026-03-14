package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoPolling")

// // Create poliing audits where precincts are used to calculate Nb
class CreateColoradoPolling (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditdir: String,
    hasSingleCardStyle: Boolean,
): CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, AuditType.POLLING, auditdir, hasSingleCardStyle=hasSingleCardStyle) {

    val contestsPolling: List<ContestWithAssertions>

    init {
        contestsPolling = contestsUA.map { contestClca ->
            ContestWithAssertions(contestClca.contest, isClca=false, NpopIn=contestClca.Npop).addStandardAssertions()
        }
    }

    override fun contestsUA() = contestsPolling
}

