# Development
last changed: 11/10/2024

## TODO

### core
* raire library
* hybrid audits

### sampling
* Consistent sampling - wtf and doc, test against shangrla (1)
* Run Audits for all assertions (2)
* Estimate sample sizes for all assertions (3)
* Parallelization
* SecureRandom must be deterministic using a given seed, so verifiers can test

### multiple rounds
* Serialization of intermediate stages
* Consistent sampling 
* Estimate sample sizes for all assertions

### interface
* CLI
* web server


## Clients

We need interfaces for testing. How elaborate should we make those?

* remote clients
* web?
* swing?
* arlo?
* colorado-rla?

## Questions

### N_c
What is effect of overestimating N_c, the bound on the number of cards for contest c?
If each of these gets conveerted to a 2-vote overstatement, which the sampleSize is highly sensisitive to.

### Check sample size

What about when you start an audit, then more CVRs arrive?
Or even just check what your sample sizes are, based on info you have ??

### Compare alternatives

Running the workflow with other RiskTestingFn, BettingFn, etc.

### Databases

If everything is stored in the database, then you have to trust the database software. If you have some kind of BB
with the raw data that anyone caan read, then you can have 3rd party verifiers. Otherwise what do you have?
A PostGres DB that you have to trust.

Theres not a problem using a DB, but you should be able to recreate it from the raw data.

OTOH the RLA should detect a compromised DB?