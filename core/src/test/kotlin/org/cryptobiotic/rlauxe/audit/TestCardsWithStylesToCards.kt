package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.Closer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
//
// class CardsWithStylesToCards(
//    val type: AuditType,
//    val hasStyle: Boolean,
//    val cards: CloseableIterator<AuditableCard>,
//    phantomCards : List<AuditableCard>?,
//    styles: List<CardStyleIF>?,
//)
class TestCardsWithStylesToCards {

    @Test
    fun testCardsWithStylesToCardsForClca() {
        val cardOrg = AuditableCard ("cardOrg", 42, 0L, false, intArrayOf(1,2,3),
            mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), 1)
        
        val auditType = AuditType.CLCA

        // simple hasStyle with or without poolid. aka "cvrs are complete".
        var hasStyle = true
        var hasCardStyles = false
        var hasPoolId = false

        var cvr = cardOrg.copy(cardStyle=null)
        var target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=null, )
        var card = target.next()
        testOneTarget("** clca complete cvrs", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, null)

        hasPoolId = true
        cvr = cardOrg.copy(cardStyle="no")
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=null)
        card = target.next()
        testOneTarget("clca hasStyle and poolIds", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)

        val styleName = "yes"
        val cardStyle = CardStyle(styleName, 1, emptyList(), listOf(0,1,2,3,4))
        // doesnt make sense to use; hasStyle means use cvr
        hasStyle = true
        hasCardStyles = true
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("clca hasStyle and poolIds and styles", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // noStyle means votes isnt complete, so you need cardStyles and poolIds. should test if votes cubset of cardpool contests
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("** clca incomplete cvrs", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the poolId? FAIL
        hasStyle = false
        hasCardStyles = true
        hasPoolId = false
        cvr = cardOrg.copy(cardStyle=null)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=listOf(cardStyle))
        card = target.next()
        // testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the cardStyles? FAIL
        hasStyle = false
        hasCardStyles = false
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=null)
        card = target.next()
       //  testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)
    }

    @Test
    fun testCardsWithStylesToCardsForPolling() {
        val cardOrg = AuditableCard ("cardOrg", 42, 0L, false, intArrayOf(1,2,3),
            mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), poolId=null, cardStyle="yes")

        val auditType = AuditType.POLLING
        var hasStyle = true
        var hasCardStyles = false
        var hasPoolId = false

        var cvr = cardOrg.copy(poolId=null)
        var target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=null, )
        var card = target.next()
        testOneTarget("polling hasStyle", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, null)

        hasPoolId = true
        cvr = cardOrg.copy(cardStyle="no")
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=null)
        card = target.next()
        testOneTarget("polling hasStyle and poolIds", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)

        // what happens if the poolId doesnt match ??
        val styleName = "yes"
        val cardStyle = CardStyle(styleName, 1, emptyList(), listOf(0,1,2,3,4))
        hasStyle = true
        hasCardStyles = true
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("polling hasStyle and poolIds and styles", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // noStyle means must supply the list of possibleContests
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCards=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("** poll noStyle", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the poolId? FAIL
        hasStyle = false
        hasCardStyles = true
        hasPoolId = false
        cvr = cardOrg.copy(cardStyle=null)
        assertFailsWith<RuntimeException> {
            target = CardsWithStylesToCardManifest(
                auditType,
                cvrsAreComplete = hasStyle,
                Closer(listOf(cvr).iterator()),
                phantomCards = null,
                styles = listOf(cardStyle)
            )
            card = target.next()
        }

        // what if you dont supply the cardStyles? FAIL
        hasStyle = false
        hasCardStyles = false
        hasPoolId = true
        cvr = cardOrg.copy(cardStyle=styleName)
        assertFailsWith<RuntimeException> {
            target = CardsWithStylesToCardManifest(
                auditType,
                cvrsAreComplete = hasStyle,
                Closer(listOf(cvr).iterator()),
                phantomCards = null,
                styles = null
            )
            card = target.next()
        }
    }

    @Test
    fun testCardsWithStylesToCardsForOA() {
        val styleName1 = "oapool1"
        val styleName2 = "oapool2"

        val cardOrg = AuditableCard ("cardOrg", 42, 0L, false, intArrayOf(1,2,3),
            mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), 1)
        val cvrc = cardOrg.copy(cardStyle = null)
        val cvrp = cardOrg.copy(cardStyle = styleName1)
        val cvrs = listOf(cvrc, cvrp)

        var auditType = AuditType.ONEAUDIT
        var hasStyle = true
        var hasCardStyles = true
        var hasPoolId = true

        val cardStyle1 = CardStyle(styleName1, 1, emptyList(), listOf(0,1,2))
        val cardStyle2 = CardStyle(styleName2, 2, emptyList(), listOf(0,1,2,3,4,7))
        val cardStyles = listOf(cardStyle1, cardStyle2)

        // hasStyle means must supply the list of possibleContests only for pooled data
        var target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(cvrs.iterator()), phantomCards=null, cardStyles, )
        testOneTarget("oa hasStyle", cvrc, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, null)
        testOneTarget("oa hasStyle pooled", cvrp, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, cardStyle1)

        // noStyle means must supply the list of possibleContests for pooled data and cvrs
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        val cvrp2 = cardOrg.copy(cardStyle = styleName2)
        val cvrs2 = listOf(cvrp, cvrp2)
        target = CardsWithStylesToCardManifest(auditType, cvrsAreComplete=hasStyle, Closer(cvrs2.iterator()), phantomCards=null, cardStyles, )
        testOneTarget("oa noStyle", cvrc, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, cardStyle1)
        testOneTarget("oa noStyle pooled", cvrp, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, cardStyle2)
    }

    fun testOneTarget(what: String, cardOrg: AuditableCard, card: AuditableCard, auditType: AuditType, hasStyle: Boolean, hasPoolId: Boolean, hasCardStyles: Boolean, expectStyle:CardStyle?) {
        println("$what [$auditType hasStyle=$hasStyle hasPoolId:$hasPoolId hasCardStyles:$hasCardStyles]:")
        println("  ${cardOrg.show()}")
        println("  ${card.show()}")
        println()

        if (auditType.isClca()) {
            // assertEquals(cardOrg, card.cvr())
            assertNotNull(card.votes)
            assertEquals(cardOrg.votes, card.votes)

        } else if (auditType.isPolling()) {
            assertNull(card.votes)
        }

        assertEquals(cardOrg.location, card.location)
        assertEquals(cardOrg.phantom, card.phantom)
        assertEquals(cardOrg.poolId, card.poolId, "poolId")
        assertEquals(expectStyle?.name(), card.cardStyle, "cardStyle")

        val expectPossibleContests = when (auditType) {
            AuditType.ONEAUDIT -> if (card.cardStyle == null && hasStyle) emptySet() else expectStyle?.contests()?.toSet() ?: emptySet()
            AuditType.CLCA -> if (hasStyle) emptySet() else expectStyle?.contests()?.toSet() ?: emptySet()
            AuditType.POLLING -> expectStyle?.contests()?.toSet() ?: cardOrg.contests().toSet()
        }
        assertEquals(expectPossibleContests, card.possibleContests.toSet(), "possibleContests")

        val expectContests = when (auditType) {
            AuditType.ONEAUDIT -> {
                if (card.votes != null && hasStyle)
                    card.votes.keys.toSet()
                else if (card.cardStyle == expectStyle!!.name) {
                    expectStyle.contests().toSet()
                } else {
                    expectPossibleContests
                }
            }
            AuditType.CLCA -> if (hasStyle) card.votes!!.keys.toSet() else expectPossibleContests
            AuditType.POLLING -> expectStyle?.contests()?.toSet() ?: cardOrg.contests().toSet()
        }
        assertEquals(expectContests, card.contests().toSet(), "card.contests()")
    }
}
