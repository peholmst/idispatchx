# Domain Concept: Callsign

A Callsign is a value object representing a Finnish rescue services unit identification code according to Ministry of the Interior guidelines (SM 2021:35).

Callsigns are used for unit identification in emergency response, communications, situation awareness, and statistical reporting.

---

## Attributes

A Callsign is a structured string composed of the following components:

* `sector` (required)
  * Type: Single uppercase letter
  * Represents the authority sector or stakeholder group
  * Valid values: `R` (Rescue), `P` (Police), `E` (EMS), `B` (Border Guard), `S` (Social Services), `M` (Military), `C` (Customs), `V` (Volunteers), `A` (Aviation), `I` (Industrial), `K` (Municipality)

* `organization` (required)
  * Type: Two uppercase letters or digits
  * Identifies the rescue department, police district, hospital district, or organizational unit
  * Examples: `EP` (Etelä-Pohjanmaa), `HE` (Helsinki), `LA` (Lappi), `SM` (Ministry of Interior)

* `station_number` (optional)
  * Type: Two digits (00–99)
  * Identifies the fire station from which the unit is dispatched
  * Omitted for leadership units not tied to a station
  * Special values: `00` (no fixed location), `0X` (administrative vehicles)

* `property_code` (required for non-virtual units)
  * Type: One or two digits
  * Indicates unit type and capabilities
  * Categories: `1X` (Firefighting), `2X` (Combination), `3X` (Agent transport), `4X` (Transport), `5X` (Rescue), `6X` (Aerial/Aircraft), `7X` (Crew/Support), `8X` (Water/Terrain), `9X` (Trailers)

* `sequence_number` (optional)
  * Type: Single digit (2–9)
  * Differentiates multiple units of the same type at the same station
  * First unit of a type omits this number

* `modifier` (optional)
  * Type: Single uppercase letter
  * Additional identifier when numeric sequencing is not possible

---

## Format Patterns

### Standard Unit Format

```
[Sector][Organization][Station][Property][Sequence?][Modifier?]
```

Example: `REP101` (Rescue, Etelä-Pohjanmaa, Station 10, Property 1)

### Leadership Unit Format

```
[Sector][Organization][Level][Area]
```

* Levels: `1` (Federation director), `2` (Duty chief), `3–4` (Duty fire chief), `5` (Duty group leader)
* Areas: `0` (Command center), `1–9` (Field operations)

Example: `REPP21` (Rescue, Etelä-Pohjanmaa, Duty chief, Field area 1)

### Virtual Unit / Alert Group Format

```
[Sector][Organization]_[Station?][Purpose][Detail?]
```

Contains an underscore separator after the organization code.

Purpose codes include: `JOH` (Leadership), `JVT` (Damage control), `PEL` (Rescue), `SR` (Fire brigade), `SUK` (Diving), `SURO` (Major incident), `TUTKI` (Investigation), `VA` (Hazmat), `VSS` (Civil defense), `VV` (Off-duty recall), `ÖT` (Oil response)

Example: `REP_10VV` (Off-duty recall for Etelä-Pohjanmaa, Station 10)

### Command/Situation Center Format

* Command center: `[Sector][Organization]_JOKE`
* Situation center: `[Sector][Organization]_TIKE`

---

## Invariants

* A Callsign must be immutable once created
* Two Callsigns are equal if their string representations are identical (case-sensitive)
* The canonical string representation uses uppercase letters only
* A Callsign must be stored and compared in its normalized (uppercase) form

---

## Validation Rules

* Length: 3–16 characters
* First character must be a valid sector code (uppercase letter from the defined set)
* Second and third characters must be uppercase letters or digits
* Underscore, if present:
  * Must appear after position 3
  * Only one underscore is allowed
  * Characters after underscore must be alphanumeric uppercase only
* For non-virtual callsigns (no underscore):
  * Positions 4–5: Digits 0–9 (station number, may be omitted for leadership units)
  * Position 6: Digit 0–9 (property code)
  * Position 7: Optional digit or uppercase letter
  * Position 8+: Optional uppercase letters only
* Permitted character set: Uppercase letters A–Z, digits 0–9, underscore (in designated position only)

---

## Semantics

### Syntactical vs Semantic Validation

This specification defines **syntactical validation only**. The system does not validate:
* Whether a sector code is appropriate for the organization
* Whether the organization code corresponds to an existing entity
* Whether station numbers are in operationally valid ranges
* Whether property codes match registered equipment types
* Whether virtual unit abbreviations follow standardized conventions

### Voice Communication

Separate formatting rules apply for voice communication of callsigns. These rules are outside the scope of this value object.

### Foreign Units

Foreign unit callsigns follow the same pattern, using two-letter country codes as the organization component.

---

## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) — system usage is geographically limited to Finland

---

## Reference

Based on: **Ohje pelastustoimen yksikkö- ja kutsutunnuksista**
Publication: Sisäministeriön julkaisuja 2021:35
Publisher: Ministry of the Interior, Finland
Date: 29.9.2021
