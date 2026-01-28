# Betting risk function
_last changed 01/27/2026_

A _risk function_ evaluates the probability that an assertion about an election is true or not. 
Rlauxe has two risk functions, one for Clca audits (BettingMart) and one for polling audits (AlphaMart).
AlphaMart is formally equivilent to BettingMart, so we will just describe BettingMart.

We have a sequence of ballot cards that have been randomly selected from the population of all ballot cards for a contest in an election.
For each assertion, each card is assigned an _assort value_ which is a double precision number So we have a sequence of
doubles (aka a "sequence of samples") that are fed into the risk function. For the ith sample:

    x_i = the assort value, 0 <= x_i <= upper
    µ_i = the expected value of x_i given the previous samples, if the assertion is false (very close to 1/2 most of the time)
    λ_i = the "bet" placed on the ith sample, based on the previous samples, 0 <= λ_i <= 2 .

    payoff_i = (1 + λ_i (x_i − µ_i)) = the payoff of the ith bet
    T_i = Prod (payoff_i, i= 1..i) = the product of the payoffs aka "testStatistic"

When T_i > 1/alpha then informally we can say that the assertion is true within the risk limit of alpha. So if alpha = .05, 
T_i must be >= 20.

## Estimating samples needed for CLCA

For CLCA, the x_i are constant when the mvr and the cvr agree. Call that value noerror; it is always > .5. 

If we approximate µ_i = .5 and use a constant bet of λc, then for the nth testStatistic  

    T_n = (1 + λc (noerror − .5))^n
    payoff = (1 + λc (noerror − .5)
    
    (1 + λc (noerror − .5))^n = (1/alpha)
    n * ln(1 + λc (noerror − .5)) = ln(1/alpha)
    n = ln(1/alpha) / ln(1 + λc (noerror − .5))
    n = ln(1/alpha) / ln(payoff)
    
which is a closed form expression for the estimated samples needed.

To minimize n, we want to maximize payoff, so we want to maximize λc.
    

## Stalled audit

The gambler is not permitted to borrow money, so to ensure that when X_i = 0 (corresponding to
losing the ith bet) the gambler does not end up in debt (Mi < 0), λi cannot exceed 1/µi.
In practice, λi < 1/µi to prevent stalls.

Since T_i is the product of the payoff_i, if ever payoff_i = 0, then the audit would never recover. So if 

    1 + λ_i (x_i − µ_i) > 0
    λ_i (x_i − µ_i) > -1
    λ_i (µ_i - x_i) < 1
    λ_i (µ_i) < 1    when x_i == 0
    λ_i < 1/u_i

how much less ?
    
