package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsForClca
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrns

private val logger = KotlinLogging.logger("PersistedMvrManagerTest")
private val checkValidity = true

class PersistedMvrManagerTest(auditDir: String, config: AuditConfig, contestsUA: List<ContestWithAssertions>)
    : MvrManagerTestIF, PersistedMvrManager(auditDir, config, contestsUA) {

    // extract the cards with sampleNumbers from the cardManifest, optionally fuzz them, and write them to sampleMvrsFile
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>, round: Int): List<AuditableCard> {
        val cards = findSamples(sampleNumbers, auditableCards())

        var lastRN = 0L
        cards.forEachIndexed { idx, mvr ->
            if (mvr.prn <= lastRN) {
                logger.error { "findSamples of order prn" }
                throw RuntimeException("findSamples of order prn")
            }
            lastRN = mvr.prn
        }

        // get maybe-fuzzed mvrs from previous round, use them again
        val previousMvrs = if (round == 1) emptyList() else readAuditableCardCsvFile( publisher.sampleMvrsFile(round-1))
        val previousPrns = if (round == 1) emptyList() else readSamplePrns(publisher.samplePrnsFile(round-1))
        val previousPrnsSet = previousPrns.toSet()
        require(previousPrns.size == previousMvrs.size)
        require(previousPrnsSet.size == previousMvrs.size)

        val mvrFuzzPct = config.mvrFuzzPct() // TODO should be independent of the simulation
        val sampledMvrs = if (mvrFuzzPct == 0.0) {
            cards // use the cvrs - ie, no errors
        } else { // fuzz the new cvrs only; doesnt work for polling since we dont have cvrs
            if (config.isPolling) {
                throw RuntimeException("cant fuzz polling audit; no cvrs!")
            }
            val wantSet = sampleNumbers.toSet()
            require(sampleNumbers.size == wantSet.size)

            // the previous samples probably include some ballots we dont want. just get the ones we want
            val wantPrevious = previousMvrs.filter{ wantSet.contains(it.prn) }

            // get the new cards not in the previous sample
            val newCards = cards.filter{ !previousPrnsSet.contains(it.prn) }

            // and fuzz them TODO use Vunder for OneAudit ?
            val newFuzzedCards = makeFuzzedCardsForClca(contestsUA.map { it.contest.info() }, newCards, mvrFuzzPct)

            // then the cards we want are the previous cards and the new fuzzed cards
            // cant assume they are sorted by prn

            val mvrs2 = mutableListOf<AuditableCard>()
            mvrs2.addAll(wantPrevious + newFuzzedCards)
            mvrs2.sortBy{ it.prn}

            require(cards.size == mvrs2.size)
            require(mvrs2.size == sampleNumbers.size)

            var count = 0
            var lastRN = 0L
            mvrs2.forEachIndexed { idx, mvr ->
                if (mvr.prn <= lastRN) {
                    logger.error { "setMvrsBySampleNumberout of order prn" }
                    throw RuntimeException("setMvrsBySampleNumberout of order prn")
                }
                lastRN = mvr.prn
                count++
            }

            val z = mvrs2.zip(cards)
            z.forEach { (mvr, card) ->
                if (mvr.prn != card.prn) {
                    logger.error { "setMvrsBySampleNumberout bad location mvr=${mvr.location} ${mvr.prn}  card=${card.location} ${card.prn}" }
                    throw RuntimeException("setMvrsBySampleNumberout bad location mvr=${mvr.location} card=${card.location} ")
                }
            }

            mvrs2
        }

        val publisher = Publisher(auditDir)
        writeAuditableCardCsvFile(sampledMvrs, publisher.sampleMvrsFile(round)) // test sampleMvrs
        logger.info{"setMvrsBySampleNumber write sampledMvrs to '${publisher.sampleMvrsFile(round)}"}
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