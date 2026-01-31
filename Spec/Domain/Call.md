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
  * Type: Embedded [Location](Location.md)
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


## Semantics

### Outcome Mutability

The `outcome` attribute may be set and changed while a call is in state `active`. It must be set before the call can transition to `ended`.

Once the call transitions to `ended`, the `outcome` becomes immutable.

If an incident was created based on a call and the call is later determined to be a hoax or accidental, the dispatcher may change the `outcome` to `hoax` or `accidental` while the call is still active. The `incident_id` remains set, preserving the link to the created incident. The dispatcher must take manual action to end the created incident (recalling dispatched units, etc.).

Changing the `outcome` does not automatically affect any linked incident.

### Incident Linkage Mutability

A call may be linked to an incident by setting `incident_id`. This occurs when:
* A new incident is created in response to the call (`outcome = incident_created`)
* The call is attached to an existing incident (`outcome = attached_to_incident`)

Once a call transitions to `ended`, the `incident_id` becomes immutable.

#### Detaching a Call

A call may be detached from an incident while the call is in state `active`, but only if the call was attached to an existing incident (`outcome = attached_to_incident`).

If the incident was created in response to the call (`outcome = incident_created`), the call cannot be detached.

When a call is detached:
* `incident_id` is cleared
* `outcome` is cleared
* An automatic [`IncidentLogEntry`](Incident.md) is created on the incident

The dispatcher may then set a new outcome before ending the call.


## Lifecycle

A call progresses through the following states:

* `active`
* `ended`

State transitions:

* `active` → `ended`


## Archival

Calls linked to an incident are archived together with the incident. See [Incident Archival](Incident.md#archival).

Calls not linked to any incident (e.g., `outcome = caller_advised | hoax | accidental | other_no_actions_taken`) are archived independently based on retention policies outside the scope of the domain model.


## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - timestamps must be stored in UTC
