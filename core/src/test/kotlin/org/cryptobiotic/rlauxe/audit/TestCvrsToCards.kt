package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Closer
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random
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
class TestCvrsToCards {

    @Test
    fun testCvrsWithStylesToCardsForClca() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))

        val auditType = AuditType.CLCA

        // simple hasStyle with or without poolid. aka "cvrs are complete".
        var hasCardStyles = false

        var cvr = cvrr.copy(poolId=null)
        // with no population, only the cvrs are present.
        var target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null)
        var card = target.next()
        testOneTarget("** clca complete cvrs", cvr, card, auditType, hasCardStyles, null)

        cvr = cvrr.copy(poolId=1)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null)
        card = target.next()
        testOneTarget("clca hasStyle and poolIds", cvr, card, auditType, hasCardStyles, expectStyle = null)

        val cardStyle = Population("you", 1, intArrayOf(0,1,2,3,4), false)
        // doesnt make sense to use; hasStyle means use cvr
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=listOf(cardStyle))
        card = target.next()
        testOneTarget("clca hasStyle and poolIds and styles", cvr, card, auditType, hasCardStyles, cardStyle.name(), expectPop = cardStyle)

        // noStyle means votes isnt complete, so you need cardStyles and poolIds. should test if votes cubset of cardpool contests
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=listOf(cardStyle))
        card = target.next()
        testOneTarget("** clca incomplete cvrs", cvr, card, auditType, hasCardStyles, cardStyle.name(), expectPop = cardStyle)

        // what if you dont supply the poolId?
        cvr = cvrr.copy(poolId=null)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=listOf(cardStyle))
        card = target.next()
        assertEquals(null, card.cardStyle, "no poolId")

        // what if you dont supply the cardStyles? FAIL
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null)
        card = target.next()
        assertEquals(null, card.cardStyle, "no pools")
    }

    @Test
    fun testCvrsWithStylesToCardsForPolling() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2), poolId=1)

        var auditType = AuditType.POLLING
        var hasCardStyles = false

        val expectedMessage = "AuditableCard must have votes, possibleContests, cardStyle, or population"
        var cvr = cvrr.copy(poolId=null)  // no poolId
        var target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null, )
        testOneTarget("** poll with no pools", cvr, target.next(), auditType, hasCardStyles, "all")

        cvr = cvrr.copy(poolId=1) // poolId but no pools
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null)
        testOneTarget("** poll with poolid but no pool", cvr, target.next(), auditType, hasCardStyles, expectStyle = "all")

        // what happens if the poolId doesnt match ??
        val cardStyle = Population("cardstyle1", 1,intArrayOf(0,1,2,3,4), false)
        cvr = cvrr.copy(poolId=2) // poolId doesnt match
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=null)
        testOneTarget("** poll with pools but wrong poolid", cvr, target.next(), auditType, hasCardStyles, expectStyle = "all")

        // successfully use pool 1
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(listOf(cvr).iterator()), phantomCvrs=null, populations=listOf(cardStyle))
        testOneTarget("** poll with pool", cvr, target.next(), auditType, hasCardStyles, cardStyle.name(), expectPop = cardStyle)
    }

    @Test
    fun testCvrsWithStylesToCardsForOA() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))
        val cvrc = cvrr.copy(poolId=null)
        val cvrp = cvrr.copy(poolId=2)
        val cvrs = listOf(cvrc, cvrp)

        var auditType = AuditType.ONEAUDIT
        var hasCardStyles = true

        val cardStyle = Population("oapool2", 2,intArrayOf(0,1,2), false)
        var target = CvrsWithPopulationsToCardManifest(auditType, Closer(cvrs.iterator()), phantomCvrs=null, populations=listOf(cardStyle), )
        testOneTarget("oa hasStyle", cvrc, target.next(), auditType, hasCardStyles, null)
        testOneTarget("oa hasStyle pooled", cvrp, target.next(), auditType, hasCardStyles, cardStyle.name(), expectPop = cardStyle)

        // noStyle means must supply the list of possibleContests for pooled data and cvrs
        hasCardStyles = true
        target = CvrsWithPopulationsToCardManifest(auditType, Closer(cvrs.iterator()), phantomCvrs=null, populations=listOf(cardStyle), )
        testOneTarget("oa noStyle", cvrc, target.next(), auditType, hasCardStyles, null)
        testOneTarget("oa noStyle pooled", cvrp, target.next(), auditType, hasCardStyles, cardStyle.name(), expectPop = cardStyle)
    }

    fun testOneTarget(what: String, cvr: Cvr, card: AuditableCard, auditType: AuditType, hasCardStyles: Boolean, expectStyle:String?, expectPop:Population?=null) {
        println("$what [$auditType hasCardStyles:$hasCardStyles]:")
        println("  ${cvr.show()}")
        println("  ${card.show()}")
        println()

        if (auditType.isClca()) {
            assertEquals(cvr, card.cvr())
            assertNotNull(card.votes)
            assertEquals(cvr.votes, card.votes)
            assertEquals(null, card.poolId, "poolId")

        } else if (auditType.isPolling()) {
            assertNull(card.votes)

        }  else if (auditType.isOA()) {
            assertEquals(cvr.poolId, card.poolId, "poolId")
        }

        assertEquals(cvr.id, card.location)
        assertEquals(cvr.phantom, card.phantom)
        assertEquals(expectStyle, card.cardStyle, "cardStyle")

        val styleContests = expectPop?.possibleContests?.toList()?.toSet() ?: emptySet()
        val expectContests = when (auditType) {
            AuditType.ONEAUDIT -> {
                if (card.votes != null)
                    card.votes.keys.toSet() // not correct; noStyles means cvrs are incomplete,
                                            // but then we need poolId to see what pool; interferes with pooled vs nonpooled cards
                                            // so cant do this case with Cvr as input
                else
                    styleContests
            }
            AuditType.CLCA -> if (hasCardStyles) styleContests else card.votes!!.keys.toSet()
            AuditType.POLLING -> expectPop?.contests()?.toSet() ?: emptySet()
        }
        if (expectContests != card.contests().toSet())
            print("${card.contests()}")
        assertEquals(expectContests, card.contests().toSet(), "card.contests()")
    }
}


fun Cvr.show() = buildString {
    append("$id phantom=$phantom votes={")
    votes.toSortedMap().forEach { (id, cands) -> append("$id: ${cands.contentToString()}, ") }
    append("} poolId=$poolId")
}

fun AuditableCard.show() = buildString {
    append("$location phantom=$phantom")
    if (votes != null) {
        append(" votes={")
        votes.toSortedMap().forEach { (id, cands) -> append("$id: ${cands.contentToString()}, ") }
        append("}")
    }
    append(" poolId=$poolId")
    append(" contests=${contests().contentToString()}")
    append(" cardStyle=$cardStyle")

}
