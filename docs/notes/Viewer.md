# Viewer

## Colorado Input Viewer

Original Idea is to show data from StatewideInput and CountyInput, not from AuditRecord

````
AuditCenter Input: ColoradoInputTable
    Available Data - ColoradoInputBean
        listOf(StatewideInput)
    Canonical Contests - CanonicalContestBean
        StatewideInput.mergedContestMap()
        StatewideInput.contestTabsAllCounties()

Counties: CiCounties
    CorlaCountyData - CountyTabBean
        /resources/data/cases/corla/countySummaryData/name.csv  (CountyInputData)
        /resources/data/cases/corla/redactedCount/${input.name()}.csv (RedactionCount)
        StatewideInput.countyTabAllContests
        CountyInput
    CountyContestTab - CountyContestTabBean
        countyTabAllContests.CountyContestVotes

All Samples: MvrSampleTable
    MvrSampleBean / ComparisionLineBean
        CardComparisonResults from stateInput.cardComparison().mvrs (all counties)

Mvr Comparisons: MvrComparisonTable
    MvrComparisonBean 
        low level headers and lines from input.mvrComparisonFile, show each line

CountySchema: CiSchema
    ContestSchema
    Choices

Redactions: CiRedactions
    Aggregations from Redaction.RedactedGroup
    RedactedRows from Redaction

Styles: CiStyles
    PrecinctStyles
        PrecinctCardStyle constructed from corlaCvrs.cvrs()
    Styles
        CvrCardStyle from CorlaCountyCvrs.CvrCardStyle

County Samples: MvrSampleTable
    MvrSampleBean / ComparisionLineBean
        stateInput.cardComparison().mvrs (restricted to this county)

CountyCards: CiCountyCards
    County Cards - CountyCardBean
        CountyCardsFromManifest manifest and cvrs.csv
    County COntest Tabs - CountyContestCvrBean
        ContestTabulation from CountyCard
        CorlaContestInfo from schemaContest
````

## Colorado Audit Viewer

````
Contests
    stateContests = CvContests

County Sampling
    CvCounties
        CvCountySampling

Mvrs
    


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

    