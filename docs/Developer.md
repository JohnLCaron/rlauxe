# Developer Notes
_12/13/2025_

## Prerequisites

1. A git client that is compatible with github.
2. **Java 21+**. Install as needed, and make it your default Java when working with rlauxe.
3. The correct version of gradle and kotlin will be installed when you invoke a gradle command.


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

If the library has changed on github and you need to update it:

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

### Run the tests using gradle

To build the complete library and run the standard tests:

```
    cd <devhome>/rlauxe
    ./gradlew build
```

## Using IntelliJ

We recommend that you use the IntelliJ IDE if you plan on doing any Java/Kotlin coding.

Do steps 1 and 2 of the prerequisites. Then, in the IntelliJ top menu:

1. File / New / "Project from existing sources"
2. in popup window, navigate to _devhome/rlauxe_ and select that directory
3. choose "Import project from existing model" / Gradle

IntelliJ will create and populate an IntelliJ project with the rlauxe sources. 

To build the library, from the topmenu:  Build / Build Project (Ctrl-F9)

To run the core tests, from the left Project Panel source tree, navigate to the _core/src/test/kotlin/org/cryptobiotic/rlauxe_
directory, right click on the directory name, choose "Run tests in ...". If that menu option isnt shown, check if you're in the 
main source tree instead of the test source tree.

To run individual tests, go to the test source, IntelliJ will place a clickable green button in the left margin wherever theres a 
runnable test.

There's lots of online help for using IntelliJ.


## Modules

* **cases**: code to create case studies
* **core**: core library
* **docs**: documentation
* **libs**: local copy of raire-java library
* **plots**: code to generate plots for documentation


## Test Cases

The repo contains all the test case data, except for San Francisco. Download

  https://www.sfelections.org/results/20241105/data/20241203/CVR_Export_20241202143051.zip

into $testdataDir/cases/sf2024.

Then run _createSf2024CvrExport()_ in _cases/src/test/kotlin/org/cryptobiotic/rlauxe/sf/CreateSf2024CvrExport.kt_
to generate crvExport.csv into testdataDir/cases/sf2024. This only needs to be done one time.

All the test cases can be generated from:

_cases/src/test/kotlin/org/cryptobiotic/util/TestGenerateAllUseCases.kt_.

## rlauxe viewer

Download the rlauxe-viewer repo and follow instructions there to view Audit Records and run audits on them, in particular any of the
test cases.

**Caveat Emptor**: The serialization formats are undergoing rapid changes, and no backwards compatibility (yet). Expect that
if you download a new version of the library, you will have to regenerate audit records, and download the latest rlauxe viewer
to view them.


# Random notes and stats

## Code Coverage (Lines of Codes)

 core tests

| date       | pct    | cover/total LOC |
|------------|--------|-----------------|
| 11/20/2025 | 84.0 % | 5602/6667       |
| 11/25/2025 | 85.2 % | 5229/6136       |
| 11/28/2025 | 85.9 % | 5188/6039       |
| 11/29/2025 | 86.3 % | 5208/6034       |
| 11/30/2025 | 86.7 % | 5255/6058       |
| 12/04/2025 | 85.0 % | 5327/6265       |
| 12/10/2025 | 80.5 % | 5338/6634       |
| 12/10/2025 | 82.8 % | 5310/6411       |

 core + cases tests 

| date       | pct    | cover/total LOC |
|------------|--------|-----------------|
| 11/28/2025 | 79.3 % | 6417/8094       |
| 11/29/2025 | 79.6 % | 6434/8087       |
| 11/29/2025 | 81.4 % | 6479/7962       |
| 12/04/2025 | 81.7 % | 6530/7994       |
| 12/10/2025 | 78.4 % | 6597/8412       |
| 12/10/2025 | 78.4 % | 6597/8412       |




## UML
last changed: 03/10/2025

![rlauxe core UML](images/rlauxeUML.svg)

![rlauxe Audit UML](images/rlauxeAuditUML.svg)

![rlauxe JSON UML](images/rlauxeJson.svg)

# TODO 11/29/25

* hasStyle
* raire, oaIrv
* review strategies and fuzzing in estimation
* review strategies and fuzzing in auditing
* replace old plots
* test dhondt, threshold assorters


# TODO 12/11/25 (Belgium)

* consolidate over counties
* include undervotes
* assertions that look at collections of parties. (Vaness)
* choose an audit size and measure the risk.
* get code stable for review
* remove /home/stormy

