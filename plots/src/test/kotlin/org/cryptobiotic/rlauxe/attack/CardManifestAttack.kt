package org.cryptobiotic.rlauxe.attack

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CreateAudit
import org.cryptobiotic.rlauxe.audit.CreateElectionIF
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.writeUnsortedPrivateMvrs
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.setPoolAssorterAverages
import org.cryptobiotic.rlauxe.oneaudit.calcOneAuditPoolsFromMvrs
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ContestVotes
import org.cryptobiotic.rlauxe.util.ContestVotesIF
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.showTabs
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateOneAuditPools
import kotlin.test.Test

// Vanessa's attack
class CardManifestAttack {
    val name = "cardManifestAttack"
    var topdir = "$testdataDir/attack/$name"
    val hasStyle = false // TODO hasStyle=false

    @Test
    fun cardManifestAttack() {
        // Let's suppose hasStyles=True and take for example a really simple setup in which
        //- Group A: There are 100 individually-indexable CVRs that don't contain the contest
        //- Group B: There are also 100 pooled CVRs from which 75 contain the contest and, of those, 50 voted for Alice and 25 for Bob

        //// make the mvrs first (truth)
        var mvrCount = 0
        val mvrs = mutableListOf<Cvr>()

        // group A
        repeat(100) {
            mvrs.add(Cvr("mvr$mvrCount", mapOf(2 to intArrayOf(1)))) // nonpooled data: doesnt contain contest 1
            mvrCount++
        }

        // group B
        var poolCount=0
        repeat(50) {
            mvrs.add(Cvr("Pool1-$poolCount", mapOf(1 to intArrayOf(1)), poolId = 1)) // Alice
            poolCount++
        }
        repeat(25) {
            mvrs.add(Cvr("Pool1-$poolCount", mapOf(1 to intArrayOf(2)), poolId = 1)) // Bob
            poolCount++
        }
        repeat(25) {
            mvrs.add(Cvr("Pool1-$poolCount", mapOf(2 to intArrayOf(2)), poolId = 1)) // doesnt contain contest 1
            poolCount++
        }

        //// make the cards (lies)
        // Move the 50 cards containing Alice's votes into Group A, and swap into Group B 50 cards that do not contain the contest
        val groupBcontests = intArrayOf(1, 2)

        var index=0
        mvrCount = 0
        poolCount = 0
        val mcards = mutableListOf<AuditableCard>()

        //// group A, mvr index 0-50 match real mvrs.
        repeat(50) {
            mcards.add(
                AuditableCard(
                    "mvr$mvrCount",
                    index,
                    0L,
                    false,
                    // intArrayOf(2),
                    votes = mapOf(2 to intArrayOf(1)),
                    poolId = null
                )
            )
            mvrCount++
            index++
        }

        // swap into Group B the mvrs index 50-100 cards that do not contain the contest
        // we move these 50 into the pool, when they sample the mvr, contestA is missing
        repeat(50) {
            mcards.add(
                AuditableCard(
                    "Pool1-$poolCount",
                    index,
                    0L,
                    false,
                    //groupBcontests,
                    votes = null, // no votes when pooled
                    poolId = 1
                )
            )
            poolCount++
            index++
        }

        // Move the 50 cards containing Alice's votes into Group A
        // the next mvr 100-150 mvrs contain the pooled votes for alice; but the cards say contest B undervotes, so dont get sampled
        repeat(50) {
            // dont use this
            // possible contests "overrides" the votes when looking to sample.
            // mcards.add(AuditableCard("mvr$mvrCount", mvrCount, 0L, false, intArrayOf(2), votes = mapOf(1 to intArrayOf(1)), poolId=null))

            // substitute cards with contest 2 undervotes
            mcards.add(AuditableCard("mvr$mvrCount",
                index,
                0L,
                false,
                //intArrayOf(2),
                votes = mapOf(2 to intArrayOf(1)),  // move the 50-100 votes to here
                poolId=null))
            mvrCount++
            index++
        }

        // these are Bobs pooled votes that match the mvrs
        repeat(25) {
            // mvr has Bob's votes
            mcards.add(AuditableCard("Pool1-$poolCount", index, 0L, false, votes = null, poolId = 1))
            poolCount++
            index++
        }
        // these are contestB pooled votes that match the mvrs
        repeat(25) {
            // mvr doesnt contain contest 1
            mcards.add(AuditableCard("Pool1-$poolCount", index, 0L, false, votes = null, poolId = 1))
            poolCount++
            index++
        }
        val cards = mcards.toList()

        require(mvrs.size == cards.size)
        ////////////////////////////////////////////////////////////////////////////////

        //// make the Contests with reported votes (lies)
        // - Claim Bob won the election with 25 of the votes in Group B, that 50 were blank, and (truthfully) that the rest of Group B do not contain the contest
        val contestA = Contest(
            ContestInfo("A", 1, mapOf("Alice" to 1, "Bob" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 0, 2 to 25),
            Nc = 75,
            75
        )

        val contestBnc = 125
        val contestB = Contest(
            ContestInfo("B", 2, mapOf("cand1" to 1, "cand2" to 2), SocialChoiceFunction.PLURALITY),
            mapOf(1 to 100, 2 to 25),
            Nc = contestBnc,
            contestBnc
        )
        val contests = listOf(contestA, contestB)
        val infos = mapOf(1 to contestA.info(), 2 to contestB.info())

        //// derive from mvrs
        println("---------------------truth")
        val mvrTabs = tabulateCvrs(Closer(mvrs.iterator()), infos)
        print(showTabs("mvrTabs", mvrTabs))
        val realContests = makeContestsFromCvrs(mvrs)
        val realNps = mvrTabs.mapValues { it.value.ncardsTabulated }
        val realcontestUA = realContests.map {
            ContestWithAssertions(it, true, NpopIn=realNps[it.id]).addStandardAssertions()
        }
        println("true Contest totals")
        realcontestUA.forEach { contestUA -> println(contestUA.showSimple())}

        val cardStyle = Population("groupB", 1, groupBcontests, false)
        val realPools = calcOneAuditPoolsFromMvrs(infos, listOf(cardStyle), mvrs)
        realPools.forEach{ println(it.show()) }
        println("--------------------- end truth")
        ////

        val manifestTabs = tabulateAuditableCards(Closer(cards.iterator()), infos)
        print(showTabs("manifestTabs", manifestTabs))


        // TODO
        //// make the CardPool with reported votes (lies)
        // OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
        //                        val ncards: Int, val regVotes: Map<Int, RegVotesIF>)
        val cardPool = OneAuditPool(
            "groupB", 1, false, 100,
            regVotes = mapOf(
                1 to ContestVotes(1, 1,mapOf(1 to 0, 2 to 25), 75, undervotes = 50), // false
                // 1 to RegVotes(mapOf(1 to 50, 2 to 25), 75, undervotes = 0), // true
                2 to ContestVotes(2, 1, mapOf(1 to 0, 2 to 25), 25, undervotes = 0),  // unchanged
            )
        )
        print(cardPool.show())
        val cardPools = listOf(cardPool)

        // The sample poulation sizes come from the cards
        val Npops = manifestTabs.mapValues { it.value.ncardsTabulated }

        val contestsUA = contests.map {
            ContestWithAssertions(it, true, NpopIn=Npops[it.id]).addStandardAssertions()
        }
        // The OA assort averages come from the card Pools
        setPoolAssorterAverages(contestsUA, cardPools)

        println("false Contest totals")
        contestsUA.forEach { contestUA -> println(contestUA.showSimple())}

        // TODO
        val contestUA = contestsUA.find { it.id == contestA.id }!!

        // check that the card pools agree with the cards
        val poolSums = tabulateOneAuditPools(cardPools, infos)
        print(showTabs("poolSums", poolSums))

        // check that the contests agree with the cards
        val sumWithPools = mutableMapOf<Int, ContestTabulation>()
        sumWithPools.sumContestTabulations(manifestTabs)
        sumWithPools.sumContestTabulations(poolSums)
        print(showTabs("sumWithPools", sumWithPools))
        contests.forEach { contest ->
            if (contest.votes != sumWithPools[contest.id]!!.votes)
                println("*** contest ${contest.id} votes ${contest.votes} != sumWithPools ${sumWithPools[contest.id]!!.votes}")
        }
        println()

        //// create a peristent audit
        val election = CreateElectionForAttack(listOf(contestUA), cards, cardPools, null)

        val auditdir = "$topdir/audit"
        val config = AuditConfig(
            AuditType.ONEAUDIT, contestSampleCutoff = 20000, nsimEst = 10,
        )
        CreateAudit("hideInOtherPoolAttack", config, election, auditDir = "$topdir/audit",)

        val publisher = Publisher(auditdir)
        writeSortedCardsInternalSort(publisher, config.seed)
        writeUnsortedPrivateMvrs(publisher, mvrs, config.seed)

        println("============================================================")
        val resultsvc = RunVerifyContests.runVerifyContests(auditdir, null, true)
        println()
        print(resultsvc)
        if (resultsvc.hasErrors) println("*** Verify fails") else println("*** Verify success")

        println("============================================================")
        var done = false
        while (!done) {
            val lastRound = runRound(inputDir = auditdir)
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }
    }
}

class CreateElectionForAttack(
    val contestsUA: List<ContestWithAssertions>,
    val cards: List<AuditableCard>,
    val populations: List<PopulationIF>?,
    val cardPools: List<OneAuditPoolFromCvrs>?,
):  CreateElectionIF {

    override fun contestsUA() = contestsUA
    override fun populations() = populations
    override fun cards() = Closer( cards.iterator() )
    override fun cardPools() = cardPools
    override fun ncards() = cards.size
}

fun ContestWithAssertions.showSimple() = buildString {
    val contestWithVotes = contest as Contest
    val votesByName = contestWithVotes.votes.map{ (key, value) ->  Pair(contestWithVotes.info.candidateIdToName[key], value) }
    append("Contest ($id) votes=$votesByName Npop=${Npop} Nc=${contestWithVotes.Nc()} undervotes=${contestWithVotes.Nundervotes()}")
}

data class OneAuditPool(override val poolName: String, override val poolId: Int, val hasSingleCardStyle: Boolean,
                        val ncards: Int, val regVotes: Map<Int, ContestVotesIF>) : OneAuditPoolIF {
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun regVotes() = regVotes
    override fun hasContest(contestId: Int) = regVotes[contestId] != null
    override fun ncards() = ncards

    override fun contests() = regVotes.keys.toList().sorted().toIntArray()
    override fun assortAvg() = assortAvg

    override fun votesAndUndervotes(contestId: Int,): Vunder {
        val regVotes = regVotes[contestId]!!         // empty for IRV ...
        return Vunder.fromNpop(contestId, regVotes.undervotes(), ncards(), regVotes.votes, regVotes.voteForN)
    }
}
