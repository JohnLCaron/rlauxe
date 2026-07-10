package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.CreateCaseData
import kotlin.test.Test

/*
    --case, -case -> belgium boulder corla sf2024 (always required) { String }
    --toptopdir, -toptopdir -> topmost output directory (always required) { String }
    --cvrExport, -cvrExport [false] -> create cvrExport.csv from CVR_Export_20241202143051.zip
    --auditType, -type [oa] -> oa clca { String }

    val cases = "/home/stormy/datadrive/rla/cases"
 */
// TODO add this to the "complete coverage calculation"
class TestCreateCaseData {

    @Test
    fun createAllCases() {
        CreateCaseData.main(
            arrayOf(
                "--case", "belgium",
                "--toptopdir", "${cases}/belgium2024",
            )
        )

        CreateCaseData.main(
            arrayOf(
                "--case", "sf2024",
                "--toptopdir", "${cases}/sf/sf2024",
                "--cvrExport",
            )
        )
        CreateCaseData.main(
            arrayOf(
                "--case", "sf2024",
                "--toptopdir", "${cases}/sf/sf2024",
                "--auditType", "oa",
            )
        )
        CreateCaseData.main(
            arrayOf(
                "--case", "sf2024",
                "--toptopdir", "${cases}/sf/sf2024",
                "--auditType", "clca",
            )
        )

        CreateCaseData.main(
            arrayOf(
                "--case", "boulder2024",
                "--toptopdir", "${cases}/boulder2024",
                "--auditType", "oa",
            )
        )
        CreateCaseData.main(
            arrayOf(
                "--case", "boulder2024",
                "--toptopdir", "${cases}/boulder2024",
                "--auditType", "clca",
            )
        )

        CreateCaseData.main(
            arrayOf(
                "--case", "corla2020",
                "--toptopdir", "${cases}/corla2020",
                "--auditcenter", "/home/stormy/datadrive/github/nealmcb/auditcenter",
            )
        )
        CreateCaseData.main(
            arrayOf(
                "--case", "corla2020",
                "--toptopdir", "${cases}/corla2020",
                "--auditcenter", "/home/stormy/datadrive/github/nealmcb/auditcenter",
                "-sampling", "uniform"
            )
        )
        CreateCaseData.main(
            arrayOf(
                "--case", "belgium",
                "--toptopdir", "${cases}/belgium2024",
            )
        )
    }

    @Test
    fun createNewCases() {
        CreateCaseData.main(
            arrayOf(
                "--case", "ga2026",
                "--toptopdir", "${cases}/ga/ga2026",
                "--input", "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted",
            )
        )
    }

}