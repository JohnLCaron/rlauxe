package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max
import kotlin.sequences.plus

private val logger = KotlinLogging.logger("ColoradoPolling")

// // Create poliing audits where precincts are used to calculate Nb
class ColoradoPolling (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    config: AuditConfig,
): ColoradoOneAudit(electionDetailXmlFile, contestRoundFile, precinctFile, config, true) {

    val contestsPolling: List<ContestUnderAudit>

    init {
        contestsPolling = makePollingContests()
        val contestMap = contestsPolling.associateBy { it.id }

        val contestTabs = tabulateCvrs(CvrIteratorfromPools(), infoMap)
        contestTabs.forEach { contestId, tab ->
            val contest = contestMap[contestId]
            if (contest != null) {
                contest.setNb(tab.ncards + contest.Np)
                println("contest $contestId Nb = ${tab.ncards} Nb/Nc = ${tab.ncards / contest.Nc.toDouble()}")
            }
        }
    }

    fun makePollingContests(): List<ContestUnderAudit> {
        val infoList= oaContests.map { it.info }.sortedBy { it.id }
        val contestMap= oaContests.associateBy { it.info.id }

        println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max( ncards, oaContest.Nc)
            val contest = Contest(info, candVotes, useNc, ncards)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            ContestUnderAudit(contest, isClca=false, hasStyle=false).addStandardAssertions()
        }

        return regContests
    }

    override fun cardPools() = null
    override fun contestsUA() = contestsPolling

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()

        val cvrIter: Iterator<Cvr> = CvrIteratorfromPools()  // "fake" truth
        val cardSeq = CvrToAuditableCardPolling(Closer(cvrIter)).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()

        return Pair(null, Closer( allCardsIter))
    }
}

////////////////////////////////////////////////////////////////////
// Create poliing audits where precincts are used to calculate Nb
fun createColoradoPolling(
    topdir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.POLLING, hasStyle = true, riskLimit = .03, contestSampleCutoff = null, nsimEst = 100,
            pollingConfig = PollingConfig()
        )
    }
    val election = ColoradoPolling(electionDetailXmlFile, contestRoundFile, precinctFile, config)

    CreateAudit("corla", topdir, config, election, clear = clear)
    println("createColoradoPolling took $stopwatch")
}


