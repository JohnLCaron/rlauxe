package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.ClcaErrorTracker
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsFrom
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile

private val logger = KotlinLogging.logger("PersistedMvrManagerTest")
private val checkValidity = true
private val checkFuzz = true

class PersistedMvrManagerTest(auditDir: String, val config: AuditConfig, val contestsUA: List<ContestUnderAudit>) : MvrManagerTestIF, PersistedMvrManager(auditDir) {

    // extract the wanted cards from the cardManifest, optionally fuzz them, and write them to sampleMvrsFile
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>): List<AuditableCard> {
        val cards = findSamples(sampleNumbers, auditableCards())
        val fuzzPct = config.simFuzzPct()
        val sampledMvrs = if (fuzzPct == null) {
            cards // use the cvrs - ie, no errors
        } else { // fuzz the cvrs
            makeFuzzedCardsFrom(contestsUA.map { it.contest }, cards, fuzzPct) // TODO, undervotes=false)
        }

        if (checkFuzz && fuzzPct != null) {
            println("fuzzPct = $fuzzPct")
            val testPairs = sampledMvrs.zip(cards)

            contestsUA.forEach { contestUA ->
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val samplet = ClcaErrorTracker(cassorter.noerror())
                    println("  contest = ${contestUA.id} assertion = ${cassorter.shortName()}")

                    testPairs.forEach { (fcard, card) ->
                        if (card.hasContest(contestUA.id)) {
                            val bassort = cassorter.bassort(fcard.cvr(), card.cvr())
                            samplet.addSample(bassort)
                        }
                    }
                    println("    SampleErrorTracker = ${samplet}")
                }
            }
            // println("fuzzPct ${dfn(fuzzPct!!,3)}: ${avgErrorRates}")
        }

        if (checkValidity) {
            require(sampledMvrs.size == sampleNumbers.size)
            var lastRN = 0L
            sampledMvrs.forEach { mvr ->
                require(mvr.prn > lastRN)
                lastRN = mvr.prn
            }
        }

        val publisher = Publisher(auditDir)
        writeAuditableCardCsvFile(sampledMvrs, publisher.sampleMvrsFile(publisher.currentRound()))
        logger.info{"setMvrsBySampleNumber write sampledMvrs to '${publisher.sampleMvrsFile(publisher.currentRound())}"}
        return sampledMvrs
    }

    // get the wanted sampleNumbers from samplePrnsFile, and call setMvrsBySampleNumber(sampledMvrs) with them.
    fun setMvrsForRoundIdx(roundIdx: Int): List<AuditableCard> {
        val publisher = Publisher(auditDir)
        val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx))
        if (resultSamples is Err) logger.error{"$resultSamples"}
        require(resultSamples is Ok)
        val sampleNumbers = resultSamples.unwrap() // these are the samples we are going to audit.

        return if (sampleNumbers.isEmpty()) {
            logger.error{"***Error sampled Indices are empty for round $roundIdx"}
            emptyList()
        } else {
            setMvrsBySampleNumber(sampleNumbers)
        }
    }
}