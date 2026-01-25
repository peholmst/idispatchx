# Domain Concept: Call

A call is a record of a call that has been received and processed by the emergency dispatch center.


## Attributes

* `id`
  * Type: Nano ID
* `state`
  * Type: `active` | `ended`
* `receiving_dispatcher`
  * Type: User ID
* `call_started`
  * Type: Timestamp (UTC)
* `call_ended`
  * Type: Timestamp (UTC)
* `caller_name`
  * Type: Text
* `caller_phone_number`
  * Type: Text
* `location`
  * Type: [Location](Location.md)
* `description`
  * Type: Text
* `outcome`
  * Type: `incident_created` | `attached_to_incident` | `caller_advised` | `hoax` | `accidental` | `other_no_actions_taken`
* `outcome_rationale`
  * Text
* `incident_id`
  * Type: Reference to [Incident](Incident.md)  


## Invariants

Always required:

* `id`
* `state`
* `receiving_dispatcher`
* `call_started`

Conditionally required:

* `call_ended` when `state = ended`
* `outcome` when `state = ended`
* `outcome_rationale` when `outcome = caller_advised | hoax | accidental | other_no_actions_taken`
* `incident_id` when `outcome = incident_created | attached_to_incident`
  * Note: `incident_id` may be assigned before the call is ended

Optional:

* `location` may be missing if the call location cannot be determined or the dispatcher decides it is not needed (e.g., hoax or accidental call)
* `caller_name` and `caller_phone_number` may be missing if they cannot be determined
* `description` may be missing if the dispatcher decides it is not needed


## Validation Rules

* `caller_phone_number` must be stored as an E.164 number
  * Only digits 0-9 allowed
  * Maximum length 15 digits
  * International numbers start with a plus sign (+)
  * Numbers without a plus sign are assumed to be domestic
* `caller_name` has a maximum length of 100 characters
* `description` has a maximum length of 1000 characters
* `outcome_rationale` has a maximum length of 1000 characters


## Lifecycle

A call progresses through the following states:

* `active`
* `ended`

State transitions:

* `active` → `ended`


## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - timestamps must be stored in UTC
