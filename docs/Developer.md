# Developer Notes
_01/27/2026_

## Prerequisites

1. A git client that is compatible with github.
2. **Java 21+**. Install as needed, and make it your default JVM when working with rlauxe.
3. The correct version of gradle and kotlin will be installed when you invoke a gradle command.
4. You need internet access to download the dependencies.


## Download the git repository

````
cd <devhome>
git clone https://github.com/JohnLCaron/rlauxe.git
cd rlauxe
````

## Build the library
To do a clean build (no tests):

```
cd <devhome>/rlauxe
./gradlew clean assemble
```

Normally rlauxe-vierer keeps the current rlauxe library inside its own repo.
However, if the library has changed on github and you need to rebuild it:

````
cd <devhome>/rlauxe
git fetch origin
git rebase -i origin/main
````

Then rebuild the code:

````
./gradlew clean assemble
````

## Run tests

## Set the test data directory

Before running the tests, go to the source file  _core/src/testFixtures/kotlin/org/cryptobiotic/rlauxe/TestUtils.kt_.
At the top of the file, change the line:

````
val testdataDir = "/home/stormy/rla"
````

to some directory on your system. Make sure the directory exists.

### Run the core tests using gradle

To build the complete library and run the core tests:

```
    cd <devhome>/rlauxe
    ./gradlew clean assemble
    ./gradlew core:test
```

To run a subset of tests in cases:

```
    cd <devhome>/rlauxe
    ./gradlew :cases:test --tests "org.cryptobiotic.util.*"
```


## Using IntelliJ

We recommend using the IntelliJ IDE if you plan on doing Java/Kotlin coding, or even if you are just building and running.

* Make sure the prerequisites are satisfied, as above.
* Download the git repository as above.
* Install IntelliJ. The community version probably works fine, but an individual license for the Ultimate Edition is well worth it.

Start up IntelliJ, and in the top menu:

1. File / New / "Project from existing sources"
2. In the popup window, navigate to _devhome/rlauxe_ and select that directory
3. Choose "Import project from existing model" / Gradle

IntelliJ will create and populate an IntelliJ project with the rlauxe sources. 

To build the library, from the topmenu:  Build / Build Project (Ctrl-F9)

To run the core tests, from the left Project Panel source tree, navigate to the _core/src/test/kotlin/org/cryptobiotic/rlauxe_
directory, right click on the directory name, choose "Run tests in ...". If that menu option isnt shown, check if you're in the 
main source tree instead of the test source tree.

To run individual tests, go to the test source; IntelliJ will place a clickable green button in the left margin wherever 
there is a runnable test.

There's lots of online help for using IntelliJ.


## Modules

* **cases**: code to create case studies
* **core**: core library
* **docs**: documentation
* **libs**: local copy of raire-java library
* **plots**: code to generate plots for documentation


## Test Cases

The repo contains all the test case data, except for San Francisco. Download

  [SF2024 data](https://www.sfelections.org/results/20241105/data/20241203/CVR_Export_20241202143051.zip)

into testdataDir/cases/sf2024/ (where _testdataDir_ is as you chose in the "Set the test data directory" step above)

Then run _createSf2024CvrExport()_ test in _cases/src/test/kotlin/org/cryptobiotic/rlauxe/sf/CreateSf2024CvrExport.kt_
to generate _testdataDir/cases/sf2024/crvExport.csv_. This only needs to be done one time.

All the test cases can be generated from:

_cases/src/test/kotlin/org/cryptobiotic/util/TestGenerateAllUseCases.kt_.

Run the verifier on all the generated test cases:

_cases/src/test/kotlin/org/cryptobiotic/util/TestVerifyUseCases.kt_.


## rlauxe viewer

Download the [rlauxe-viewer repo](https://github.com/JohnLCaron/rlauxe-viewer) and follow instructions there to view 
Audit Records and run audits on them, in particular, on any of the test cases.

**Caveat Emptor**: The serialization formats are undergoing rapid changes, with no backwards compatibility (yet). Expect that
if you download a new version of the library, you will possibly have to regenerate any audit records (including tests cases), 
before viewing them.


# Random notes and stats

## Code Coverage (Lines of Codes)

 **core test coverage**

| date       | pct    | cover/total LOC |
|------------|--------|-----------------|
| 11/20/2025 | 84.0 % | 5602/6667       |
| 11/25/2025 | 85.2 % | 5229/6136       |
| 11/28/2025 | 85.9 % | 5188/6039       |
| 11/29/2025 | 86.3 % | 5208/6034       |
| 11/30/2025 | 86.7 % | 5255/6058       |
| 12/04/2025 | 85.0 % | 5327/6265       |
| 12/10/2025 | 80.5 % | 5338/6634       |
| 12/13/2025 | 82.8 % | 5341/6449       |
| 12/18/2025 | 83.9 % | 5332/6357       |
| 12/23/2025 | 83.9 % | 5393/6431       |
| 01/07/2026 | 84.5 % | 5344/6327       |
| 01/16/2026 | 87.5 % | 5417/6190       |
| 01/18/2026 | 90.2 % | 5677/6294       |
| 01/27/2026 | 87.6 % | 6021/6871       |
| 01/27/2026 | 87.8 % | 5847/6658       |

 **core + cases test coverage** 

| date       | pct    | cover/total LOC |
|------------|--------|-----------------|
| 11/28/2025 | 79.3 % | 6417/8094       |
| 11/29/2025 | 79.6 % | 6434/8087       |
| 11/29/2025 | 81.4 % | 6479/7962       |
| 12/04/2025 | 81.7 % | 6530/7994       |
| 12/10/2025 | 78.4 % | 6597/8412       |
| 12/13/2025 | 80.7 % | 6606/8187       |
| 12/23/2025 | 81.0 % | 6634/8186       |


## UML
last changed: 01/07/2026

![rlauxe core UML](images/coreUML.svg)

![rlauxe Audit UML](images/auditUML.svg)


# TODO 12/11/25 (Belgium)

* include undervotes
* assertions that look at coalitions of parties. (Vanessa)
* choose an audit size and measure the risk.

# TODO 12/20/25

* investigate the effect of population.hasSingleCardStyle = hasStyle.
* investigate possible attacks with mvr_assort = 0.5 when the mvr is missing the contest.
* review strategies and fuzzing in estimation and auditing
* replace old plots

# TODO 01/04/26

* maxRisk does it help? reduce lamda tradeoff 
* 2D plotting
* betting on the error rate
* mix_betting_mart: "Finds a simple discrete mixture martingale as a (flat) average of D TSMs each with fixed bet 'lam'"
* review COBRA 3.2, 4.3 (Diversified betting)
 
////////////////////////////////////////////
Simulation

Production

Estimation

  Polling: ContestSimulation.simulateCvrsDilutedMargin(contestRound.contestUA, config)
    ContestSimulation(contestOrg, contestUA.Npop).makeCvrs() else ContestSimulation(contestScaled, sNb)
    // could we use PollingCardFuzzSampler ??

  Clca:    ClcaCardFuzzSampler(config.simFuzzPct ?: 0.0, contestCards, contestUA.contest, cassorter)
    //  for one contest, this takes a list of cards and fuzzes them for the mvrs

  OneAudit: OneAuditVunderBarFuzzer.makePairsFromCards(contestCards)
    // fuzz cvrs, leave pools alone i think

  OneAuditVunderBarFuzzer
    // uses primitives in SamplingForEstimation
    // simulate pooled data from the pool values; (not sure about IRV)
    VunderBar = simulate pooled data Votes and Undervotes for multiple OA Pools
    VunderPool = Votes and Undervotes for one OA Pool
    Vunder = Votes and Undervotes for one OA contest

    // combines Vunder for multiple contests into cvrs for one pool
    // make cvrs until we exhaust the votes
    // this algorithm puts as many contests as possible on each cvr
    // the number of cvrs can vary when there are multiple contests
    fun makeVunderCvrs(vunders: Map<Int, Vunder>, poolName: String, poolId: Int?): List<Cvr>

Auditing

for a real audit, so simulation is used:

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits

    open class PersistedMvrManager(val auditDir: String, val config: AuditConfig, val contestsUA: List<ContestUnderAudit>, val mvrWrite: Boolean = true): MvrManager {

Testing

1. class PersistedMvrManagerTest(auditDir: String, config: AuditConfig, contestsUA: List<ContestUnderAudit>)
   // extract the cards with sampleNumbers from the cardManifest, optionally fuzz them, and write them to sampleMvrsFile
   // fails if Polling
   makeFuzzedCardsFrom(contestsUA.map { it.contest.info() }, newCards, simFuzzPct)

(or)

2. class MvrManagerForTesting
   // simulated cvrs, mvrs for testing are sorted and kept here in memory
    makeFuzzedCvrsFrom(contests, testCvrs, mvrFuzzPct) // handles IRV

(or)

3. writeUnsortedPrivateMvrs(Publisher(auditdir), testMvrs, config.seed)
    // (persistent) write mvrs to private when audit is created
    // eg from MultiContestTestData.makeCvrsFromContests() with simFuzzPct

(or)

// used for singleRoundAudit
4. class MvrManagerFromManifest(
    cardManifest: List<AuditableCard>,
    mvrs: List<Cvr>,
    val infoList: List<ContestInfo>,
    seed:Long,
    val simFuzzPct: Double?,
    val pools: List<OneAuditPoolIF>? = null,
    ) : MvrManager


Other Testing

testFixtures

    MultiContestTestData: specify the contests with range of margins, phantoms, undervotes, phantoms, single poolId, poolPct
        used by RunRlaStartFuzz (Polling, Clca)

    MultiContestCombineData: specify the contests with exact number of votes

    OneAuditTest : One OA contest
        makeOneAuditTestContests: multiple OA contests
        used by RunRlaCreateOneAudit


    // Simulation of Raire Contest; pass in the parameters and simulate the cvrs; then call raire library to generate the assertions
    simulateRaireTestContest: single raire contest

    makeTestContestOAIrv : One OA IRV contest (not used?)

    ContestForTesting.makeContestFromCrvs(): single contest, make cvrs first


/////////////////////////////////////////

you could say theres two kinds of Contests, Regular and Irv
you could say theres two kinds of Audits, Polling and Clca
if a Clca has pools, then its a OneAudit with ClcaAssorterOneAudit

| audit   | contest | assorters                               |
|---------|---------|-----------------------------------------|
| polling | regular | PAssorter                               |
| polling | irv     | RaireAssorter                           |
| clca    | regular | ClcaAssorter                            |
| clca    | regular | ClcaAssorterOneAudit                    |
| clca    | irv     | ClcaAssorter with RaireAssorter         |
| clca    | irv     | ClcaAssorterOneAudit with RaireAssorter |

ContestIF
    Contest
        DhondtContest
    RaireContest

ContestUnderAudit
    hasa ContestIF
    hasa List<PrimitiveAssorter>
    hasa List<ClcaAssorter>, (if Clca): (if ClcaAssorterOneAudit, then its OneAudit)

->subclass RaireContestUnderAudit
    hasa RaireContest
    hasa List<RaireAssorter>
    hasa List<ClcaAssorter>, (if Clca): (if ClcaAssorterOneAudit, then its OneAudit)

AssorterIF (aka PrimitiveAssorter)
    PluralityAssorter
    AboveThreshold
    BelowThreshold
    DhondtAssorter
    RaireAssorter

ClcaAssorter
    hasa AssorterIF

->subclass ClcaAssorterOneAudit

Assertion
    hasa AssorterIF

->subclass ClcaAssertion
        hasa ClcaAssorter


///////////////

In the old days, as they say, we only had zeros. Then ones were invented and we could start getting work done. 
When God created Fortran and gave us Fortran Formatted I/O, we could start to share data. That is, as long as the person
you wanted to share with was using the same hardware floating point format, the same hardware endianess, the same operating system and the same compiler.
And lastly, you had to go find the program that wrote the data, and copy the Fortran format statement into your reading program. 
Then you could share data. This worked until the total number of programmers became greater than 42, and you couldnt remember all of their names.

So this was the problem that NetCDF (and many others) set out to solve. Create a binary file format for scientific data that could be shared across
hardware, software, and the great religious chasms known as programming languages. NetCDF-3 followed 
the Fortran-77 data model of fixed length rectangular arrays of numbers in row-major order. The NetCDF "Classic Format" specification fits
on a single one-sided page of paper. When I first implemented a NetCDF-3 reader in Java, it probably took less than 100 lines of a notoriously
verbose language. Did I mention it was simple? Like all great technology, it made itself invisible by just reliably working.

Other efforts to share scientific data designed from around the same time, were also widely successful and often brilliant in their own ways.
The World Meterological Organization (WMO) needed a variable length binary format for weather observations from around the world, with severe constraints
on communication bandwidth. They also needed an exchange file format for georeferenced 2-D gridded data. These became BUFR and GRIB, respectively.
Both rely on metadata tables that are seperately stored and maintained from the data in order to know what the data means, aka semantics, and in BUFR's case, 
to be able to even read the data, aka syntax. A correctly managed central repository could have made the dependence on external tables a non-problem, and
indeed the formal design requires just that. The communities of practice writing these files, usually national weather services, failed rather
miserably on this account, caught between a rock of time critical operational services and a hard place of plodding world bureacracies. 
Certain weather services are better than others, and for sure the later version of GRIB and BUFR offer some improvements. Unfortunately to this day
to read data in BUFR or GRIB from a source you dont personally have experience with requires tracking down the program that wrote the data, and finding and copying
their table into your reading software. And yes, there are more than 42 of them.

The strength of NetCDF-3 is its simple format and its use of arbitrary key/value metadata pairs called attributes. Attributes allow the user to
document their data in whatever way they like, no permission needed from the NetCDF library or from the WMO committee on What Attributes Are Allowed.

The weakness of NetCDF-3 is its simple format and its use of arbitrary key/value metadata pairs called attributes. Arbitrary means its up
to the writer as to what attributes are put into the file. We say you can use NetCDF to write crap Fortran-like files, just like
you can use modern languages to write spaghetti-code Fortran-like programs. NetCDF solves the syntactical problems of reading shared data, but not the 
semantic problems. WMO's efforts to control the metadata wasn't misguided, just poorly engineered. In the place of central tables,
sets of semantic conventions have emerged to specify metadata required for specific scientific communities. The best example of this
are the Climate and Forecast (CF) conventions for climate and forecast model data output. These communities are like "vertical markets", and
NetCDF can be used across them.

The simplicity of NetCDF's Fortran-77 data model eventually found its limits in Exoscale data sets that beg for more sophisticated 
data structures and ways to speed up data access. Heres where Ed Hartnett NetCDF story begins. Ed merged the NetCDF-3 and HDF5 
data models and APIs to create NetCDF-4. He built the NetCDF-4 library so it could use both the classic NetCDF-3 file format, or 
the HDF5 format as its storage layer. 

HDF is another important general purpose scientific data format. While NetCDF-3 followed the KISS principle, HDF went for the
feature rich, kitchen sink approach. It is used in a much wider way, eg in biology, financials, etc. Its APIs are correspondingly
more complex and general purpose. 

Limits of HDF5. Dimension scales vs Dimensions. Single threaded.

