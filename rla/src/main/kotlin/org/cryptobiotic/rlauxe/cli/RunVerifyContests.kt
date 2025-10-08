package org.cryptobiotic.rlauxe.cli


import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.verifier.VerifyContests

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

        parser.parse(args)
        print("RunVerifyContests on $inputDir ")
        if (contestId != null) println("for contest $contestId") else println()
        val results = runVerifyContests(inputDir, contestId, show)
        println(results)
    }

    fun runVerifyContests(inputDir: String, contestId: Int?, show: Boolean): String {
        val verifier = VerifyContests(inputDir, show)
        if (contestId != null) {
            val wantContest = verifier.contests.find { it.id == contestId }
            if (wantContest == null) return ("Cant find contest with id $contestId")
            return verifier.verifyContest(wantContest)
        }
        return verifier.verify()
    }
}
