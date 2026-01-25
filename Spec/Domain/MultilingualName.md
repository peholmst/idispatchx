# Domain Concept: Multilingual Name

A multilingual name is a formal name (e.g., of a place or legal entity) that can have multiple language versions. 

In Finland, the names are typically in Finnish, Swedish, or both. In Lapland, Sami languages are also used.


## Attributes

A Multilingual Name consists of zero or more (`language_code`, `value`) pairs.

Example:

- `("fi", "Helsinki")`
- `("sv", "Helsingfors")`


## Invariants

* A Multilingual Name must not contain more than one value for the same language code.
* ISO 639 language codes must be used (ISO 639-1 where available, otherwise ISO 639-2 or 639-3).
* Language versions must be stored in UTF-8.
* When retrieved from a reference lookup table, all language versions must be included and preserved.
* When manually entered, only one version is needed and the language is *unspecified*.
  * When the language of a manually entered name is unspecified, the system must not assume or infer a language.
* An empty Multilingual Name represents an unknown or unspecified name.
  * The absence of a name must be represented as an empty Multilingual Name, not as null.
  * The system must not invent or infer name values to populate an empty Multilingual Name.


## Validation Rules

* Maximum length of a language version is 200 characters.


## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - system usage is geographically limited to Finland
* [NFR: Availability](../NonFunctionalRequirements/Availability.md) - system should remain functional even when geospatial services are unavailable
