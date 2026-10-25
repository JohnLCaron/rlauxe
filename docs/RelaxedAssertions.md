# Relaxed Assertions on Belgium Chambre des représentants Elections
10/25/2026

**Uniform Relaxation**. Across all contests (aka "Constituencies"), iteratively choose the assertion that has the most impact on the number of samples needed for the entire audit. Most-impact is calculated by finding the difference between an assertion's estimated MVRs (nmvrs) and the next highest assertion's nmvrs from the same contest. Once the largest-impact assertion is found, that contest's sample limit is set to the next highest assertion's nmvrs. Then all assertions for that contest with estimated nmvrs higher than the sample limit will fail. 

This algorithm does not try to minimimize the seat spread, does not try to control how many ballots any Constituency will need to sample, and has no bias about any party's results.

The plot shows, starting at the rightmost column which has no failing assertions, the effect of successively failing the next largest-impact assertion, as you move left across the plot. 

The plots below shows the range of seats as error bars, as well as the reported number of seats as points.

**Party Seat Ranges** are calculated from the latest draft of the paper [Ideas for bounding sample sizes for Belgian
RLAs](papers/DhondtRelaxedAssertions.1001.pdf). Each time another assertion is "failed", we run the complete _RelaxedAssertions_ algorithm to calculate bounds on all partys' winning seat count. 

The tables below shows the initial and final samples needed by each constituency after removing the first six most-impact assertions, which in both years brings
the total number of samples needed below 10,116 and 9,210 in 2024 and 2019 respectively.

Note that this is the "best case" scenario where no discrepencies are found between the sampled ballots and the CVRs. 
In the presence of discrepencies, the sample sizes will be higher.

## 2024

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/dhondt/RelaxedSeats2024.html" rel="RelaxedSeats2024">![RelaxedSeats2024](plots2/dhondt/RelaxedSeats2024.png)</a>

_(click on image to get interactive html graph)_


| contest        | initialSamples | relaxedSamples | failures |
|----------------|----------------|----------------|----------|
| Anvers         | 12016          | 2828           | 2        |
| Bruxelles      | 7811           | 1756           | 1        |
| FlandreWest    | 985            | 985            | 0        |
| FlandreEast    | 3567           | 787            | 1        |
| Limbourg       | 673            | 673            | 0        |
| Liège          | 5914           | 640            | 1        |
| BrabantWallon  | 622            | 622            | 0        |
| Luxembourg     | 574            | 574            | 0        |
| Hainaut        | 10449          | 549            | 1        |
| BrabantFlamant | 388            | 388            | 0        |
| Namur          | 314            | 314            | 0        |
| Total          | 43313          | 10116          | 6        |

## 2019

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/dhondt/RelaxedSeats2019.html" rel="RelaxedSeats2019">![RelaxedSeats2019](plots2/dhondt/RelaxedSeats2019.png)</a>


| contest        | initialSamples | relaxedSamples | failures |
|----------------|----------------|----------------|----------|
| Anvers         | 8227           | 1826           | 1        |
| Hainaut        | 3393           | 1762           | 1        |
| FlandreEast    | 1377           | 1377           | 0        |
| FlandreWest    | 962            | 962            | 0        |
| Liège          | 2283           | 802            | 1        |
| BrabantFlamant | 1517           | 690            | 1        |
| Limbourg       | 674            | 674            | 0        |
| Bruxelles      | 1733           | 508            | 1        |
| Namur          | 2357           | 286            | 1        |
| Luxembourg     | 169            | 169            | 0        |
| BrabantWallon  | 154            | 154            | 0        |
| Total          | 22846          | 9210           | 6        |