# Relaxed Assertions
10/3/2026

<a href="https://johnlcaron.github.io/rlauxe/docs/plots2/dhondt/RelaxedSeats.html" rel="RelaxedSeats">![RelaxedSeats](plots2/dhondt/RelaxedSeats.png)</a>

**Uniform Relaxation** across all contests ("Constituencies"), choose the assertion that has the most impact on the number of samples needed for the audit. This is calculated by finding the difference between an assertion's estimated Mvrs (nmvrs) and the next highest assertion's nmvrs from the same contest. Once the largest-impact assertion is found, that contest's sample limit is set to the next highest assertion's nmvrs. Then all the assertions for that contest with estimated nmvrs higher than the sample limit will fail.

The plot shows, starting at the rightmost point which has no failing assertions, the effect of successively failing the next largest-impact assertion. 

**Party Seat Ranges** are calculated from the latest draft of the paper (Ideas for bounding sample sizes for Belgian
RLAs)[papers/DhondtRelaxedAssertions.1001.pdf]. Each time another assertion is "failed", run the complete RelaxedAssertions algoritm to calculate bounds on each party's winning seat count. The plot shows these as error bars, as well as the reported number of seats won as points.