package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.math.ln
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
        createBelgiumElection("Limbourg", showVerify=true)
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
        showAllBelgiumElection()
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
                "${trunc("upper", 8)} | " +
                "${trunc("nmvrs", 6)} | " +
                "${trunc("minMvrs", 7)} | " +
                "${sfn("pct", 3)} % |")

        allResults.forEach {
            val (Nc, nmvrs, minAssorter) = it.value
            val pct = (100.0 * nmvrs) / Nc.toDouble()
            val minSamples = -ln(.05) / ln(2 * minAssorter.noerror())

            println("${sfn(it.key, 15)} | " +
                    "${sfn(minAssorter.shortName(), 42)} | " +
                    "${dfn(minAssorter.noerror(), 6)} | " +
                    "${dfn(minAssorter.reportedMean(), 6)} | " +
                    "${dfn(minAssorter.upperBound(), 6)} | " +
                    "${trunc(nmvrs.toString(), 6)} | " +
                    "${trunc(roundToClosest(minSamples).toString(), 7)} | " +
                    "${dfn(pct, 2)} % |"
            )
        }
    }
}

fun createBelgiumElection(electionName: String, stopRound:Int=0, showVerify:Boolean = false): Pair<Int, Int> {
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
    val results = RunVerifyContests.runVerifyContests(auditdir, null, show = showVerify)
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


// Npop, nmvrs, minAssorter
fun showBelgiumElection(electionName: String): Triple<Int, Int, AssorterIF> {
    println("======================================================")
    println("showBelgiumElection $electionName")
    val topdir = "$toptopdir/$electionName"
    val auditdir = "$topdir/audit"

    val auditRecord = PersistedWorkflow(auditdir, useTest=true).auditRecord
    val contestUA = auditRecord.contests.first()
    println(contestUA.show())
    val minAssertion = contestUA.minAssertion()
    val minAssorter = minAssertion!!.assorter
    println("minAssorter: ${minAssorter}")
    println("  ${contestUA.minAssertionDifficulty()}")
    println(contestUA.contest.showCandidates())

    val finalRound = auditRecord.rounds.last()
    val Nb = finalRound.contestRounds.first().Npop
    return Triple(Nb, finalRound.nmvrs, minAssorter)
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

/*
AdaptiveBetting 11/19 noerrors
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  11581 | 0.97 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |   7260 | 1.40 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    907 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |   3328 | 0.32 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9364 | 1.27 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |   5339 | 0.84 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    617 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    501 | 0.29 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    357 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    569 | 0.22 % |

    GeneralAdaptiveBetting 11/20 noerrors
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  11151 | 0.94 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |   7088 | 1.37 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    904 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |   3292 | 0.32 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9081 | 1.23 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |   5246 | 0.83 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    616 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    500 | 0.29 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    356 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    568 | 0.22 % |

  GeneralAdaptiveBetting 11/20 .001 fuzz
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  11151 | 0.94 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |   9524 | 1.84 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    991 | 0.12 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |   3925 | 0.38 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9081 | 1.23 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |   5246 | 0.83 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    616 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    500 | 0.29 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    356 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    665 | 0.26 % |

    GeneralAdaptiveBetting 11/20 .01 fuzz
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  67299 | 5.65 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |  56993 | 10.98 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    904 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |  25639 | 2.47 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9081 | 1.23 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |  31027 | 4.91 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    949 | 0.16 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |   1603 | 0.91 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    285 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    496 | 0.07 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    568 | 0.22 % |
 */

/*
    GeneralAdaptiveBetting 11/22 noerrors
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  11582 | 0.97 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |   7264 | 1.40 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    907 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |   3328 | 0.32 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9483 | 1.28 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |   5341 | 0.85 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    617 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    501 | 0.29 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    357 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    569 | 0.22 % |

     GeneralAdaptiveBetting 11/22 .001 fuzz
               |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  14351 | 1.20 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |   7158 | 1.38 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    907 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |  11616 | 1.12 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9483 | 1.28 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |   5295 | 0.84 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    617 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    501 | 0.29 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |    357 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |   7186 | 2.80 % |

     GeneralAdaptiveBetting 11/22 .01 fuzz
                |                                minAssorter |  noerror |     mean |  nmvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 |  94364 | 7.92 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 |  78844 | 15.19 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 |    906 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 |   3317 | 0.32 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 |   9354 | 1.26 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 |  21738 | 3.44 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 |    832 | 0.14 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 |    997 | 0.57 % |
          Namur |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.507362 | 0.512092 |    279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 |   3469 | 0.48 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 |    584 | 0.23 % |
 */

/*
     GeneralAdaptiveBetting 11/22 noerror
                |                                minAssorter |  noerror |     mean |    upper |  nmvrs | minMvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 | 1.000000 |  11582 |   11203 | 0.97 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 | 1.125000 |   7264 |    7137 | 1.40 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 | 2.000000 |    907 |     904 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 | 1.000000 |   3328 |    3296 | 0.32 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 | 0.526316 |   9370 |    9136 | 1.27 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 | 1.166667 |   5341 |    5267 | 0.85 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 | 1.500000 |    617 |     616 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 | 1.000000 |    501 |     501 | 0.29 % |
          Namur |                      DHondt w/l='PTB'/'PS' | 0.505404 | 0.516037 | 1.500000 |    279 |     279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 | 1.333333 |    357 |     356 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 | 0.833333 |    569 |     568 | 0.22 % |

     GeneralAdaptiveBetting 11/22 .001 fuzz
                |                                minAssorter |  noerror |     mean |    upper |  nmvrs | minMvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 | 1.000000 |  11464 |   11203 | 0.96 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 | 1.125000 |   7158 |    7137 | 1.38 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 | 2.000000 |    906 |     904 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 | 1.000000 |   5461 |    3296 | 0.53 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 | 0.526316 |   9354 |    9136 | 1.26 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 | 1.166667 |   6018 |    5267 | 0.95 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 | 1.500000 |    617 |     616 | 0.11 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 | 1.000000 |    501 |     501 | 0.29 % |
          Namur |                      DHondt w/l='PTB'/'PS' | 0.505404 | 0.516037 | 1.500000 |    279 |     279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 | 1.333333 |    357 |     356 | 0.05 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 | 0.833333 |    569 |     568 | 0.22 % |

     GeneralAdaptiveBetting 11/22 .01 fuzz
                     |                                minAssorter |  noerror |     mean |    upper |  nmvrs | minMvrs | pct % |
         Anvers |                   DHondt w/l='CD&V'/'PVDA' | 0.500134 | 0.500267 | 1.000000 |  39657 |   11203 | 3.33 % |
      Bruxelles |                       DHondt w/l='PS'/'MR' | 0.500210 | 0.500472 | 1.125000 |   7158 |    7137 | 1.38 % |
    FlandreWest |                   DHondt w/l='PVDA'/'CD&V' | 0.501659 | 0.506614 | 2.000000 |    906 |     904 | 0.11 % |
    FlandreEast |                DHondt w/l='Vooruit'/'CD&V' | 0.500455 | 0.500908 | 1.000000 |  16056 |    3296 | 1.55 % |
        Hainaut |                   BelowThreshold for ECOLO | 0.500164 | 0.500173 | 0.526316 |   9354 |    9136 | 1.26 % |
          Liège |              DHondt w/l='LES ENGAGÉS'/'PS' | 0.500284 | 0.500663 | 1.166667 |  43993 |    5267 | 6.96 % |
       Limbourg |       DHondt w/l='Vooruit'/'VLAAMS BELANG' | 0.502437 | 0.507277 | 1.500000 |   1034 |     616 | 0.18 % |
     Luxembourg |              DHondt w/l='LES ENGAGÉS'/'MR' | 0.503001 | 0.505966 | 1.000000 |    500 |     501 | 0.29 % |
          Namur |                      DHondt w/l='PTB'/'PS' | 0.505404 | 0.516037 | 1.500000 |    279 |     279 | 0.09 % |
 BrabantFlamant |          DHondt w/l='VLAAMS BELANG'/'N-VA' | 0.504228 | 0.511180 | 1.333333 |   4220 |     356 | 0.59 % |
  BrabantWallon |              DHondt w/l='MR'/'LES ENGAGÉS' | 0.502644 | 0.504384 | 0.833333 |    774 |     568 | 0.30 % |

 */