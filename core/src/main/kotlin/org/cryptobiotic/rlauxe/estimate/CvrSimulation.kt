package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.oneaudit.Vunder
import org.cryptobiotic.rlauxe.oneaudit.VunderPicker
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.min
import kotlin.math.round

// val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
//var testCvrs = sim.makeCvrs() // includes undervotes and phantoms

//// used by estimateSampleSizes
//// only used by test
fun simulateCvrsFromMargin(Nc: Int, margin: Double, undervotePct: Double, phantomPct: Double, Npop: Int = Nc,
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
    return Pair( cu, simulateCvrsForContest(cu, config))
}

// simulate the polling mvrs once, for all the assertions for this contest
// scale number of cvrs to config.contestSampleCutoff if that exists
fun simulateCvrsForContest(contestUA: ContestWithAssertions, config: AuditConfig): List<Cvr> {
    val contest = contestUA.contest as Contest
    val ncvrs = min( contest.Nc, config.contestSampleCutoff ?: Int.MAX_VALUE)
    val pct = ncvrs/contestUA.Npop.toDouble()
    val missing = contestUA.Npop - (contest.Nphantoms() + contest.Nundervotes() + contest.votes.values.sum()) / contest.info.voteForN
    val voteCounts = contest.votes.map { Pair(intArrayOf(it.key), it.value) }
    val vunder = Vunder(contest.id, null, voteCounts, contest.Nundervotes(), missing,  contest.info.voteForN)
    // println("vunder = $vunder")

    // val vunder = Vunder.fromNpop(contest.id, contest.Nundervotes(), contestUA.Npop, contest.votes, contest.info.voteForN)
    val nphantoms = roundToClosest(pct*contest.Nphantoms())
    val limit = ncvrs - nphantoms
    return makeVunderCvrs(vunder, nphantoms, "simCvr", limit, null)
}

// i think you need to do this by population, which is where you get hasSingleCardStyle
fun ContestWithAssertions.votesAndUndervotes(hasSingleCardStyle: Boolean): Vunder {
    val contest = this.contest as Contest

    val voteCounts = contest.votes.map { Pair(intArrayOf(it.key), it.value) }
    val voteSum = contest.votes.values.sum()
    val voteForN = contest.info.voteForN

    val result = if (hasSingleCardStyle) {
        // if hasSingleCardStyle, then missing has to be zero
        // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        val undervotes = this.Npop * voteForN - voteSum
        Vunder(contest.id, null, voteCounts, undervotes, 0, voteForN)
    } else {
        val missing = this.Npop - (contest.Nundervotes() + voteSum) / voteForN
        Vunder(contest.id, null, voteCounts, contest.Nundervotes(), missing, voteForN)
    }

    return result
}


fun makeVunderCvrs(vunder: Vunder, nphantoms: Int, prefix: String, limit: Int, poolId: Int?): List<Cvr> {
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
                cvb2.replaceContestVotes(contestId, useCandidates)
            }
        }

        rcvrs.add(cvb2.build())

        count++
        done = vunderPicker.isEmpty()
    }

    repeat(nphantoms) {
        val cvrb = CvrBuilder2("$prefix-${count++}", phantom=true)
        cvrb.replaceContestVotes(contestId, intArrayOf())
        rcvrs.add(cvrb.build())
    }

    rcvrs.shuffle()
    return rcvrs
}
