package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsFrom
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrns

private val logger = KotlinLogging.logger("PersistedMvrManagerTest")
private val checkValidity = true

class PersistedMvrManagerTest(auditDir: String, config: AuditConfig, contestsUA: List<ContestUnderAudit>)
    : MvrManagerTestIF, PersistedMvrManager(auditDir, config, contestsUA) {

    // extract the cards with sampleNumbers from the cardManifest, optionally fuzz them, and write them to sampleMvrsFile
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>, roundIdx: Int): List<AuditableCard> {
        val cards = findSamples(sampleNumbers, auditableCards())

        // get maybe-fuzzed mvrs from previous round, use them again
        val previousMvrs = if (roundIdx == 1) emptyList() else readAuditableCardCsvFile( publisher.sampleMvrsFile(roundIdx-1))
        val previousPrns = if (roundIdx == 1) emptyList() else readSamplePrns(publisher.samplePrnsFile(roundIdx-1))
        val previousPrnsSet = previousPrns.toSet()
        require(previousPrns.size == previousMvrs.size)
        require(previousPrnsSet.size == previousMvrs.size)

        val simFuzzPct = config.simFuzzPct()
        val sampledMvrs = if (simFuzzPct == null) {
            cards // use the cvrs - ie, no errors
        } else { // fuzz the new cvrs only
            val wantSet = sampleNumbers.toSet()
            val wantPrevious = previousMvrs.filter{ wantSet.contains(it.prn) }
            val newCards = cards.filter{ !previousPrnsSet.contains(it.prn) }
            val newFuzzedCards = makeFuzzedCardsFrom(contestsUA.map { it.contest.info() }, newCards, simFuzzPct)
            wantPrevious + newFuzzedCards
        }
        require(cards.size == sampledMvrs.size)

        if (checkValidity) {
            require(sampledMvrs.size == sampleNumbers.size)
            var lastRN = 0L
            sampledMvrs.forEachIndexed { idx, mvr ->
                if (mvr.prn <= lastRN)
                    print("hey")
                if (mvr.location != cards[idx].location)
                    print("there")

                lastRN = mvr.prn
            }
        }

        val publisher = Publisher(auditDir)
        writeAuditableCardCsvFile(sampledMvrs, publisher.sampleMvrsFile(roundIdx)) // test sampleMvrs
        logger.info{"setMvrsBySampleNumber write sampledMvrs to '${publisher.sampleMvrsFile(roundIdx)}"}
        return sampledMvrs
    }

    // get the wanted sampleNumbers from samplePrnsFile, and call setMvrsBySampleNumber(sampledMvrs) with them.
    fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        val sampleNumbers = readSamplePrns(publisher.samplePrnsFile(roundIdx))

        return if (sampleNumbers.isEmpty()) {
            logger.error{"***Error sampled Indices are empty for round $roundIdx"}
            emptyList()
        } else {
            setMvrsBySampleNumber(sampleNumbers, roundIdx)
        }
    }
}