# Domain Concept: Incident Type

An Incident Type classifies the **nature and nominal scale** of an incident.

Incident Types are used to support:

* categorization and reporting
* dispatcher decision-making
* predefined response guidelines

Incident Types do not represent actions taken, units dispatched, or outcomes achieved.

## Attributes

* `code` (required)
  * Type: Alphanumeric string
* `description` (required)
  * Type: [MultilingualName](MultilingualName.md)

## Invariants

* `code` is authoritative and must be used for identification, referencing, and filtering
* `code` must be unique within the Incident Type reference set
* `code` must consist only of characters `0–9`, `a–z`, and `A–Z`
* `code` must not exceed 1000 characters

## Semantics

Incident Types describe **classification** only.

* An Incident Type does not imply that a specific response has occurred
* An Incident Type does not imply that any particular units must be dispatched
* Actual response decisions are always made explicitly by the dispatcher

In future versions, Incident Types may be used as input to **optional response suggestion mechanisms**, such as proposing suitable units based on predefined guidelines.

Such suggestions:

* must be non-authoritative
* must require explicit dispatcher acceptance
* must not result in automatic dispatch
* must not infer or invent actions in the absence of dispatcher input

## Authority and Management

Incident Types are **authoritative reference data**.

* They are loaded from a local reference file at system startup
* They are always available during normal and degraded operation

Authorized administrators may edit Incident Types using the Admin Client, subject to constraints defined elsewhere.

When reference data is modified:

* the underlying reference file is updated
* changes are reloaded dynamically
* existing incidents retain their previously assigned Incident Type codes

Manual creation or modification of Incident Types by dispatchers during incident handling is not permitted.

## Scope

Incident Types are valid only within the Finnish operational context.

## Relevant Non-Functional Requirements

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md)  
Multilingual descriptions must be preserved as provided and must not be inferred or translated automatically.

## Notes

Incident Type intentionally does not encode:

* required units
* staffing levels
* command structure
* dispatch rules

These concerns belong to operational planning and decision-support modules, not the core domain model.
