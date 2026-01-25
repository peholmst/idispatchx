# Domain Concept: Municipality

A Municipality represents a municipality in Finland. Municipalities are typically selected from a reference lookup table, but if that reference data is unavailable, it must be possible to enter them manually. 

The official list of municipalities is typically updated once a year. The current one can be found from https://koodistot.suomi.fi/.

## Attributes

* `code`
  * Type: Fixed-length numeric string (3 digits)
* `name`
  * Type: [MultilingualName](MultilingualName.md)


## Invariants

* A Municipality must represent a municipality within Finland.
* When selected from a reference lookup table, both `code` and `name` are required.
* When manually entered because the system is running in degraded mode, only `name` is required. Codes are *never* entered manually.
* Manually entered municipalities are considered non-authoritative and may not correspond to official reference data.
* Manually entered municipalities are intended for operational use during degraded modes and are not required to be reconciled with official reference data.
* `code` is authoritative and should be used for references and filtering whenever available.


## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - system usage is geographically limited to Finland
* [NFR: Availability](../NonFunctionalRequirements/Availability.md) - system should remain functional even when geospatial services are unavailable
