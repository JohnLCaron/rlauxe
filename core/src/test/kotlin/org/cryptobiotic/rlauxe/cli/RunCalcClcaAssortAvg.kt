package org.cryptobiotic.rlauxe.cli

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ClcaAssorter
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.persist.CardManifest
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.String
import kotlin.use

/** Calculate CLCA Assort averages by reading through the entire CardManifest.
 * TODO: probably this only works when there are no errors ??
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
        )
        val assertionName by parser.option(
            ArgType.String,
            shortName = "assertion",
            description = "assertion short name"
        )

        try {
            parser.parse(args)

            val auditRecordResult = AuditRecord.readWithResult(auditDir)
            val auditRecord = if (auditRecordResult.isOk) auditRecordResult.unwrap() else {
                println("auditRecord not found at $auditDir")
                println(auditRecordResult.unwrapError())
                return
            }
            require(auditRecord is AuditRecord)
            val config = auditRecord.config
            println("auditRecord in $auditDir isOA=${config.isOA}")

            var onlyContest: ContestWithAssertions? = null
            var onlyAssertion: ClcaAssertion? = null
            if (contestId != null) {
                onlyContest = auditRecord.contests.find { it.id == contestId }
                if (onlyContest == null) {
                    println("contest id = $contestId not found")
                    return
                } else {
                    if (assertionName != null) {
                        onlyAssertion = onlyContest.clcaAssertions.find { it.assorter.shortName() == assertionName }
                        if (onlyAssertion == null) {
                            println("assertion with assertionName = '$assertionName' for contest $contestId not found")
                            return
                        } else {
                            println("want contest $contestId with assertion=$assertionName")
                        }
                    } else {
                        println("want contest $contestId with all assertions")
                    }
                }
            } else {
                println("want all contests and all assertions")
            }

            val expectations = if (onlyContest != null) {
                if (onlyAssertion != null) {
                    listOf(Expectation(onlyContest, onlyAssertion.cassorter))
                } else {
                    onlyContest.clcaAssertions.map { Expectation(onlyContest, it.cassorter) }
                }
            } else {
                auditRecord.contests.map { contest ->
                    contest.clcaAssertions.map {
                        Expectation(
                            contest,
                            it.cassorter
                        )
                    }
                }.flatten()
            }

            val mvrManager = PersistedMvrManager(auditRecord)
            val cardManifest = mvrManager.sortedManifest()
            val publisher = Publisher(auditDir)
            println("cardManifest has ${cardManifest.ncards} cards")

            val usePrivate = (config.mvrSource == MvrSource.testPrivateMvrs)
            val mvrIter = if (usePrivate) mvrManager.readCardsAndMerge(publisher.sortedMvrsFile()) else null

            runCards(expectations, cardManifest, mvrIter, usePrivate)

        } catch (t: Throwable) {
            println(t.message)
        }
    }


    val tol = doublePrecision

    fun runCards(
        expectations: List<Expectation>,
        cardManifest: CardManifest,
        mvrIter: Iterator<AuditableCard>?,
        usePrivate: Boolean
    ) {
        var count = 0
        cardManifest.cards.iterator().use { cardIter ->
            while (cardIter.hasNext()) {
                val card = cardIter.next()
                val mvr = if (usePrivate) mvrIter!!.next() else card
                if (usePrivate) {
                    require(mvr.prn == card.prn)
                }
                expectations.forEach { expect ->
                    if (card.hasContest(expect.id)) {
                        val bassort = expect.cassorter.bassort(mvr, card, hasStyle = card.hasStyle())
                        expect.bwelford.update(bassort)

                        val mvrAssort = expect.assorter.assort(mvr, usePhantoms = false)
                        expect.mvrWelford.update(mvrAssort)
                    }
                }
                count++
                if (count % 10_000 == 0) {
                    print(" $count,")
                }
                if (count % 100_000 == 0) {
                    println()
                }
            }
        }
        println()
        expectations.forEach { expect ->
            print("contest=${expect.id}, cassorter=${expect.cassorter.shortName()}, Npop ${expect.contest.Npop} count ${expect.bwelford.count}")

            if (expect.bwelford.count != expect.contest.Npop) println(" *** FAIL ${(expect.bwelford.count - expect.contest.Npop)}")
            else println()
        }

        var lastContest = -1
        expectations.forEach { expect ->
            if (expect.id != lastContest) println()
            lastContest = expect.id
            println(expect)

            if (!doubleIsClose(expect.expectBassortMean(), expect.bwelford.mean, tol))
                println("*** FAIL expectBassortMean ${expect.expectBassortMean()} != ${expect.bwelford.mean}")
            if (!doubleIsClose(expect.mean, expect.mvrWelford.mean, tol))
                println("*** FAIL expectMean ${expect.mean} != ${expect.mvrWelford.mean}")
            //if (expect.bwelford.count != expect.contest.Npop)
            //     println( "*** FAIL contest Npop ${expect.contest.Npop} != ${expect.bwelford.count} count" )
        }
    }

    data class Expectation(val contest: ContestWithAssertions, val cassorter: ClcaAssorter) {
        val assorter = cassorter.assorter
        val mean = margin2mean(cassorter.assorterMargin)
        val noerror = cassorter.noerror  // TODO can we predict bassortMean based on nphantoms and pool avgs ??

        val id = contest.id
        val bwelford = Welford()
        val mvrWelford = Welford()

        fun expectBassortMean(): Double =
            // phantoms are tau = 1/2 * noerror
            cassorter.noerror * (1.0 - contest.Nphantoms / (2.0 * contest.Npop))

        override fun toString() = buildString {
            append(
                "contest=$id, cassorter=${cassorter.shortName()}, mean=${dfn(mean, 8)} == mvr assortMean=${
                    dfn(
                        mvrWelford.mean,
                        8
                    )
                }; "
            )
            append(
                "expectBassortMean=${dfn(expectBassortMean(), 8)} == bassortMean=${
                    dfn(
                        bwelford.mean,
                        8
                    )
                }; Npop=${contest.Npop}"
            )
        }
    }
}

/*
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
} */
