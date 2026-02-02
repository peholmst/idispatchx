# Domain Concept: Alert Target

An Alert Target represents a **delivery channel** through which dispatch alerts can be sent to units.

Alert Targets are used to:

* deliver dispatch alerts to units when they are dispatched to an incident
* track acknowledgment of alert delivery (triggering the `dispatching` → `dispatched` transition)
* provide redundancy through multiple delivery channels per unit

Alert Targets do not represent units themselves, only the channels through which units receive alerts.

## Attributes

* `id` (required)
  * Type: Nano ID
  * Internal identifier used within CAD Server

* `name` (required)
  * Type: String
  * Human-readable name used by administrators
  * Must be unique across all Alert Targets

* `state` (required)
  * Type: `active` | `inactive`

* `target_type` (required)
  * Type: `station_alert_client` | `mobile_unit_client` | `email` | `sms`

* `configuration` (required)
  * Type: Varies by `target_type` (see [Target Type Configuration](#target-type-configuration))

* `units` (required)
  * Type: Set of references to [Unit](Unit.md)
  * May be empty

## Target Type Configuration

Each `target_type` has specific configuration requirements:

### `station_alert_client`

* `client_id` (required)
  * Type: Nano ID
  * Identifier of the specific Station Alert Client instance

### `mobile_unit_client`

* `client_id` (required)
  * Type: Nano ID
  * Identifier of the specific Mobile Unit Client instance

### `email`

* `email_addresses` (required)
  * Type: List of email addresses
  * Must contain at least one valid email address

### `sms`

* `phone_numbers` (required)
  * Type: List of phone numbers
  * Must contain at least one valid phone number
  * Phone numbers must be in E.164 format

## Invariants

* `id` must be unique within the system
* `name` must be unique across all Alert Targets
* `state` must always be set
* `configuration` must be valid for the specified `target_type`
* Alert Targets in state `inactive` must not be used for alert delivery
* An Alert Target may be associated with zero or more Units
* A Unit may be associated with zero or more Alert Targets

## Semantics

### Alert Delivery

When a unit is dispatched:

1. The system identifies all Alert Targets associated with the unit
2. Alerts are sent only to Alert Targets in state `active`
3. When the first Alert Target acknowledges delivery, the unit transitions from `dispatching` to `dispatched`

If a unit has no active Alert Targets, the system cannot dispatch the unit automatically. The dispatcher must use Manual Dispatch Confirmation after contacting the unit through other means (e.g., radio).

### Acknowledgment Semantics

Alert Target acknowledgment indicates **technical delivery** of the alert, not human acknowledgment.

* For `station_alert_client`: the Station Alert Client has received and displayed the alert
* For `mobile_unit_client`: the Mobile Unit Client has received and displayed the alert
* For `email`: the email has been accepted by the mail server
* For `sms`: the SMS has been accepted by the SMS gateway

Acknowledgment does not confirm that the crew has noticed, read, or acted upon the alert. Crew acknowledgment is a separate operational concern and is outside the scope of this domain concept.

### Dispatch Eligibility

A unit without any active Alert Targets:

* Cannot be dispatched by the system (dispatch command will fail or require manual confirmation)
* Can still be assigned to incidents
* Can still be manually confirmed as dispatched by a dispatcher

This ensures that units are not left in `dispatching` state indefinitely when no acknowledgment is possible.

### Association with Units

The association between Alert Targets and Units is managed by administrators.

* A single Alert Target may serve multiple units (e.g., a station alert client serving all units at a station)
* A single unit may have multiple Alert Targets (e.g., mobile client, SMS to unit leader, station alert)
* Removing a unit from an Alert Target does not affect historical dispatch records

## Authority and Management

Alert Targets are authoritative reference data.

* Alert Targets are managed by authorized administrators using the Admin Client
* Alert Target definitions are stored in a local reference file
* Reference data is loaded at system startup and reloaded dynamically when modified

Manual creation or modification of Alert Targets by dispatchers during incident handling is not permitted.

## Lifecycle

Alert Targets have a simple, reversible lifecycle:

* `active`
  The Alert Target is operational and will be used for alert delivery

* `inactive`
  The Alert Target is not operational and must not be used for alert delivery
  Historical references and delivery records remain valid

Alert Targets may transition freely between `active` and `inactive` states as administrative decisions change.

Deactivating an Alert Target does not affect units currently in `dispatching` state; they will either receive acknowledgment from other active Alert Targets or time out.

## Scope

Alert Targets are valid only within the Finnish operational context.

## Relevant Non-Functional Requirements

* [NFR: Availability](../NonFunctionalRequirements/Availability.md)
  * Alerts must reach units through at least one channel
  * Degraded operation when some alert channels are unavailable

* [NFR: Performance](../NonFunctionalRequirements/Performance.md)
  * Alerts must be delivered to Station Alert Clients and Mobile Unit Clients within seconds

* [NFR: Security](../NonFunctionalRequirements/Security.md)
  * Alert Target configuration (email addresses, phone numbers) may contain PII and must be protected accordingly

## Notes

Alert Target intentionally does not model:

* alert content or formatting
* delivery retry logic
* acknowledgment protocol details
* escalation rules

These concerns belong to implementation and operational configuration.
