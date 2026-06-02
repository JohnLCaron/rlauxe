# CORLA Verification notes

_last changed 06/02/2026_

See   https://docs.google.com/document/d/1bAZCWKFRmo6W3K2zC604IKoMrVNvETx3muyo_U_ENuI/edit?tab=t.0#heading=h.1lgfbw97qzhb

# County Support for Public Election Verification

Pilot the involvement of audit verifiers for the RLA of the June 2026 primary.
La Plata, Weld, Boulder and El Paso.

* Before the random seed is drawn, a redacted version of the CVR file that was submitted to the state must be produced and a SHA256 hash of that file made publicly available.
* By day 13 (after the election), the random seed is selected.
* At least a day before the RLA, which could be the same date as the selection of the random seed, the county will make the following files available on the county web site:
    * the redacted CVR file
    * the list of ballot sheets to be audited, identified by Imprinted Id.
    * the number of audit boards that the county will use to audit the ballot sheets.
    * the ballot manifest
    * the summary results file that was uploaded to the SoS at the same time that the unredacted CVR file and the ballot manifest was uploaded.

Questions:
* I think "ballot sheet" = card in SHANGRLA terminology? YES
* Does each CVR = 1 ballot sheet ? Even when there are multiple "sheets" per ballot? YES
* What does the redaction of the CVR file do? Are they aggregating ballots in some way (like Boulder County) or is there one line per CVR ?
* What does the ballot manifest look like? Does each sheet have their own entry?
* Is there a seperate "trusted maximum" number of cards for each contest? Or do we just use the CVR count?
* 
