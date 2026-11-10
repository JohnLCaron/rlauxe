# Viewer

## Colorado Input Data

Original Idea is to show data from StatewideInput and CountyInput, not from AuditRecord

````
AuditCenter Input
    Available Data - ColoradoInputBean
        statewideInputs
    Canonical Contests - CanonicalContestBean
        StatewideInput.mergedContestMap()
        StatewideInput.contestTabsAllCounties()

Counties
    CorlaCountyData - CountyTabBean
        /resources/data/cases/corla/countySummaryData/name.csv  (CountyInputData)
        /resources/data/cases/corla/redactedCount/${input.name()}.csv (RedactionCount)
        StatewideInput.countyTabAllContests
        CountyInput
    CountyContestTab - CountyContestTabBean
        countyTabAllContests.CountyContestVotes

MvrSamples - MvrSampleBean
        stateInput.cardComparison().mvrs - CardComparisonResults from input.mvrComparisonFile (all counties), combine lines into Mvrs

MvrComparisons - MvrComparisonBean 
        low level headers and lines from input.mvrComparisonFile, show each line

CountySchema
    ContestSchema
    Choices

Redactions
    Aggregations from Redaction.RedactedGroup
    RedactedRows from Redaction

Styles
    PrecinctStyles
        PrecinctCardStyle constructed from corlaCvrs.cvrs()
    Styles
        CvrCardStyle from CorlaCountyCvrs.CvrCardStyle

CountyMvrs
    MvrSampleTable
        stateInput.cardComparison().mvrs restricted to this county

CountyCards
    CountyCardBean
        CountyCardsFromManifest manifest and cvrs.csv
    CountyContestCvrBean
        ContestTabulation from CountyCard
        CorlaContestInfo from schemaContest
````

### Reading CountyCardsFromManifest is 16x slower

readCorlaCvrs from /home/joncifer/IdeaProjects/nealmcb/auditcenter/2020/general/round_1/manifest-Denver.csv and /home/joncifer/rla/cases/corla/2020/cvrs/Denver.csv
CountyCardsFromManifest (1181464) took 13569 msec

read county cards from /home/joncifer/rla/cases/corla/2020/county/Denver/auditrecord/cardManifest.csv
readCountyCards (1181464) took 2990 msec ratio=4.538461538461538

readCountyProtoCards (1181464) took 736 msec ratio=4.0625

So what is difference between CountyCards and CardManifest ??
* CardManifest doesnt have Aggregation rows
* CardManifest has redacted rows in the missing pool or the aggregation pool

    