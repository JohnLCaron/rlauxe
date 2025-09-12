Use Cases
9/12/2025

core
  CvrExport    ( val id: String, val group: Int, val votes: Map<Int, IntArray>))
  AuditableCard( val location: String, val index: Int, val prn: Long, val phantom: Boolean, val contests: IntArray, val votes: List<IntArray>?, val poolId: Int? )
  Cvr          ( val id: String, val votes: Map<Int, IntArray>, val phantom: Boolean, val poolId: Int?)

  CardSortMerge: CvrExport -> AuditableCard (sorted)

Also have (but not used yet):
  CardLocation(  val location: String, val phantom: Boolean, val cardStyle: CardStyle?, val contestIds: List<Int>? = null)


SF
 DominionCvrExportJson
  CVR_Export_20241202143051.zip -> CvrExport_xxxx and manifests -> DominionCvrSummary -> CvrExport.csv

CORLA
  2024 3.241.120 ballot cast
  CO dont publish the CVRs, just precinct totals, see 2024GeneralPrecinctLevelResults.csv/zip/xlsx 
  detail.xls has summary by contest broken out by county, in a multipage excel file
  detail.xml has same info in xml file
  round1/contest.csv has summary of each round and we use these fields from it to make the contest: 
    contest_name,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,gamma,optimistic_samples_to_audit,estimated_samples_to_audit
  Note that this gives us the number of samples used in each audit round, from the CORLA software. TODO compare with our estimates

  createColoradoElectionFromDetailXmlAndPrecincts: contestRound, electionDetailXml, precinctResults -> precinctCvrs -> CvrExport.csv
  createCorla2024sortedCards: use CardSortMerge to convert to AuditableCard, assign prn, sort and write sortedCards (900 Mb, 120 Mb zipped)


BoulderCounty
  2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx
  