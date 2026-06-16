package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// data class AuditableCard (
//    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle?
//    val votes: Map<Int, IntArray>?, // for CLCA or OneAudit, a map of contest -> the candidate ids voted; must include undervotes; missing for pooled data or polling audits
//                                                                                // when IRV, ranked first to last
//    val poolId: Int?, // for OneAudit, or for setting style
//    val cardStyle: String? = null, // set style in a way that doesnt interfere with onaudit....
//)
class TestAuditableCard {

    @Test
    fun testFromCvr() {
        repeat(1000) {
            val id = Random.nextInt()
            val cvr = makeCvr(id, 11, 15)
            val card = AuditableCard.fromCvr(cvr, it, it.toLong())
            assertEquals(cvr, card.toCvr())
            assertEquals(id.toString(), card.id)
            assertEquals(it, card.index)
            assertEquals(it.toLong(), card.prn)
            assertEquals(false, card.phantom)
            // assertEquals(cvr.votes.keys, card.possibleContests.toSet())
            assertNotNull(card.votes())
            assertNull(card.poolId())
        }
    }

    @Test
    fun testString() {
        val id = Random.nextInt()
        val card1 = AuditableCard.fromVotes ("cvr$id", null, 42, 4422L, false, // intArrayOf(1,2,3),
            votes=mapOf(2 to intArrayOf(2), 3 to intArrayOf(1,2,3), 4 to intArrayOf(4,5,6), 1 to intArrayOf(1), 42 to IntArray(0), 11 to intArrayOf()), poolId=null, styleName="style")
        assertEquals("""AuditableCard(id='cvr$id', index=42, prn=4422, styleName='style', votes= 1:1, 2:2, 3:[1, 2, 3], 4:[4, 5, 6], 11:[], 42:[],)""",
            card1.toString())

        val card2 = AuditableCard ("cvr$id", "loc$id", 42, 4422L, true, styleName="yes", poolId=42,
            card1.contestIds, card1.contestStarts, card1.candidates)
        assertEquals("""AuditableCard(id='cvr$id', location='loc$id', index=42, prn=4422, styleName='yes', phantom=true, poolId=42, votes= 1:1, 2:2, 3:[1, 2, 3], 4:[4, 5, 6], 11:[], 42:[],)""",
            card2.toString())

        assertEquals("""AuditableCard(id='99', index=0, prn=0, styleName='style', phantom=true, votes=null)""",
            AuditableCard.empty("99", true, "style").toString())
    }

    @Test
    fun testEquals() {
        val id = Random.nextInt()
        val card1 = AuditableCard.fromVotes ("cvr$id", null, 42, 4422L, false, // intArrayOf(1,2,3),
            votes=mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), poolId=1, styleName="pool1")
        val card2 = AuditableCard.fromVotes ("cvr$id", null, 42, 4422L, false, // intArrayOf(1,2,3),
            votes=mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), poolId=1, styleName="pool1")
        assertEquals(card1.toCvr(), card2.toCvr())
        assertEquals(card1.hashCode(), card2.hashCode())
        assertEquals(card1, card2)
        assertEquals(card1.toString(), card2.toString())
    }
}

fun makeCvr(id: Int, ncontests: Int, ncandidates: Int, poolId:Int?=null): Cvr {
    val cvrb = CvrBuilder2(id.toString(),  false, poolId=poolId)
    repeat(ncontests) {
        val contestId = Random.nextInt(ncontests)
        val votesForN = Random.nextInt(ncandidates)
        val candidates = IntArray(votesForN) { Random.nextInt(ncandidates) }
        cvrb.replaceContestVotes(contestId, candidates)
    }
    return cvrb.build()
}