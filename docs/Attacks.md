=============================================

Attacks

1. Clca, SwitchWinnerMinAttack

* Create two-candidate Contest with given margin. No undervotes, no phantoms. candB is reported winner. Generate cards to match.
* Let diff = margin * Nc. In the mvrs, switch (diff + 3)/2 votes for candB to candA. Now candA is winner by 1 vote.


2. Clca, HideInUndervotesAttack

* Create two-candidate Contest with
  undervotes = Nc*undervotePct.
  nvotes = Nc - undervotes
  diff = undervotes + 1; win = (nvotes + diff)/2, lose = nvotes - win
  candB is reported winner. Generate cards to match.
* Let In the mvrs, switch all undervotes to candA. Now candA is winner by 1 vote.


3. Clca, HideInOtherGroupAttack

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner.
  Let diff = margin * Nc.
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.


4. OneAudit, HideInOtherPoolAttack; hasStyles = false

* create 2 card styles, style1 = contest1, contest2, style2 = contest2

* Create two-candidate Contest1 with given margin. No undervotes, no phantoms. candB is reported winner.
  Let diff = margin * Nc.
  In the cardManifest, switch (diff + 1) cards to style2
  In the mvrs, switch (diff + 1) cvrs from candB to candA

* the cardManifest tabulation fails for Clca, since the cvrs must be present.
