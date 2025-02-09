package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlin.system.exitProcess

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.min

/** Create the starting election state. */
class RunRlaStartTest {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunRlaStartTest")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing test election record"
            ).required()
            val isPolling by parser.option(
                ArgType.Boolean,
                shortName = "isPolling",
                description = "Polling election"
            ).default(false)
            val minMargin by parser.option(
                ArgType.Double,
                shortName = "minMargin",
                description = "contest minimum margin"
            ).default(0.04)
            val fuzzMvrs by parser.option(
                ArgType.Double,
                shortName = "fuzzMvrs",
                description = "Fuzz Mvrs by this factor (0.0 to 1.0)"
            ).default(0.0)
            val pctPhantoms by parser.option(
                ArgType.Double,
                shortName = "pctPhantoms",
                description = "Pct phantoms (0.0 to 1.0)"
            )
            val ncards by parser.option(
                ArgType.Int,
                shortName = "ncards",
                description = "Total number of ballot/cards"
            ).default(10000)
            val mvrFile by parser.option(
                ArgType.String,
                shortName = "mvrs",
                description = "File containing sampled Mvrs"
            ).required()

            parser.parse(args)
            println("RunStartTestElection on $inputDir isPolling=$isPolling minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards\n  mvrFile=$mvrFile")
            val retval = if (!isPolling) startTestElectionClca(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, mvrFile)
                              else startTestElectionPolling(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, mvrFile)
            println("retval $retval")
            exitProcess(retval)
        }

        fun startTestElectionClca(topdir: String, minMargin: Double, fuzzMvrs: Double, pctPhantoms: Double?, ncards: Int, mvrFile: String): Int {
            println("Start startTestElectionClca")
            val publish = Publisher(topdir)
            val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, nsimEst=10)
            writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())

            val maxMargin = .05
            val useMin = min(minMargin, maxMargin)
            val phantomPctRange: ClosedFloatingPointRange<Double> = if (pctPhantoms == null) 0.00..  0.005 else pctPhantoms .. pctPhantoms

            val testData = MultiContestTestData(11, 4, ncards, marginRange=useMin..maxMargin, phantomPctRange=phantomPctRange)

            val contests: List<Contest> = testData.contests
            println("$testData")
            contests.forEach{ println("  $it")}
            println()

            // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
            val testCvrs = testData.makeCvrsFromContests()
            val testMvrs = if (fuzzMvrs == 0.0) testCvrs
                // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

            // ClcaWorkflow assigns the sample numbers, and creates the assertions
            var clcaWorkflow = ClcaWorkflow(auditConfig, contests, emptyList(), testCvrs, quiet = false)
            writeCvrsJsonFile(clcaWorkflow.cvrsUA, publish.cvrsFile())
            println("   writeCvrsJsonFile ${publish.cvrsFile()}")

            // save the testMvrs. kludgey
            val mvrus = testMvrs.mapIndexed { idx, mvr ->
                val cvr = clcaWorkflow.cvrsUA[idx]
                CvrUnderAudit(mvr, cvr.sampleNumber())
            }
            writeCvrsJsonFile(mvrus, mvrFile)
            println("   writeCvrsJsonFile ${mvrFile}")

            // get the first round of samples wanted, write them to round1
            val samples = runChooseSamples(1, clcaWorkflow, publish)
            val result = if (samples.size == 0) {
                println("***FAILED TO GET ANY SAMPLES***")
                -1
            } else {
                println("nsamples needed = ${samples.size}, ready to audit")
                0
            }

            // write the partial audit state to round1
            val state = AuditState("Starting", 1, samples.size, samples.size, false, false, clcaWorkflow.getContests())
            writeAuditStateJsonFile(state, publish.auditRoundFile(1))
            println("   writeAuditStateJsonFile ${publish.auditRoundFile(1)}")

            return result
        }

        fun startTestElectionPolling(topdir: String, minMargin: Double, fuzzMvrs: Double, pctPhantoms: Double?, ncards: Int, mvrFile: String): Int {
            val publish = Publisher(topdir)
            val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, seed = 12356667890L, nsimEst = 10)
            writeAuditConfigJsonFile(auditConfig, publish.auditConfigFile())
            println("   writeAuditConfigJsonFile ${publish.auditConfigFile()}")

            val maxMargin = .08
            val useMin = min(minMargin, maxMargin)
            val testData = MultiContestTestData(11, 4, ncards, marginRange=useMin..maxMargin)

            val contests: List<Contest> = testData.contests
            println("Start testPersistentWorkflowPolling $testData")
            contests.forEach { println("  $it") }
            println()

            val (testCvrs, ballotManifest) = testData.makeCvrsAndBallotManifest(auditConfig.hasStyles)
            val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

            // PollingWorkflow assigns the sample numbers, and creates the assertions
            val pollingWorkflow = PollingWorkflow(auditConfig, contests, ballotManifest, testCvrs.size, quiet = false)
            val ballotManifestUA = BallotManifestUnderAudit(pollingWorkflow.ballotsUA, ballotManifest.ballotStyles)
            writeBallotManifestJsonFile(ballotManifestUA, publish.ballotManifestFile())
            println("   writeBallotManifestJsonFile ${publish.ballotManifestFile()}")

            // save the testMvrs. kludgey
            val mvrus = testMvrs.mapIndexed { idx, mvr ->
                val ballot = pollingWorkflow.ballotsUA[idx]
                CvrUnderAudit(mvr, ballot.sampleNumber())
            }
            writeCvrsJsonFile(mvrus, mvrFile)
            println("   writeCvrsJsonFile ${mvrFile}")

            // get the first round of samples wanted, write them to round1 subdir
            val samples = runChooseSamples(1, pollingWorkflow, publish)
            val result = if (samples.size == 0) {
                println("***FAILED TO GET ANY SAMPLES***")
                -1
            } else {
                println("nsamples needed = ${samples.size}, ready to audit")
                0
            }

            // write the partial audit state to round1
            val state = AuditState("Starting", 1, samples.size, samples.size, false, false, pollingWorkflow.getContests())
            writeAuditStateJsonFile(state, publish.auditRoundFile(1))
            println("   writeAuditStateJsonFile ${publish.auditRoundFile(1)}")

            return result
        }
    }
}

fun runChooseSamples(roundIdx: Int, workflow: RlauxWorkflowIF, publish: Publisher): List<Int> {
    val indices = workflow.chooseSamples(roundIdx, show = true)
    if (indices.isNotEmpty()) {
        writeSampleIndicesJsonFile(indices, publish.sampleIndicesFile(roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.sampleIndicesFile(roundIdx)}")
    }
    return indices
}
