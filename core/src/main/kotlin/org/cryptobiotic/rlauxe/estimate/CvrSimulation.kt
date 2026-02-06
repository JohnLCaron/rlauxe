package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.VunderPicker
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.min
import kotlin.math.round

// val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
//var testCvrs = sim.makeCvrs() // includes undervotes and phantoms

fun simulateCvrsWithDilutedMargin(Nc: Int, margin: Double, undervotePct: Double, phantomPct: Double,
                                  Npop: Int = Nc,
                                  limit: Int? = null): Pair<ContestWithAssertions, List<Cvr>> {
    val nvotes = round(Nc * (1.0 - undervotePct - phantomPct))
    val winner = roundToClosest((margin * Nc + nvotes) / 2)
    val loser = roundToClosest(nvotes - winner)
    val Np = roundToClosest(Nc * phantomPct)
    val Nu = roundToClosest(Nc * undervotePct)
    val contest = Contest(
        ContestInfo("standard", 0, mapOf("A" to 0,"B" to 1), choiceFunction = SocialChoiceFunction.PLURALITY),
        mapOf(0 to winner, 1 to loser),
        Nc = Nc,
        Ncast = roundToClosest(nvotes + Nu)
    )
    val cu = ContestWithAssertions(contest, true, NpopIn = Npop)
    val config = AuditConfig(AuditType.CLCA, contestSampleCutoff = limit)
    return Pair( cu, simulateCvrsWithDilutedMargin(cu, config))
}


// simulates Cvrs consistent with vote totals, up to min(Nc, config.contestSampleCutoff)
// handles Polling, CLCA, regular or IRV

fun simulateCvrsWithDilutedMargin(contestUA: ContestWithAssertions, config: AuditConfig): List<Cvr> {
    val contest = contestUA.contest as Contest
    val ncvrs = min( contest.Nc, config.contestSampleCutoff ?: Int.MAX_VALUE)
    val pct = ncvrs/contestUA.Npop.toDouble()
    val missing = contestUA.Npop - (contest.Nphantoms() + contest.Nundervotes() + contest.votes.values.sum()) / contest.info.voteForN
    val voteCounts = contest.votes.map { Pair(intArrayOf(it.key), it.value) }
    val vunder = Vunder(contest.id, -1, voteCounts, contest.Nundervotes(), missing,  contest.info.voteForN)
    // println("vunder = $vunder")

    // val vunder = Vunder.fromNpop(contest.id, contest.Nundervotes(), contestUA.Npop, contest.votes, contest.info.voteForN)
    val nphantoms = roundToClosest(pct*contest.Nphantoms())
    val limit = ncvrs - nphantoms
    return makeVunderCvrs(vunder, nphantoms, "simCvr", limit, null)
}

// If youre going to simulate IRV, you need the VoteConsolidator (or the Cvrs).
// We have the VoteConsolidator in the pools for OneAudit, but not otherwise
fun simulateCvrsForIrv(contestUA: ContestWithAssertions, config: AuditConfig, irvVotes: VoteConsolidator): List<Cvr> {
    val contest = contestUA.contest as RaireContest
    val undervotes = contest.Nundervotes()
    val npop = contestUA.Npop
    val candidateIds = contest.info.candidateIds

    // see ContestTabulation.votesAndUndervotes()
    val missing = npop - undervotes - irvVotes.nvotes()
    val voteCounts = irvVotes.votes.map { (hIntArray, count) ->
        // convert indices back to ids
        val idArray: List<Int> = hIntArray.array.map { candidateIds[it] }
        Pair(idArray.toIntArray(), count)
    }
    val vunder = Vunder(contest.id, null, voteCounts, undervotes, missing, 1)
    val ncvrs = min( contest.Nc, config.contestSampleCutoff ?: Int.MAX_VALUE)
    return makeVunderCvrs(vunder, contest.Nphantoms(), "simIrv", ncvrs, null)
}

fun makeVunderCvrs(vunder: Vunder, phantomCount: Int, prefix: String, limit: Int, poolId: Int?): List<Cvr> {
    val vunderPicker = VunderPicker(vunder)
    val contestId = vunder.contestId

    val rcvrs = mutableListOf<Cvr>()
    var count = 0
    var done = false
    while (!done && count < limit) {
        val cvrId = "${prefix}-${count + 1}"
        val cvb2 = CvrBuilder2(cvrId, phantom = false, poolId = poolId)
        if (vunderPicker.isNotEmpty()) {
            // pick random candidates for the contest
            val useCandidates = vunderPicker.pickRandomCandidatesAndDecrement()
            // add the contest to cvr unless its a novote
            if (useCandidates != null) {
                cvb2.addContest(contestId, useCandidates)
            }
        }

        rcvrs.add(cvb2.build())

        count++
        done = vunderPicker.isEmpty()
    }

    repeat(phantomCount) {
        val cvrb = CvrBuilder2("$prefix-${count++}", phantom=true)
        cvrb.addContest(contestId, intArrayOf())
        rcvrs.add(cvrb.build())
    }

    rcvrs.shuffle()
    return rcvrs
}
