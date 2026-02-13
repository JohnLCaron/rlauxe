package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.cryptobiotic.rlauxe.verify.VerifyResults

/** Run election record verification CLI. */
object RunVerifyContests {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunVerifier")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing input election record"
        ).required()
        val contestId by parser.option(
            ArgType.Int,
            shortName = "contest",
            description = "Verify just this contest"
        )
        val show by parser.option(
            ArgType.Boolean,
            shortName = "show",
            description = "Show details"
        ).default(false)

        try {
            parser.parse(args)
            val stopwatch = Stopwatch()

            val results = runVerifyContests(inputDir, contestId, show)
            println("RunVerifyContests took $stopwatch")
            println(results)
        } catch (t: Throwable) {
            println(t.message)
        }
    }

    fun runVerifyContests(auditDir: String, contestId: Int?, show: Boolean): VerifyResults {
        val verifier = VerifyContests(auditDir, show)
        if (contestId != null) {
            val wantContest = verifier.allContests!!.find { it.id == contestId }
            if (wantContest == null) return VerifyResults("Cant find contest with id $contestId")
            return verifier.verifyContest(wantContest)
        }
        return verifier.verify()
    }
}
