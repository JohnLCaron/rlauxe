package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CreateAudit
import org.cryptobiotic.rlauxe.audit.CreateElection
import org.cryptobiotic.rlauxe.audit.OneAuditConfig
import org.cryptobiotic.rlauxe.audit.OneAuditStrategyType
import org.cryptobiotic.rlauxe.audit.writeMvrsForRound
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.audit.writeSortedMvrs
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.oneaudit.CardPool
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.RegVotes
import org.cryptobiotic.rlauxe.util.showTabs
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCardPools
import kotlin.test.Test

// Vanessa's attack
class HideInOtherPoolAttackV {
    val name = "hideInOtherPoolAttackV"
    var topdir = "/home/stormy/rla/attack/$name"
    val hasStyle = false

    @Test
    fun hideInOtherPoolAttack() {
        // Let's suppose hasStyles=True and take for example a really simple setup in which
        //- Group A: There are 100 individually-indexable CVRs that don't contain the contest
        //- Group B: There are also 100 pooled CVRs from which 75 contain the contest and, of those, 50 voted for Alice and 25 for Bob

        // make the mvrs first
        var mvrCount = 0
        val mvrs = mutableListOf<Cvr>()

        // group A
        repeat(100) {
            mvrs.add(Cvr("mvr$mvrCount", mapOf(2 to intArrayOf(1))))
            mvrCount++
        }

        // group B
        repeat(50) {
            mvrs.add(Cvr("mvr$mvrCount", mapOf(1 to intArrayOf(1)), poolId = 1)) // Alice
            mvrCount++
        }
        repeat(25) {
            mvrs.add(Cvr("mvr$mvrCount", mapOf(1 to intArrayOf(2)), poolId = 1)) // Bob
            mvrCount++
        }
        repeat(25) {
            mvrs.add(Cvr("mvr$mvrCount", mapOf(2 to intArrayOf(2)), poolId = 1)) // doesnt contain contest 1
            mvrCount++
        }

        // - Claim Bob won the election with 25 of the votes in Group B, that 50 were blank, and (truthfully) that the rest of Group B do not contain the contest
        val contestA = Contest(
            ContestInfo("A", 1, mapOf("Alice" to 1, "Bob" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 0, 2 to 25),
            Nc = 75,
            75
        )

        val contestBnc = 75
        val contestB = Contest(
            ContestInfo("B", 2, mapOf("Ham" to 1, "Pam" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to contestBnc, 2 to 0),
            Nc = contestBnc,
            contestBnc
        )
        val contests = listOf(contestA, contestB)
        val infos = mapOf(1 to contestA.info(), 2 to contestB.info())

        // make the cards that match the reported outcome
        // Move the 50 cards containing Alice's votes into Group A, and move into Group B 50 cards that do not contain the contest
        val groupBcontests = intArrayOf(1, 2)

        mvrCount = 0
        val mcards = mutableListOf<AuditableCard>()

        //// group A
        repeat(50) {
            mcards.add(
                AuditableCard(
                    "mvr$mvrCount",
                    mvrCount,
                    0L,
                    false,
                    intArrayOf(2),
                    votes = mapOf(2 to intArrayOf(1)),
                    poolId = null
                )
            )
            mvrCount++
        }

        // swap into Group B 50 cards that do not contain the contest
        repeat(50) {
            mcards.add(
                AuditableCard(
                    "mvr$mvrCount",
                    mvrCount,
                    0L,
                    false,
                    groupBcontests,
                    votes = mapOf(2 to intArrayOf(1)),
                    poolId = 1
                )
            )
            mvrCount++
        }

        // Move the 50 cards containing Alice's votes into Group A
        repeat(50) {
            // mvr has Alice's votes, swap with 50 cards in group A
            // possible contests "overrides" the votes when looking to sample. TODO: check possibles contain votes.keys
            mcards.add(AuditableCard("mvr$mvrCount", mvrCount, 0L, false, intArrayOf(2), votes = mapOf(1 to intArrayOf(1)), poolId=null))
            // substitute cards with contest 2 undervotes
            // mcards.add(AuditableCard("mvr$mvrCount", mvrCount, 0L, false, intArrayOf(2), votes = mapOf(2 to intArrayOf()), poolId=null))
            mvrCount++
        }

        repeat(25) {
            // mvr has Bob's votes
            mcards.add(AuditableCard("mvr$mvrCount", mvrCount, 0L, false, groupBcontests, votes = null, poolId = 1))
            mvrCount++
        }
        repeat(25) {
            // mvr doesnt contain contest 1
            mcards.add(AuditableCard("mvr$mvrCount", mvrCount, 0L, false, groupBcontests, votes = null, poolId = 1))
            mvrCount++
        }
        val cards = mcards.toList()

        require(mvrs.size == cards.size)
        ////////////////////////////////////////////////////////////////////////////////

        val manifestTabs = tabulateAuditableCards(Closer(cards.iterator()), infos)
        println(showTabs("manifestTabs", manifestTabs))

        // data class RegVotes(override val votes: Map<Int, Int>, val ncards: Int, val undervotes: Int): RegVotesIF {
        val cardPool = CardPool(
            "groupB", 1, 100,
            regVotes = mapOf(
                1 to RegVotes(mapOf(1 to 0, 2 to 25), 75, undervotes = 50),
                2 to RegVotes(mapOf(1 to 25, 2 to 0), 25, undervotes = 0),
            )
        )
        println(cardPool)
        val cardPools = listOf(cardPool)

        // The Nbs come from the cards
        val Nbs = manifestTabs.mapValues { it.value.ncards }

        val contestsUA = contests.map {
            ContestUnderAudit(it, true, hasStyle = hasStyle, NpopIn=Nbs[it.id]).addStandardAssertions()
        }
        // The OA assort averages come from the card Pools
        addOAClcaAssortersFromMargin(contestsUA, cardPools, hasStyle=hasStyle)

        val contestUA = contestsUA.find { it.id == contestA.id }!!

        // check that the card pools agree with the cards
        val poolSums = tabulateCardPools(cardPools, infos)
        println(showTabs("poolSums", poolSums))

        val sumWithPools = mutableMapOf<Int, ContestTabulation>()
        sumWithPools.sumContestTabulations(manifestTabs)
        sumWithPools.sumContestTabulations(poolSums)
        print(showTabs("sumWithPools", sumWithPools))
        contests.forEach { contest ->
            if (contest.votes != sumWithPools[contest.id]!!.votes)
                println("*** contest ${contest.id} votes ${contest.votes} != sumWithPools ${sumWithPools[contest.id]!!.votes}")
        }
        println()

        //     val contestsUA: List<ContestUnderAudit>,
        //    val cardPools: List<CardPoolIF>,
        //    val cardManifest: List<AuditableCard>
        val election = CreateElection(listOf(contestUA), cardPools, cards)

        val auditdir = "$topdir/audit"
        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = hasStyle, contestSampleCutoff = 20000, nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        CreateAudit("hideInOtherPoolAttack", topdir, config, election)

        val publisher = Publisher(auditdir)
        writeSortedCardsInternalSort(publisher, config.seed)
        writeSortedMvrs(publisher, mvrs, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(resultsvc)

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = false, quiet = true)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
            if (!done) writeMvrsForRound(publisher, lastRound!!.roundIdx)
        }
    }
}

// since group A is not pooled, the only way to "Move the 50 cards containing Alice's votes into (the MVR pile corresponding to) Group A" is to add a CVR.
// but then the card is seen as having contest 1 on it, and is included in the sample.

// dont touch Group B and just "swap into (the MVR pile corresponding to) Group B 50 cards that do not contain the contest
