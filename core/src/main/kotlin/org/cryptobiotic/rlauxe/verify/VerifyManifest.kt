package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.collections.forEach
import kotlin.use

class VerifyManifest() {
    val results = VerifyResults()

    fun verifyManifest(
        seed: Long,
        cards: CloseableIterable<AuditableCard>,
    ) {
        results.addMessage("VerifyManifest")

        val locationSet = mutableSetOf<String>()
        val indexSet = mutableSetOf<Int>()
        val indexList = mutableListOf<Pair<Int, Long>>()

        var count = 0
        var lastCard: AuditableCard? = null
        cards.iterator().use { cardIter ->
            while (cardIter.hasNext()) {
                val card = cardIter.next()

                // 1. 1. Check that all card locations and indices are unique, and the card prns are in ascending order
                if (!locationSet.add(card.location)) {
                    results.addError("$count duplicate card.location ${card.location}")
                }

                if (!indexSet.add(card.index)) {
                    results.addError("$count duplicate card.index ${card.index}")
                }

                if (lastCard != null) {
                    if (card.prn <= lastCard.prn) {
                        results.addError("$count prn out of order lastCard = $lastCard card = ${card}")
                    }
                }

                indexList.add(Pair(card.index, card.prn))
                lastCard = card
                count++
            }
        }

        // 2. Given the seed and the PRNG, check that the PRNs are correct and are assigned sequentially by index.
        val indexSorted = indexList.sortedBy { it.first }
        val prng = Prng(seed)
        indexSorted.forEach {
            val prn = prng.next()
            require(it.second == prn)
        }
        results.addMessage("verify $count cards")
    }

}