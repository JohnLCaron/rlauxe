# CORLA notes


_last changed 04/24/2026_

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
* I think "ballot sheet" = card in SHANGRLA terminology?
* Does each CVR = 1 ballot sheet ? Even when there are multiple "sheets" per ballot?
* What does the redaction of the CVR file do? Are they aggregating ballots in some way (like Boulder County) or is there one line per CVR ?
* What does the ballot manifest look like? Does each sheet have their own entry?
* Is there a seperate "trusted maximum" number of cards for each contest? Or do we just use the CVR count?

# Corla Auditing

## How does Corla do their county-level sampling?

There is one "target" contest for each county. Its probably contained in a single county. If not, then the audit only measures the contest in that county.

All of the cards in the county constitute the population (ie the denominator of the fully diluted margin calculation).
The sampling is uniform across all cards in the county.
The number of cards needed for the target contest are estimated at each round.
Because of the randomness of the sequence of samples, there is variance in the actual number of cards that contain the target contest in the sample.
The variance can be estimated, and a quantile of the distribution can be selected as the "estimated sample size". I dont know what quantile Corla uses.
This only affects the number of rounds needed, not the rresult.

The selected cards are found and an audit of all contests on the card is done.
Because the sampling is uniform for all contests, we can use the results to measure the risk of all the contests, not just the target contest.

## How does Corla do their state-level sampling?

There are also one or more statewide contests selected to sample.

Vanessa thinks they do an independent sampling for this, but use the same seed.
Presumably the sampling is uniform across all cards in the state.
The number of cards needed for the target statewide contest(s) are estimated at each round.
If more than one statewide contest, presumably the larger of the estimates is used.

Presumably the selected cards are divided into the counties where the cards live, and sample list is given to the county (along with the selected ballots for the target county contest) to audit. Each county returns their audit results. The card id can be used to determine whether the card is used for the county-level and/or the statewide contest risk calculations. 

# Simulation of Corla auditing

I propose to try to simulate Corla auditing in rlauxe. The simulation strongly depends on accurately modeling what actual CVRS look like, eg are there multiple cards per ballot, are there multiple ballot styles per precinct or county, how many of each card/ballot style there are, and the undervote count for each card style.

If there are CVRS, then all those questions are answered. With just subtotals by precinct or county, the simulation wont be that accurate, unless you also know the card styles. That would allow quite accurate simulation.

Smaller populations (like precincts) are preferable because the card styles within the population are likely to more uniform.

## 2024 General Election

I have the 2024 Coordinated Election files downloaded from https://www.coloradosos.gov/pubs/elections/auditCenter.html in March 2025. This data is no longer on the website, though there is a notice "For historical audit data email public.elections@coloradosos.gov". There are precinct level subtotals by candidate. However there is no information on card styles.

## 2025  Coordinated Election

I have the 2025 Coordinated Election files downloaded from https://www.coloradosos.gov/pubs/elections/auditCenter.html on 4/22/2026. There is no precinct level data, only county level subtotals. Again, this would be adequate if there was also card style information.

## Risk measurements 

For all contests contained within a county, we should be able to show accurate risk measurements with the existing rlauxe library.

For contests spanning counties, we will have to add stratified risk measurement calculations to the rlauxe library. These will likely be based on the ALPHA and SWEETER papers from Philip Stark et al. Finding optimal stratified sampling strategies are probably not possible at this point. However, just measuring the risk from existing stratified samples is probably tractable.

## Simulating alternative sampling

If we can accurately simulate Corla, we can try alternative sampling designs based on consistent sampling, which allows "card style data" to be used to make the sample sizes smaller. It would also eliminate the variance of the sample estimation (when there are no errors), and so should allow the audit to complete in one round when there are no errors found.



# Corla 2022 Coordinated election 

Neal McBurnett 2023-12-15 "Corla Accomplishments" slidedeck
https://neal.mcburnett.org/elections/corla-beac/

Summarize Audit of 2022 Coordinated election in Colorado
also see slide 12 for # Cvrs audited in "single county audit"

* > 2,508,830 ballots cast statewide (undervotes make exact number hard to find)
* 3,526,411 ballot cards in the state (multiple cards per ballot in some counties)
* 6,454 ballot cards selected (based on margin, 0.2% overall)
* 233,223 votes compared! (36 per card on average) (public data from Audit Center)
* 186 discrepancies - 0.08%, apparently mostly wrong ballot or entry error
  * 116 Wrong Ballot
  *  44 Audit Board Error
  *  16 Adjudication Error
  *  6 Voting System Limitation
  *  3 Voter Mistake
  *  1 Ambiguous Voter Intent
* Audit data for 981 contests
* RLAs of 68 contests (some partial and thus artificial...)
* Many more opportunistic contest audits, below risk limit. 
* Opportunistic sampling across counties is not uniformly random, so very tricky to calculate rigorous risk levels for those.

* 2023 AuditData for Arvada Mayor contest
  * About a 1% margin of victory, small subset of the county, thus quite hard to audit
  * 98.4% in Jefferson county, where 46 ballots for mayor were audited
  * Audit found zero discrepancies - 46 for 46!
  * Measured risk of about 80% (disappointing, but less scary than it seems)
  
* Boulder did style based sampling for their 2023 IRV (mayor) audit, with new software
* better to audit close contests, even if manageable sample size requires large risk limit
* We should really design districting and election to avoid need for redaction
* Improvements
  * Now typically picking just one local contest per county
  * Before contest selection, publish data on potential contests to audit to help public provide input
  * Best to guarantee a risk limit for all contests
  * Better to audit a close contest at a 50% risk limit, than a landslide one at 3%
  * Explore recent auditing innovations like Non(c)euch and ONEAudit
* Improvement 5: Minimum BallotCount to Audit perCounty
  * Often there is no suitable county-level contest with a contest close enough to drive a good audit
  * SoS often audits statewide contests using county margin
  * should establish minimum ballot count to audit per county for opportunistic audits, quality control
* Improvement 6: Avoid Needfor ExtraRounds
  * Audit sometimes has a single discrepancy, and requires whole audit team to return to audit just a few more ballots
  * Avoid those extra rounds via a small amount of oversampling
* Improvement 8: LeverageBallot Images
  * Publish hashes of each image ASAP after scanning
* [Principles and Best Practices for Post-Election Audits 2018](https://electionaudits.org/principles/)

Probably this presentation is referring to the 2023 Coordinated Election

# Next Steps paper

The report [Next Steps for the Colorado Risk-Limiting Audit (CORLA) Program](papers/Corla.pdf) (2018) 

Counties that have CVRS: "CVR Counties" can perform CLCA.
Counties that do not have CVRS: "no-CVR Counties" or "legacy". Assume can perform Polling audits.

The following issues should be addressed:

1. The current version (1.1.0) of RLATool needs to be modified to
  recognize and group together contests that cross jurisdictional boundaries.
  Currently, it treats every contest as if it were entirely contained in a single county. 
2. It does not allow the user to select the sample size
3. It does not allow an unstratified random sample to be drawn across counties.
4. New statistical methods are needed to deal with contests that include both CVR counties and no-CVR counties. (I think this would be OneAudit)
5. Auditing contests that appear only on a subset of ballots can be made much more efficient if the sample can be drawn from
  just those ballots that contain the contest.

Its not clear if any of these issues have been addressed.

## Remarks

**Need to find out what the Corla sampling strategy is**. Apparently not "Consistent Sampling".

The paper predates OneAudit. I think that OneAudit would allow auditing both CVR and non-CVR together, but the OneAudit paper
doesnt mention Colorado, so theres a big if. 
But "All counties participating in the RLA have CVRs. San Juan County does a hand count and is exempted from the RLA."
So apparently unecessary.

SHANGRLA doesnt yet support stratified audits ("This is still a research project"), so theres a bigger IF. 

Philip says:

"I think Colorado doesn't need ONEAudit, since it has CVRs linked to each ballot sheet/card (if I understand correctly). The latest work on stratified sequential tests for the mean is here: https://arxiv.org/abs/2409.06680 (revised last month, 3/2026). The underlying calculations are in Python here: https://github.com/spertus/UI-TS

Some work would need to be done to integrate it into the NonnegMean class in SHANGRLA, track sample sizes by stratum, select sample sizes by stratum, etc.

The optimization over the composite null can't in general be done with methods in scipy.optimize: see the paper linked above. 
For some betting strategies the optimization is much simpler than for others. 

If this is only going to be used in "risk-measuring" mode (what's the attained risk for this particular combination of sample sizes and observed votes?) 
rather than "risk-limiting" mode (including estimating sample sizes needed to attain the risk limit, subject to assumptions about the true votes), 
there's a lot less to do."

# Notes on CORLA implementation

IntelliJ "Vulnerabile Dependencies" Code Analysys a few dozens of Security issues, many high severity. (4/19/2026)

Everything revolves around the database as global, mutable shared state. No real separation of business logic
from the persistence layer. So, difficult to evolve separately.

* uses sparkjava web framework (now abandoned), with Jetty providing the Servlet container.
* client written in typescript
* hibernate/jpa ORM with postgres database
* The auditing math is contained in a few dozen line of code in the Audit class.
* Uses BigDecimal instead of Double for some reason.
* Log4J 2.17.2 (not vulnerable to RCE attack, but stable release is 2.24.0)
* Gson 2.8.1 (should be upgraded to latest stable).
* MAven build
* Eclipse project source layout and build, and also a makefile build system, (maybe legacy).
* Travis CI ("Travis CI is no longer free for open source accounts")
* testng, mockito

Other issues that are not clear to me:

* **How is Sampling done?**
* Can CORLA efficiently do multiple contests at once?
* How does CORLA handle phantom records?
* How is batching of ballots for auditing done?

## Postgres schema

I think hibernate reads through the annotated classes and automatically constructs the ORM mapping?

server/eclipse-project/src/test/resources/SQL/corla.sql

````
create sequence hibernate_sequence;

alter sequence hibernate_sequence owner to corlaadmin;

create table assertion_context
(
    id                 bigint       not null,
    assumed_continuing varchar(255) not null
);

alter table assertion_context
    owner to corlaadmin;

create table asm_state
(
    id           bigint       not null
        primary key,
    asm_class    varchar(255) not null,
    asm_identity varchar(255),
    state_class  varchar(255),
    state_value  varchar(255),
    version      bigint
);

alter table asm_state
    owner to corlaadmin;

create table assertion
(
    assertion_type              varchar(31)      not null,
    id                          bigserial
        primary key,
    contest_name                varchar(255)     not null,
    current_risk                numeric(10, 8)   not null,
    difficulty                  double precision not null,
    diluted_margin              numeric(10, 8)   not null,
    estimated_samples_to_audit  integer          not null,
    loser                       varchar(255)     not null,
    margin                      integer          not null,
    one_vote_over_count         integer          not null,
    one_vote_under_count        integer          not null,
    optimistic_samples_to_audit integer          not null,
    other_count                 integer          not null,
    two_vote_over_count         integer          not null,
    two_vote_under_count        integer          not null,
    version                     bigint,
    winner                      varchar(255)     not null
);

alter table assertion
    owner to corlaadmin;

create table assertion_assumed_continuing
(
    id                 bigint       not null
        constraint fk357sixi5a6nt1sus8jdk1pcpn
            references assertion,
    assumed_continuing varchar(255) not null
);

alter table assertion_assumed_continuing
    owner to corlaadmin;

create table assertion_discrepancies
(
    id          bigint  not null
        constraint fkt31yi3mf6c9axmt1gn1mu33ea
            references assertion,
    discrepancy integer not null,
    cvr_id      bigint  not null,
    primary key (id, cvr_id)
);

alter table assertion_discrepancies
    owner to corlaadmin;

create table ballot_manifest_info
(
    id                      bigint       not null
        primary key,
    batch_id                varchar(255) not null,
    batch_size              integer      not null,
    county_id               bigint       not null,
    scanner_id              integer      not null,
    sequence_end            bigint       not null,
    sequence_start          bigint       not null,
    storage_location        varchar(255) not null,
    version                 bigint,
    ultimate_sequence_end   bigint,
    ultimate_sequence_start bigint,
    uri                     varchar(255)
);

alter table ballot_manifest_info
    owner to corlaadmin;

create index idx_bmi_county
    on ballot_manifest_info (county_id);

create index idx_bmi_seqs
    on ballot_manifest_info (sequence_start, sequence_end);

create table cast_vote_record
(
    id                bigint       not null
        primary key,
    audit_board_index integer,
    comment           varchar(255),
    cvr_id            bigint,
    ballot_type       varchar(255) not null,
    batch_id          varchar(255) not null,
    county_id         bigint       not null,
    cvr_number        integer      not null,
    imprinted_id      varchar(255) not null,
    record_id         integer      not null,
    record_type       varchar(255) not null,
    scanner_id        integer      not null,
    sequence_number   integer,
    timestamp         timestamp,
    version           bigint,
    rand              integer,
    revision          bigint,
    round_number      integer,
    uri               varchar(255),
    constraint uniquecvr
        unique (county_id, imprinted_id, record_type, revision)
);

alter table cast_vote_record
    owner to corlaadmin;

create index idx_cvr_county_type
    on cast_vote_record (county_id, record_type);

create index idx_cvr_county_cvr_number
    on cast_vote_record (county_id, cvr_number);

create index idx_cvr_county_cvr_number_type
    on cast_vote_record (county_id, cvr_number, record_type);

create index idx_cvr_county_sequence_number_type
    on cast_vote_record (county_id, sequence_number, record_type);

create index idx_cvr_county_imprinted_id_type
    on cast_vote_record (county_id, imprinted_id, record_type);

create index idx_cvr_uri
    on cast_vote_record (uri);

create table contest_result
(
    id              bigint       not null
        primary key,
    audit_reason    integer,
    ballot_count    bigint,
    contest_name    varchar(255) not null
        constraint idx_cr_contest
            unique,
    diluted_margin  numeric(19, 2),
    losers          text,
    max_margin      integer,
    min_margin      integer,
    version         bigint,
    winners         text,
    winners_allowed integer
);

alter table contest_result
    owner to corlaadmin;

create table comparison_audit
(
    audit_type                    varchar(31)    not null,
    id                            bigint         not null
        primary key,
    contest_cvr_ids               text,
    diluted_margin                numeric(10, 8) not null,
    audit_reason                  varchar(255)   not null,
    audit_status                  varchar(255)   not null,
    audited_sample_count          integer        not null,
    disagreement_count            integer        not null,
    estimated_recalculate_needed  boolean        not null,
    estimated_samples_to_audit    integer        not null,
    gamma                         numeric(10, 8) not null,
    one_vote_over_count           integer        not null,
    one_vote_under_count          integer        not null,
    optimistic_recalculate_needed boolean        not null,
    optimistic_samples_to_audit   integer        not null,
    other_count                   integer        not null,
    risk_limit                    numeric(10, 8) not null,
    two_vote_over_count           integer        not null,
    two_vote_under_count          integer        not null,
    version                       bigint,
    overstatements                numeric(19, 2),
    contest_result_id             bigint         not null
        constraint fkn14qkca2ilirtpr4xctw960pe
            references contest_result
);

alter table comparison_audit
    owner to corlaadmin;

create table audit_to_assertions
(
    id            bigint not null
        constraint fkgrx2l2qywbc3nv83iid55ql36
            references comparison_audit,
    assertions_id bigint not null
        constraint fkqomhyyib2xno6nq0wjpv95fs5
            references assertion
);

alter table audit_to_assertions
    owner to corlaadmin;

create table contest_vote_total
(
    result_id  bigint       not null
        constraint fkfjk25vmtng6dv2ejlp8eopy34
            references contest_result,
    vote_total integer,
    choice     varchar(255) not null,
    primary key (result_id, choice)
);

alter table contest_vote_total
    owner to corlaadmin;

create table county
(
    id      bigint       not null
        primary key,
    name    varchar(255) not null
        constraint uk_npkepig28dujo4w98bkmaclhp
            unique,
    version bigint
);

alter table county
    owner to corlaadmin;

create table administrator
(
    id               bigint       not null
        primary key,
    full_name        varchar(255) not null,
    last_login_time  timestamp,
    last_logout_time timestamp,
    type             varchar(255) not null,
    username         varchar(255) not null
        constraint uk_esogmqxeek1uwdyhxvubme3qf
            unique,
    version          bigint,
    county_id        bigint
        constraint fkh6rcfib1ishmhry9ctgm16gie
            references county
);

alter table administrator
    owner to corlaadmin;

create table contest
(
    id              bigint       not null
        primary key,
    description     varchar(255) not null,
    name            varchar(255) not null,
    sequence_number integer      not null,
    version         bigint,
    votes_allowed   integer      not null,
    winners_allowed integer      not null,
    county_id       bigint       not null
        constraint fk932jeyl0hqd21fmakkco5tfa3
            references county,
    constraint ukdv45ptogm326acwp45hm46uaf
        unique (name, county_id, description, votes_allowed)
);

alter table contest
    owner to corlaadmin;

create index idx_contest_name
    on contest (name);

create index idx_contest_name_county_description_votes_allowed
    on contest (name, county_id, description, votes_allowed);

create table contest_choice
(
    contest_id         bigint  not null
        constraint fknsr30axyiavqhyupxohtfy0sl
            references contest,
    description        varchar(255),
    fictitious         boolean not null,
    name               varchar(255),
    qualified_write_in boolean not null,
    index              integer not null,
    primary key (contest_id, index),
    constraint uka8o6q5yeepuy2cgnrbx3l1rka
        unique (contest_id, name)
);

alter table contest_choice
    owner to corlaadmin;

create table contests_to_contest_results
(
    contest_result_id bigint not null
        constraint fkr1jgmnxu2fbbvujdh3srjmot9
            references contest_result,
    contest_id        bigint not null
        constraint uk_t1qahmm5y32ovxtqxne8i7ou0
            unique
        constraint fki7qed7v0pkbi2bnd5fvujtp7
            references contest,
    primary key (contest_result_id, contest_id)
);

alter table contests_to_contest_results
    owner to corlaadmin;

create table counties_to_contest_results
(
    contest_result_id bigint not null
        constraint fk2h2muw290os109yqar5p4onms
            references contest_result,
    county_id         bigint not null
        constraint fk1ke574b6yqdc8ylu5xyqrounp
            references county,
    primary key (contest_result_id, county_id)
);

alter table counties_to_contest_results
    owner to corlaadmin;

create table county_contest_result
(
    id                   bigint  not null
        primary key,
    contest_ballot_count integer,
    county_ballot_count  integer,
    losers               text,
    max_margin           integer,
    min_margin           integer,
    version              bigint,
    winners              text,
    winners_allowed      integer not null,
    contest_id           bigint  not null
        constraint fkon2wldpt0279jqex3pjx1mhm7
            references contest,
    county_id            bigint  not null
        constraint fkcuw4fb39imk9pyw360bixorm3
            references county,
    constraint idx_ccr_county_contest
        unique (county_id, contest_id)
);

alter table county_contest_result
    owner to corlaadmin;

create index idx_ccr_county
    on county_contest_result (county_id);

create index idx_ccr_contest
    on county_contest_result (contest_id);

create table county_contest_vote_total
(
    result_id  bigint       not null
        constraint fkip5dfccmp5x5ubssgar17qpwk
            references county_contest_result,
    vote_total integer,
    choice     varchar(255) not null,
    primary key (result_id, choice)
);

alter table county_contest_vote_total
    owner to corlaadmin;

create table cvr_audit_info
(
    id                      bigint not null
        primary key,
    count_by_contest        text,
    multiplicity_by_contest text,
    disagreement            text   not null,
    discrepancy             text   not null,
    version                 bigint,
    acvr_id                 bigint
        constraint fk2n0rxgwa4njtnsm8l4hwc8khy
            references cast_vote_record,
    cvr_id                  bigint not null
        constraint fkdks3q3g0srpa44rkkoj3ilve6
            references cast_vote_record
);

alter table cvr_audit_info
    owner to corlaadmin;

create table contest_comparison_audit_disagreement
(
    contest_comparison_audit_id bigint not null
        constraint fkt490by57jb58ubropwn7kmadi
            references comparison_audit,
    cvr_audit_info_id           bigint not null
        constraint fkpfdns930t0qv905vbwhgcxnl2
            references cvr_audit_info,
    primary key (contest_comparison_audit_id, cvr_audit_info_id)
);

alter table contest_comparison_audit_disagreement
    owner to corlaadmin;

create table contest_comparison_audit_discrepancy
(
    contest_comparison_audit_id bigint not null
        constraint fkcajmftu1xv4jehnm5qhc35j9n
            references comparison_audit,
    discrepancy                 integer,
    cvr_audit_info_id           bigint not null
        constraint fk3la5frd86i29mlwjd8akjgpwp
            references cvr_audit_info,
    primary key (contest_comparison_audit_id, cvr_audit_info_id)
);

alter table contest_comparison_audit_discrepancy
    owner to corlaadmin;

create table cvr_contest_info
(
    cvr_id     bigint  not null
        constraint fkrsovkqe4e839e0aels78u7a3g
            references cast_vote_record,
    county_id  bigint,
    choices    varchar(1024),
    comment    varchar(255),
    consensus  varchar(255),
    contest_id bigint  not null
        constraint fke2fqsfmj0uqq311l4c3i0nt7r
            references contest,
    index      integer not null,
    primary key (cvr_id, index)
);

alter table cvr_contest_info
    owner to corlaadmin;

create index idx_cvrci_uri
    on cvr_contest_info (county_id, contest_id);

create table dos_dashboard
(
    id                  bigint not null
        primary key,
    canonical_choices   text,
    canonical_contests  text,
    election_date       timestamp,
    election_type       varchar(255),
    public_meeting_date timestamp,
    risk_limit          numeric(10, 8),
    seed                varchar(255),
    version             bigint
);

alter table dos_dashboard
    owner to corlaadmin;

create table generate_assertions_summary
(
    id           bigserial
        primary key,
    contest_name varchar(255) not null
        constraint uk_g5q4xm0ga61nbiccn44irhivx
            unique,
    error        varchar(255) not null,
    message      varchar(255) not null,
    version      bigint       not null,
    warning      varchar(255) not null,
    winner       varchar(255) not null
);

create table irv_ballot_interpretation
(
    id             bigint        not null
        primary key,
    cvr_number     integer       not null,
    imprinted_id   varchar(255)  not null,
    interpretation varchar(1024) not null,
    raw_choices    varchar(1024) not null,
    record_type    varchar(255)  not null,
    version        bigint,
    contest_id     bigint        not null
        constraint fkinw3u6cigskdttcwqosnsl98e
            references contest
            on delete cascade
);

alter table irv_ballot_interpretation
    owner to corlaadmin;

create table contest_to_audit
(
    dashboard_id bigint not null
        constraint fkjlw9bpyarqou0j26hq7mmq8qm
            references dos_dashboard,
    audit        varchar(255),
    contest_id   bigint not null
        constraint fkid09bdp5ifs6m4cnyw3ycyo1s
            references contest,
    reason       varchar(255)
);

alter table contest_to_audit
    owner to corlaadmin;

create table log
(
    id                  bigint       not null
        primary key,
    authentication_data varchar(255),
    client_host         varchar(255),
    hash                varchar(255) not null,
    information         varchar(255) not null,
    result_code         integer,
    timestamp           timestamp    not null,
    version             bigint,
    previous_entry      bigint
        constraint fkfw6ikly73lha9g9em13n3kat4
            references log
);

alter table log
    owner to corlaadmin;

create table tribute
(
    id                     bigint not null
        primary key,
    ballot_position        integer,
    batch_id               varchar(255),
    contest_name           varchar(255),
    county_id              bigint,
    rand                   integer,
    rand_sequence_position integer,
    scanner_id             integer,
    uri                    varchar(255),
    version                bigint
);

alter table tribute
    owner to corlaadmin;

create table uploaded_file
(
    id                       bigint       not null
        primary key,
    computed_hash            varchar(255) not null,
    approximate_record_count integer      not null,
    file                     oid          not null,
    filename                 varchar(255),
    size                     bigint       not null,
    timestamp                timestamp    not null,
    version                  bigint,
    result                   text,
    status                   varchar(255) not null,
    submitted_hash           varchar(255) not null,
    county_id                bigint       not null
        constraint fk8gh92iwaes042cc1uvi6714yj
            references county
);

alter table uploaded_file
    owner to corlaadmin;

create table county_dashboard
(
    id                       bigint  not null
        primary key,
    audit_board_count        integer,
    driving_contests         text,
    audit_timestamp          timestamp,
    audited_prefix_length    integer,
    audited_sample_count     integer,
    ballots_audited          integer not null,
    ballots_in_manifest      integer not null,
    current_round_index      integer,
    cvr_import_error_message varchar(255),
    cvr_import_state         varchar(255),
    cvr_import_timestamp     timestamp,
    cvrs_imported            integer not null,
    disagreements            text    not null,
    discrepancies            text    not null,
    version                  bigint,
    county_id                bigint  not null
        constraint uk_6lcjowb4rw9xav8nqnf5v2klk
            unique
        constraint fk1bg939xcuwen7fohfkdx10ueb
            references county,
    cvr_file_id              bigint
        constraint fk6rb04heyw700ep1ynn0r31xv3
            references uploaded_file,
    manifest_file_id         bigint
        constraint fkrs4q3gwfv0up7swx7q1q6xlwo
            references uploaded_file
);

alter table county_dashboard
    owner to corlaadmin;

create table audit_board
(
    dashboard_id  bigint    not null
        constraint fkai07es6t6bdw8hidapxxa5xnp
            references county_dashboard,
    members       text,
    sign_in_time  timestamp not null,
    sign_out_time timestamp,
    index         integer   not null,
    primary key (dashboard_id, index)
);

alter table audit_board
    owner to corlaadmin;

create table audit_intermediate_report
(
    dashboard_id bigint  not null
        constraint fkmvj30ou8ik3u7avvycsw0vjx8
            references county_dashboard,
    report       varchar(255),
    timestamp    timestamp,
    index        integer not null,
    primary key (dashboard_id, index)
);

alter table audit_intermediate_report
    owner to corlaadmin;

create table audit_investigation_report
(
    dashboard_id bigint  not null
        constraint fkdox65w3y11hyhtcba5hrekq9u
            references county_dashboard,
    name         varchar(255),
    report       varchar(255),
    timestamp    timestamp,
    index        integer not null,
    primary key (dashboard_id, index)
);

alter table audit_investigation_report
    owner to corlaadmin;

create table county_contest_comparison_audit
(
    id                            bigint         not null
        primary key,
    diluted_margin                numeric(10, 8) not null,
    audit_reason                  varchar(255)   not null,
    audit_status                  varchar(255)   not null,
    audited_sample_count          integer        not null,
    disagreement_count            integer        not null,
    estimated_recalculate_needed  boolean        not null,
    estimated_samples_to_audit    integer        not null,
    gamma                         numeric(10, 8) not null,
    one_vote_over_count           integer        not null,
    one_vote_under_count          integer        not null,
    optimistic_recalculate_needed boolean        not null,
    optimistic_samples_to_audit   integer        not null,
    other_count                   integer        not null,
    risk_limit                    numeric(10, 8) not null,
    two_vote_over_count           integer        not null,
    two_vote_under_count          integer        not null,
    version                       bigint,
    contest_id                    bigint         not null
        constraint fk8te9gv7q10wxbhg5pgttbj3mv
            references contest,
    contest_result_id             bigint         not null
        constraint fkag9u8fyqni2ehb2dtqop4pox8
            references contest_result,
    dashboard_id                  bigint         not null
        constraint fksycb9uto400qabgb97d4ihbat
            references county_dashboard
);

alter table county_contest_comparison_audit
    owner to corlaadmin;

create index idx_ccca_dashboard
    on county_contest_comparison_audit (dashboard_id);

create table county_contest_comparison_audit_disagreement
(
    county_contest_comparison_audit_id bigint not null
        constraint fk7yt9a4fjcdctwmftwwsksdnma
            references county_contest_comparison_audit,
    cvr_audit_info_id                  bigint not null
        constraint fk9lhehe4o2dgqde06pxycydlu6
            references cvr_audit_info,
    primary key (county_contest_comparison_audit_id, cvr_audit_info_id)
);

alter table county_contest_comparison_audit_disagreement
    owner to corlaadmin;

create table county_contest_comparison_audit_discrepancy
(
    county_contest_comparison_audit_id bigint not null
        constraint fk39q8rjoa19c4fdjmv4m9iir06
            references county_contest_comparison_audit,
    discrepancy                        integer,
    cvr_audit_info_id                  bigint not null
        constraint fkpe25737bc4mpt170y53ba7il2
            references cvr_audit_info,
    primary key (county_contest_comparison_audit_id, cvr_audit_info_id)
);

alter table county_contest_comparison_audit_discrepancy
    owner to corlaadmin;

create table county_dashboard_to_comparison_audit
(
    dashboard_id        bigint not null
        constraint fkds9j4o8el1f4nepf2677hvs5o
            references county_dashboard,
    comparison_audit_id bigint not null
        constraint fksliko6ckjcr7wvmicuqyreopl
            references comparison_audit,
    primary key (dashboard_id, comparison_audit_id)
);

alter table county_dashboard_to_comparison_audit
    owner to corlaadmin;

create table round
(
    dashboard_id                   bigint    not null
        constraint fke3kvxe5r43a4xmeugp8lnme9e
            references county_dashboard,
    ballot_sequence_assignment     text      not null,
    actual_audited_prefix_length   integer,
    actual_count                   integer   not null,
    audit_subsequence              text      not null,
    ballot_sequence                text      not null,
    disagreements                  text      not null,
    discrepancies                  text      not null,
    end_time                       timestamp,
    expected_audited_prefix_length integer   not null,
    expected_count                 integer   not null,
    number                         integer   not null,
    previous_ballots_audited       integer   not null,
    signatories                    text,
    start_audited_prefix_length    integer   not null,
    start_time                     timestamp not null,
    index                          integer   not null,
    primary key (dashboard_id, index)
);

alter table round
    owner to corlaadmin;

create index idx_uploaded_file_county
    on uploaded_file (county_id);

--
-- Name: hibernate_sequence; Type: SEQUENCE SET; Schema: public; Owner: corlaadmin
--

SELECT pg_catalog.setval('public.hibernate_sequence', 44767, true);

````