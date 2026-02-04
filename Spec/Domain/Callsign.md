# Domain Concept: Callsign

A Callsign is a value object representing a Finnish rescue services unit identification code according to Ministry of the Interior guidelines (SM 2021:35).

Callsigns are used for unit identification in emergency response, communications, situation awareness, and statistical reporting.

---

## Attributes

* `value` (required)
  * Type: String
  * A syntactically valid callsign string

---

## Invariants

* A Callsign must be immutable once created
* Two Callsigns are equal if their string representations are identical (case-sensitive)
* The canonical string representation uses uppercase letters only
* A Callsign must be stored and compared in its normalized (uppercase) form
* Changes to administrative boundaries (emergency response center areas, municipalities) do not affect existing callsigns

---

## Validation Rules

* Length: 3–16 characters
* First character must be a valid sector code (uppercase letter from the defined set)
* Organization code (characters 2–3 or 2–4):
  * Two uppercase letters (standard rescue departments, special codes)
  * Two digits (some special organizations)
  * One to three digits (municipalities with K prefix only)
* Underscore, if present:
  * Must appear after position 3 (or after position 4 for municipalities)
  * Only one underscore is allowed
  * Characters after underscore must be alphanumeric uppercase only
* For Rescue units (sector R), non-virtual callsigns:
  * Positions 4–5: Digits 0–9 (station number, may be omitted for leadership units)
  * Position 6: Digit 0–9 (property code)
  * Position 7: Optional digit or uppercase letter
  * Position 8+: Optional uppercase letters only
* For Industrial units (IR/IE): First digit of station number must be 1–6 (or 7–9 if reserved numbers activated)
* Permitted character set: Uppercase letters A–Z, digits 0–9, underscore (in designated position only)

---

## Syntax Reference

The following sections document the syntax of callsigns for reference. The Callsign value object does not parse or decompose the string into these components.

### Sector Codes

* `R` (Rescue), `P` (Police), `E` (EMS), `B` (Border Guard), `S` (Social Services), `M` (Military), `C` (Customs), `V` (Volunteers), `A` (Aviation), `I` (Industrial), `K` (Municipality)

### Special Organization Codes

* **Aviation**: `AR` (Aviation rescue), `AE` (Aviation emergency medical)
* **Industrial**: `IR` (Industrial rescue), `IE` (Industrial emergency medical)
* **Military**: `MR` (Army/Navy/Logistics rescue), `MRA` (Air Force rescue), `ME` (Military emergency medical), `MEA` (Air Force emergency medical)
* **Railway**: `RR` (Finnish Transport Infrastructure Agency)

### Format Patterns

Note: These patterns apply to Rescue units only unless otherwise specified. Other sectors have their own patterns and meanings.

#### Standard Unit Format

```
[Sector][Organization][Station][Property][Sequence?][Modifier?]
```

Example: `REP101`

#### Leadership Unit Format

```
[Sector][Organization][Level][Area]
```

* Levels: `1` (Federation director), `2` (Duty chief), `3–4` (Duty fire chief), `5` (Duty group leader)
* Areas: `0` (Command center), `1–9` (Field operations)

Example: `REPP21`

#### Industrial Format (IR/IE)

```
[I][R or E][EmergencyArea][FacilityNumber][Property][Sequence?][Modifier?]
```

* Emergency response center areas: `1` (Kerava), `2` (Turku), `3` (Pori), `4` (Vaasa), `5` (Oulu), `6` (Kuopio), `7–9` (reserved)

Example: `IR1234`

#### Municipal Format (K prefix)

```
K[MunicipalityCode][Station][Property][Sequence?][Modifier?]
```

Example: `K5101`

#### Virtual Unit / Alert Group Format

```
[Sector][Organization]_[Station?][Purpose][Detail?]
```

Purpose codes include: `JOH`, `JVT`, `LSP`, `PEL`, `SR`, `SUK`, `SURO`, `TUTKI`, `VA`, `VSS`, `VV`, `ÖT`

Example: `REP_10VV`

#### Command/Situation Center Format

* Command center: `[Sector][Organization]_JOKE`
* Situation center: `[Sector][Organization]_TIKE`

#### Foreign Unit Format

```
[Sector][CountryCode][HomeUnitIdentifier]
```

Examples: `RSE2116310`, `RNOD11`

---

## Semantics

This specification defines **syntactical validation only**. The system does not validate:
* Whether a sector code is appropriate for the organization
* Whether the organization code corresponds to an existing entity
* Whether station numbers are in operationally valid ranges
* Whether property codes match registered equipment types
* Whether virtual unit abbreviations follow standardized conventions
* Whether municipality codes are valid
* Whether foreign country codes are valid
* Whether emergency response center area codes for IR units are correct

---

## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) — system usage is geographically limited to Finland

---

## Reference

Based on: **Ohje pelastustoimen yksikkö- ja kutsutunnuksista**
Publication: Sisäministeriön julkaisuja 2021:35
Publisher: Ministry of the Interior, Finland
Date: 29.9.2021
