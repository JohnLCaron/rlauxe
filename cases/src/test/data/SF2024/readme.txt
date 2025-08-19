# SF 2024 election

used in OneAudit example:  https://github.com/pbstark/SHANGRLA/blob/main/examples/ONEAudit-demo.ipynb

https://sfelections.org/results/20241105w/detail.html
https://www.sfelections.org/results/20241105/data/20241203/CVR_Export_20241202143051.zip (296 Mb)
CVR_Export_20241202143051.csv.zip (17 Mb)

    fun testReadDominionCvrJsonFiles() {
        val zipFilename = "/home/stormy/Downloads/CVR_Export_20241202143051.zip"
        val csvFilename = "/home/stormy/Downloads/CVR_Export_20241202143051.csv"
// read 1,641,744 cvrs 27,554 files took 58.67 s

https://sfelections.org/results/20241105w/index.html

Number of Ballots Cast: 412,231
Last Update: December 3, 2024 3:05 PM
Voter registration total: 522,265
Voter turnout: 78.93%
Precincts that have reported in-person results: 514 of 514 (100.00%)

-------------
Aug  18, 2025

From Dice dont Slice paper:

We consider the 2024 mayoral race in San Francisco as a case study. This instant-
runoff voting (IRV) contest included thirteen candidates. Daniel Lurie, who
received 26% of the first-choice selections and 55% after all but two candidates
were eliminated, defeated incumbent London Breed, who received 24% of the
first-choice elections and 45% of the final round votes.

The election produced 1,603,908 CVRs,
of which 216,286 were for cards cast in 4,223 precinct batches
and 1,387,622 CVRs were for vote-by-mail (VBM) cards.
VBM CVRs are linked to the corresponding card, facilitating ballot-level
comparison auditing, but the in-person CVRs are not linked to individual cards,
only to tabulation batches. The CVRs were incorporated into the audit using
ONEAudit. RAIRE [4] was used to generate the assertions for the audit to test.

From /home/stormy/dev/github/rla/UI-TS/Code/read-sf-cvrs.ipynb

Download the SF CVRs from https://sfelections.org/results/20241105w/detail.html
Under the 'Final Report' tab click "Cast Vote Record (Raw data) - JSON" to download a zip file with all the CVRs.

(This file is /home/temp/cases/sf2024/CVR_Export_20241202143051.zip)
This zip file CVR_Export_20241202143051.zip (296 MB) contains 27,570 files:

    BallotTypeContestManifest.json
    BallotTypeManifest.json
    CandidateManifest.json
    Configuration.json
    ContestManifest.json
    CountingGroupManifest.json
    CvrExport_0.json
    CvrExport_10000.json
    CvrExport_10001.json
    CvrExport_10002.json
    ...

read-sf-cvrs.ipynb:

audit = Audit.from_dict({
         'seed':           12345678901234567890,
         'sim_seed':       314159265,
         'cvr_file':       './sf-cvrs-2024/CvrExport_*.json', # Edit with your file path
         'manifest_file':  './sf-cvrs-2024/BallotTypeManifest.json',
         'sample_file':    '...', # EDIT
         'mvr_file':       '...', # EDIT
         'log_file':       '...', # EDIT
         'quantile':       0.8,
         'error_rate_1':   0.001,
         'error_rate_2':   0.0,
         'reps':           200,
         'strata':         {'stratum_1': {'max_cards':   1603908,
                                          'use_style':   True,
                                          'replacement': False
                                         }
                           }
        })

# Mayoral contest
contest_dict = {
               '18':{
                   'name': 'MAYOR',
                   'risk_limit':       0.05,
                   'cards':            1401980,
                   'choice_function':  Contest.SOCIAL_CHOICE_FUNCTION.PLURALITY,
                   'n_winners':        1,
                   'candidates':       ['54', '55', '56', '57', '58', '59', '60', '61', '62', '63', '64', '65', '66', '173', '175', '176'],
                   'winner':           ['57'],
                   'assertion_file':   None,
                   'audit_type':       Audit.AUDIT_TYPE.ONEAUDIT,
                   'test':             NonnegMean.alpha_mart,
                   'estim':            NonnegMean.shrink_trunc,
                   'test_kwargs':      {'d': 100, 'f': 0}
                  }
               }


TestSfElection:

        // write sf2024 cvrs
        @Test
        fun createSF2024cards() {
            val topDir = "/home/stormy/rla/cases/sf2024"
            val zipFilename = "$topDir/CVR_Export_20241202143051.zip"
            val manifestFile = "ContestManifest.json"
            createAuditableCards(topDir, zipFilename, manifestFile) // write to "$topDir/cards.csv"

            val auditDir = "$topDir/audit"
            clearDirectory(Path.of(auditDir))

            // optionally pass in auditConfigIn: AuditConfig
            createSfElectionFromCards(
                auditDir,
                zipFilename,
                "ContestManifest.json",
                "CandidateManifest.json",
                "$topDir/cards.csv",
                show = false,
            )

            sortCards(auditDir, "$topDir/cards.csv", "$topDir/sortChunks")
            mergeCards(auditDir, "$topDir/sortChunks") // merge to "$auditDir/sortedCards.csv"
        }

// election ready to run in RlauxeViewer or RunRliRoundCli