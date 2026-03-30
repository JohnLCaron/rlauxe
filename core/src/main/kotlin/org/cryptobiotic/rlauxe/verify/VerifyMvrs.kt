package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.util.ErrorMessages

// see if the mvr, card pairs match
fun verifyMvrCardPairs(mvrCardPairs: List<Pair<AuditableCard, AuditableCard>>, errs: ErrorMessages) {

    mvrCardPairs.forEachIndexed { index, (mvr, card) ->
        val nested = errs.nested("sample $index")
        var hasError = false
        if (mvr.location != card.location || mvr.prn != card.prn || mvr.index != card.index) {
            hasError = true
            nested.add("*** Mvr location, prn, or index does not match card")
        }

        mvr.votes!!.keys.forEach { mvrContestId ->
            if (!card.possibleContests().contains(mvrContestId)) {
                hasError = true
                nested.add("*** Mvr contains contest ${mvrContestId} not contained in card $card")
            }
        }
        if (card.batch.hasSingleCardStyle()) {
            card.batch.possibleContests().forEach { batchContestId ->
                if (!mvr.votes.contains(batchContestId)) {
                    hasError = true
                    nested.add("*** batch contains contest ${batchContestId} not contained in Mvr")
                }
            }
        }

        if (hasError) {
            nested.add("    mvr=${mvr.show()}")
            nested.add("    card=${card.show()}")
        }
    }
}

fun AuditableCard.show() = buildString {
    append("AuditableCard(location='$location', index=$index, sampleNum=$prn, phantom=$phantom")
    if (poolId() != null) append(", poolId=${poolId()}")
    append(", batchName='${batchName()}'")
    append(", has batch contests=${batch.possibleContests().contentToString()}")
    if (votes != null) append(" has vote contests=${votes.keys.toList().sorted()})")
}