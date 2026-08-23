# X-ray Investigation

This context supports staff-led review of suspicious mining patterns recorded in an authoritative block-history source. It helps investigators find and inspect unusual activity; it does not determine whether a player cheated.

## Language

**X-ray investigation**:
A staff review of mining history for patterns that may warrant closer inspection.
_Avoid_: Cheat detection, X-ray conviction, proof of cheating

**Mining history**:
The authoritative record of tracked block activity used by an X-ray investigation. CoreProtect currently supplies this record.
_Avoid_: XRayHunter database, audit cache

**Lookup**:
A bounded query of mining history for a time window and optional world scope.
_Avoid_: Detection run

**Lookup summary**:
The ranked player and material totals produced by a lookup.
_Avoid_: Verdict, conviction list

**Investigation session**:
Transient, staff-member-scoped state from the most recent lookup, including its selected player and loaded vein details.
_Avoid_: Hunt, case file, player record

**Tracked block event**:
A recorded break or placement involving a configured material that is eligible for an investigation lookup.
_Avoid_: Violation, cheat event

**Vein**:
A spatial group of related tracked block events representing one mined occurrence for detail review.
_Avoid_: Finding, offense
