package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.verify.AssortAvg
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.String
import kotlin.use

/** Calculate CLCA Assort averages by reading throun the entire CardManifest.
 * TODO: perhaps this only works when there are no errors ??
 */
object RunCalcAssortAvg {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunCalcAssortAvg")
        val auditDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val contestId by parser.option(
            ArgType.Int,
            shortName = "contest",
            description = "contest id"
        ).required()
        val assertionName by parser.option(
            ArgType.String,
            shortName = "assertion",
            description = "assertion short name"
        ).required()

        try {
            parser.parse(args)

            val auditRecord = AuditRecord.readFrom(auditDir)
            if (auditRecord == null) {
                println("auditRecord not found at $auditDir")
                return
            }
            require (auditRecord is AuditRecord)
            val config = auditRecord.config
            println("auditRecord in $auditDir isOA=${config.isOA}")

            val contest = auditRecord.contests.find { it.id == contestId }
            if (contest == null) {
                println("contest id = $contestId not found")
                return
            }
            println("contest $contestId has Npop=${contest.Npop}")

            val clcaAssertion = contest.clcaAssertions.find { it.assorter.shortName() == assertionName }
            if (clcaAssertion == null) {
                println("assertion with assorter.winLose() = '$assertionName' not found")
                return
            }
            val cassorter = clcaAssertion.cassorter
            println("contest $contestId assertion $assertionName has noerror=${cassorter.noerror}")
            val mean = margin2mean(cassorter.assorterMargin)
            val umean =  margin2mean(cassorter.assorterMargin * contest.Npop / contest.Nc)
            println("  margin=${cassorter.assorterMargin} mean=$mean undiluted=$umean")
            val assorter = clcaAssertion.assorter
            val tracker = if (config.isOA) OaAssorterMarginTracker(contestId, cassorter as OneAuditClcaAssorter) else null

            val cardManifest = auditRecord.readCardManifest()
            val publisher = Publisher(auditDir)

            val usePrivate = (config.persistedWorkflowMode == PersistedWorkflowMode.testPrivateMvrs)
            val mvrIter = if (usePrivate) {
                readCardsCsvIterator(publisher.privateMvrsFile()).iterator()
            } else {
                null
            }
            println("cardManifest has ${cardManifest.ncards}")

            val bwelford = Welford()
            val mvrWelford = Welford()
            var count = 0
            cardManifest.cards.iterator().use { cardIter ->
                while (cardIter.hasNext()) {
                    val card = cardIter.next()
                    val mvr = if (usePrivate) mvrIter!!.next() else card
                    if (usePrivate) {
                        require(mvr.prn == card.prn)
                    }
                    if (card.hasContest(contestId)) {
                        // might want to run it through ClcaSamplerErrorTracker adapted for iterator....
                        val bassort = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
                        bwelford.update(bassort)
                        if (tracker != null) tracker.addBassort(card, mvr)

                        val mvrAssort = assorter.assort(mvr, usePhantoms = false)
                        mvrWelford.update(mvrAssort)
                    }
                    count++
                    if (count % 10_000 == 0) { print(" $count,")}
                    if (count % 100_000 == 0) { println() }
                }
            }
            println("contest $contestId assertion $assertionName\n    bassort = ${bwelford.show()}\n  mvrAssort = ${mvrWelford.show()}")
            require(doubleIsClose(cassorter.noerror,bwelford.mean, .00001)) { "*** FAIL noerror ${cassorter.noerror} != ${bwelford.mean}"}
            require(doubleIsClose(mean,mvrWelford.mean, .00001)) { "*** FAIL assort average ${mean} != ${mvrWelford.mean}"}

            if (tracker != null) {
                tracker.checkPoolAverages()
                // println(tracker.show())
            }

        } catch (t: Throwable) {
            println(t.message)
        }
    }
}

class OaAssorterMarginTracker(val contestId: Int, val oaAssorter: OneAuditClcaAssorter) {
    val passorter = oaAssorter.assorter

    val poolAssortAvgs = mutableMapOf<Int, AssortAvg>()  // contest -> assorter -> average
    val poolBassortAvgs = mutableMapOf<Int, AssortAvg>()  // contest -> assorter -> average
    var ncards = 0

    fun addBassort(card: AuditableCard, mvr: CvrIF) {
        if (card.poolId() != null) {
            val bassort = oaAssorter.bassort(mvr, card, hasStyle=card.exactContests())
            val bassortAvg = poolBassortAvgs.getOrPut(card.poolId()!!) { AssortAvg() }
            bassortAvg.totalAssort += bassort
            bassortAvg.ncards++

            val assort = passorter.assort(mvr, usePhantoms = false)
            val assortAvg = poolAssortAvgs.getOrPut(card.poolId()!!) { AssortAvg() }
            assortAvg.totalAssort += assort
            assortAvg.ncards++
        }
    }

    fun checkPoolAverages() {
        poolAssortAvgs.forEach { key, value ->
            val assortAvg = oaAssorter.poolAverage(key)!! // poolAverage of the assort values
            // TODO why not full precision?
            require(doubleIsClose(assortAvg, value.avg(), .0005)) {"*** FAIL pool average $assortAvg == ${value.avg()} for pool $key"}
        }
    }

    fun show() = buildString {
        poolAssortAvgs.forEach { key, value ->
            val assortAvg = oaAssorter.poolAverage(key) // poolAverage of the assort values
            appendLine("$key -> assortAvg $value assortAvg=$assortAvg")

            val assortMargin = mean2margin(assortAvg!!)
            // if there are no errors we expect that
            val calcBassortAvg = (1.0 / (2.0 - assortMargin / passorter.upperBound()))
            val bassortAvg = poolBassortAvgs[key]
            appendLine("        bassortAvg $bassortAvg calc=$calcBassortAvg") // these are bassort values
        }
    }
}
