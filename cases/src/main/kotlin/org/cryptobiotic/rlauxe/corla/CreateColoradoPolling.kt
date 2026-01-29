package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoPolling")

// // Create poliing audits where precincts are used to calculate Nb
class ColoradoPolling (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    config: AuditConfig,
): CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, config) {

    val contestsPolling: List<ContestWithAssertions>

    init {
        val contestTabs: Map<Int, ContestTabulation> = tabulateCvrs(CvrIteratorfromPools(), infoMap)
        contestsPolling = makePollingContests(contestTabs)
    }

    fun makePollingContests(tabs: Map<Int, ContestTabulation>): List<ContestWithAssertions> {
        val infoList= oaBuilders.map { it.info }.sortedBy { it.id }
        val contestMap= oaBuilders.associateBy { it.info.id }

        println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max( ncards, oaContest.Nc)
            val contest = Contest(info, candVotes, useNc, ncards)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            val Nb = tabs[contest.id]?.ncardsTabulated // tabs.ncards + contest.Np TODO
            ContestWithAssertions(contest, isClca=false, NpopIn=Nb).addStandardAssertions()
        }

        return regContests
    }

    override fun contestsUA() = contestsPolling
}

////////////////////////////////////////////////////////////////////
// Create poliing audits where precincts are used to calculate Nb
fun createColoradoPolling(
    topdir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true,
)
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.POLLING, riskLimit = .03, nsimEst = 100, quantile = 0.5,
            pollingConfig = PollingConfig()
        )
    }
    // open class CreateColoradoElection (
    //    electionDetailXmlFile: String,
    //    contestRoundFile: String,
    //    precinctFile: String,
    //    val config: AuditConfig,
    //    val poolsHaveOneCardStyle:Boolean = false,
    val election = CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, config)

    CreateAudit("corla", config, election, auditDir = "$topdir/audit", clear = clear)
    println("createColoradoPolling took $stopwatch")
}


