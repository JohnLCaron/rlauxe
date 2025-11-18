package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.runRound
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

val belgianElectionMap = mapOf(
    "Anvers" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription d'Anvers.json",
    "Bruxelles" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Bruxelles-Capitale.json",
    "FlandreWest" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre occidentale.json",
    "FlandreEast" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre orientale.json",
    "Hainaut" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Hainaut.json",
    "Liège" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Liège.json",
    "Limbourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Limbourg.json",
    "Luxembourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Luxembourg.json",
    "Namur" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Namur.json",
    "BrabantFlamant" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant flamand.json",
    "BrabantWallon" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant wallon.json",
)
val toptopdir = "/home/stormy/rla/cases/belgium/2024"

class TestCreateBelgiumClcaFromJson {
    @Test
    fun createBelgiumElection() {
        createBelgiumElection("Limbourg")
    }

    @Test
    fun runBelgiumElection() {
        runBelgiumElection("Limbourg")
    }

    @Test
    fun createAllBelgiumElection() {
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        belgianElectionMap.keys.forEach {
            allmvrs[it] =  createBelgiumElection(it)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

    @Test
    fun showBelgiumElection() {
        val electionName = "Limbourg"
        val result = showBelgiumElection(electionName)
        val pct = (100.0 * result.second) / result.first.toDouble()
        println("${sfn(electionName, 15)}: Nc= ${trunc(result.first.toString(), 10)} " +
                " nmvrs= ${trunc(result.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
    }

    @Test
    fun showAllBelgiumElection() {
        val allResults = mutableMapOf<String, Triple<Int, Int, AssorterIF>>()
        belgianElectionMap.keys.forEach {
            allResults[it] = showBelgiumElection(it)
        }

        println("${sfn("", 15)} | ${trunc("minAssorter", 42)} | " +
                "${trunc("noerror", 8)} | " +
                "${trunc("mean", 8)} | " +
                "${trunc("nmvrs", 6)} | ${sfn("pct", 3)} % |")

        allResults.forEach {
            val (Nc, nmvrs, minAssorter) = it.value
            val pct = (100.0 * nmvrs) / Nc.toDouble()
            val expectedRisk = minAssorter.noerror().pow(nmvrs.toDouble())

            println("${sfn(it.key, 15)} | " +
                    "${sfn(minAssorter.shortName(), 42)} | " +
                    "${dfn(minAssorter.noerror(), 6)} | " +
                    "${dfn(minAssorter.reportedMean(), 6)} | " +
                    // "${trunc(Nc.toString(), 10)} | " +
                    "${trunc(nmvrs.toString(), 6)} | " +
                    "${dfn(pct, 2)} % |"
                    // "${dfn(expectedRisk, 6)} |"
            )
        }
    }
}

fun createBelgiumElection(electionName: String, stopRound:Int=0): Pair<Int, Int> {
    println("======================================================")
    println("electionName $electionName")
    val filename = belgianElectionMap[electionName]!!
    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
    val belgiumElection = if (result is Ok) result.unwrap()
    else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

    val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
    val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
    // val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes,.05)
    val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, 0,.05)

    val totalVotes = belgiumElection.NrOfValidVotes // + belgiumElection.NrOfBlankVotes
    val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

    val topdir = "$toptopdir/$electionName"
    createBelgiumClca(topdir, contestd)

    val publisher = Publisher("$topdir/audit")
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    val auditdir = "$topdir/audit"
    val results = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(results)
    if (results.hasErrors) fail()

    println("============================================================")
    var done = false
    var finalRound: AuditRound? = null
    while (!done) {
        val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
        if (lastRound != null) finalRound = lastRound
        done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
    }

    return if (finalRound != null) {
        println("$electionName: ${finalRound.show()}")
        Pair(totalVotes, finalRound.nmvrs)
    } else Pair(0, 0)
}

fun runBelgiumElection(electionName: String, stopRound:Int=0): Int {
    val topdir = "$toptopdir/$electionName"
    val auditdir = "$topdir/audit"

    var done = false
    var finalRound: AuditRound? = null
    while (!done) {
        val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
        if (lastRound != null) finalRound = lastRound
        done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
    }

    return if (finalRound != null) {
        println("$electionName: ${finalRound.show()}")
        finalRound.nmvrs
    } else 0
}


// Nc, nmvrs, minAssorter
fun showBelgiumElection(electionName: String): Triple<Int, Int, AssorterIF> {
    println("======================================================")
    println("showBelgiumElection $electionName")
    val topdir = "$toptopdir/$electionName"
    val auditdir = "$topdir/audit"

    val auditRecord = PersistedWorkflow(auditdir, useTest=true).auditRecord
    val contestUA = auditRecord.contests.first()
    println(contestUA.show())
    val (minAssertion, minMargin) = contestUA.minAssertion()
    val minAssorter = minAssertion!!.assorter
    println("minAssorter: ${minAssorter}")
    println("  ${contestUA.minAssertionDifficulty()}")
    println(contestUA.contest.showCandidates())

    val finalRound = auditRecord.rounds.last()
    val Nc = finalRound.contestRounds.first().Nc
    return Triple(Nc, finalRound.nmvrs, minAssorter)
}

/* UnderThreshold assertions only
20 14
         Anvers: Nc=    1189179  nmvrs=   3113 pct= 0.26 %
      Bruxelles: Nc=     527782  nmvrs=   5263 pct= 1.00 %
    FlandreWest: Nc=     853739  nmvrs=   1184 pct= 0.14 %
    FlandreEast: Nc=    1038603  nmvrs=   9478 pct= 0.91 %
        Hainaut: Nc=     811712  nmvrs=   2085 pct= 0.26 %
          Liège: Nc=     673797  nmvrs=   2596 pct= 0.39 %
       Limbourg: Nc=     588158  nmvrs=   8009 pct= 1.36 %
     Luxembourg: Nc=     184133  nmvrs=    365 pct= 0.20 %
          Namur: Nc=     324712  nmvrs=   2344 pct= 0.72 %
 BrabantFlamant: Nc=     713184  nmvrs=    418 pct= 0.06 %
  BrabantWallon: Nc=     252499  nmvrs=   1484 pct= 0.59 %
  
20 19
         Anvers: Nc=    1200314  nmvrs=   8134 pct= 0.68 %
      Bruxelles: Nc=     538109  nmvrs=   1677 pct= 0.31 %
    FlandreWest: Nc=     850662  nmvrs=    929 pct= 0.11 %
    FlandreEast: Nc=    1049066  nmvrs=   1331 pct= 0.13 %
        Hainaut: Nc=     810896  nmvrs=   3301 pct= 0.41 %
          Liège: Nc=     675279  nmvrs=   2214 pct= 0.33 %
       Limbourg: Nc=     588261  nmvrs=    651 pct= 0.11 %
     Luxembourg: Nc=     188681  nmvrs=    163 pct= 0.09 %
          Namur: Nc=     330601  nmvrs=   2286 pct= 0.69 %
 BrabantFlamant: Nc=     725017  nmvrs=   1467 pct= 0.20 %
  BrabantWallon: Nc=     261747  nmvrs=    148 pct= 0.06 %

20 24
         Anvers: Nc=    1235587  nmvrs=  12030 pct= 0.97 %
      Bruxelles: Nc=     550514  nmvrs=   7713 pct= 1.40 %
    FlandreWest: Nc=     866870  nmvrs=    951 pct= 0.11 %
    FlandreEast: Nc=    1083369  nmvrs=   3473 pct= 0.32 %
        Hainaut: Nc=     819569  nmvrs=  10408 pct= 1.27 %
          Liège: Nc=     685695  nmvrs=   5803 pct= 0.85 %
       Limbourg: Nc=     606094  nmvrs=   1493 pct= 0.25 %
     Luxembourg: Nc=     193691  nmvrs=    554 pct= 0.29 %
          Namur: Nc=     338346  nmvrs=    303 pct= 0.09 %
 BrabantFlamant: Nc=     751697  nmvrs=    374 pct= 0.05 %
  BrabantWallon: Nc=     270051  nmvrs=    600 pct= 0.22 %
 */

/* UnderThreshold and OverThreshold assertions
20 14
         Anvers: Nc=    1189179  nmvrs=   3113 pct= 0.26 %
      Bruxelles: Nc=     527782  nmvrs=   5263 pct= 1.00 %
    FlandreWest: Nc=     853739  nmvrs=   1184 pct= 0.14 %
    FlandreEast: Nc=    1038603  nmvrs=   9478 pct= 0.91 %
        Hainaut: Nc=     811712  nmvrs=   2085 pct= 0.26 %
          Liège: Nc=     673797  nmvrs=   2596 pct= 0.39 %
       Limbourg: Nc=     588158  nmvrs=   8009 pct= 1.36 %
     Luxembourg: Nc=     184133  nmvrs=    365 pct= 0.20 %
          Namur: Nc=     324712  nmvrs=   2344 pct= 0.72 %
 BrabantFlamant: Nc=     713184  nmvrs=    418 pct= 0.06 %
  BrabantWallon: Nc=     252499  nmvrs=   1484 pct= 0.59 %

20 19
         Anvers: Nc=    1200314  nmvrs=   8134 pct= 0.68 %
      Bruxelles: Nc=     538109  nmvrs=   1677 pct= 0.31 %
    FlandreWest: Nc=     850662  nmvrs=    929 pct= 0.11 %
    FlandreEast: Nc=    1049066  nmvrs=   1331 pct= 0.13 %
        Hainaut: Nc=     810896  nmvrs=   3301 pct= 0.41 %
          Liège: Nc=     675279  nmvrs=   2214 pct= 0.33 %
       Limbourg: Nc=     588261  nmvrs=    651 pct= 0.11 %
     Luxembourg: Nc=     188681  nmvrs=    163 pct= 0.09 %
          Namur: Nc=     330601  nmvrs=   2286 pct= 0.69 %
 BrabantFlamant: Nc=     725017  nmvrs=   1467 pct= 0.20 %
  BrabantWallon: Nc=     261747  nmvrs=    284 pct= 0.11 %

20 24
         Anvers: Nc=    1235587  nmvrs=  12030 pct= 0.97 %
      Bruxelles: Nc=     550514  nmvrs=   7713 pct= 1.40 %
    FlandreWest: Nc=     866870  nmvrs=    951 pct= 0.11 %
    FlandreEast: Nc=    1083369  nmvrs=   3473 pct= 0.32 %
        Hainaut: Nc=     819569  nmvrs=  10408 pct= 1.27 %
          Liège: Nc=     685695  nmvrs=   5803 pct= 0.85 %
       Limbourg: Nc=     606094  nmvrs=   1493 pct= 0.25 %
     Luxembourg: Nc=     193691  nmvrs=    554 pct= 0.29 %
          Namur: Nc=     338346  nmvrs=    303 pct= 0.09 %
 BrabantFlamant: Nc=     751697  nmvrs=    374 pct= 0.05 %
  BrabantWallon: Nc=     270051  nmvrs=    600 pct= 0.22 %
 */