package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
            assertEquals(cvr, card.cvr())
            assertEquals(id.toString(), card.location)
            assertEquals(it, card.index)
            assertEquals(it.toLong(), card.prn)
            assertEquals(false, card.phantom)
            // assertEquals(cvr.votes.keys, card.possibleContests.toSet())
            assertNotNull(card.votes)
            assertNull(card.poolId)
        }
    }

    @Test
    fun testEqualsAndString() {
        val id = Random.nextInt()
        val card1 = AuditableCard ("cvr$id", 42, 4422L, false, // intArrayOf(1,2,3),
            mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), 1)
        val card2 = AuditableCard ("cvr$id", 42, 4422L, false, // intArrayOf(1,2,3),
            mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), 1)
        assertEquals(card1.cvr(), card2.cvr())
        assertEquals(card1.hashCode(), card2.hashCode())
        assertEquals(card1, card2)
        assertEquals(card1.toString(), card2.toString())

        val expected = """AuditableCard(desc='cvr$id', index=42, sampleNum=4422, phantom=false, poolId=1)
   contest 1: [1, 2, 3]
   contest 2: [4, 5, 6]
   contest 3: [0, 1]
"""
        assertEquals(expected, card1.toString())
    }
}

fun makeCvr(id: Int, ncontests: Int, ncandidates: Int, poolId:Int?=null): Cvr {
    val cvrb = CvrBuilder2(id.toString(),  false, poolId=poolId)
    repeat(ncontests) {
        val contestId = Random.nextInt(ncontests)
        val votesForN = Random.nextInt(ncandidates)
        val candidates = IntArray(votesForN) { Random.nextInt(ncandidates) }
        cvrb.addContest(contestId, candidates)
    }
    return cvrb.build()
}