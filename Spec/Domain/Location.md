# Domain Concept: Location

A Location describes where a call, incident, unit, or station is located. 

Locations may vary in precision and representation depending on the information available. The description should preferably be detailed enough for a person with local knowledge to find it, with the help of ordinary maps and consumer-level GPS navigators.


## Location Variants

A Location is exactly one of the following variants:

### ExactAddress

An exact street address or address point. An address point is a named official address without an actual road, for instance on a small but permanently inhabited island. Address points can have numbers.

* `municipality` (required)
  * Type: Embedded [Municipality](Municipality.md)
* `address_name` (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `address_number` (optional)
  * Type: Text
* `coordinates` (optional)
  * Type: Decimal degrees (EPSG:4326)
* `additional_details` (optional)
  * Type: Text

### RoadIntersection

An intersection between two named roads.

* `municipality` (required)
  * Type: Embedded [Municipality](Municipality.md)
* `road_name_a` (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `road_name_b` (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `coordinates` (optional)
  * Type: Decimal degrees (EPSG:4326)
* `additional_details` (optional)
  * Type: Text

### NamedPlace

A named geographical place such as an island, village, or landmark.

* `municipality` (required)
  * Type: Embedded [Municipality](Municipality.md)
* `name` (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `coordinates` (optional)
  * Type: Decimal degrees (EPSG:4326)
* `additional_details` (optional)
  * Type: Text

### RelativeLocation

An approximate location described relative to a known reference.

* `municipality` (required)
  * Type: Embedded [Municipality](Municipality.md)
* `reference_place` (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `coordinates` (optional)
  * Type: Decimal degrees (EPSG:4326)
  * Approximate coordinates derived from the relative description
* `additional_details` (required)
  * Type: Text


## Invariants

* A Location must be exactly one variant.
* A Location variant must not contain attributes from other variants.
* Different variants have different required attributes, which must be enforced.
  * For RelativeLocation, `additional_details` is required to describe the relative positioning.
* If a required attribute is missing from any variant, the Location is considered undeterminable.
* The system must not add or infer coordinates or precision beyond what is explicitly provided by the dispatcher.


## Validation Rules

* `address_number` has a maximum length of 30 characters.
* `additional_details` has a maximum length of 1000 characters.
* `coordinates` must conform to [Coordinate Precision and Bounds](../NonFunctionalRequirements/Internationalization.md#coordinate-precision-and-bounds):
  * Maximum 6 decimal places
  * Latitude: 58.84° to 70.09°
  * Longitude: 19.08° to 31.59°


## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - coordinates must be stored and processed in EPSG:4326, with defined precision and bounds
