# iDispatchX System Context Specification

This document describes the system context of iDispatchX: what it is, who uses it and which external entities it interacts with.

A C4 context diagram is provided in: [Context.puml](Context.puml)


## What is iDispatchX?

iDispatchX is a Computer-Aided Dispatch (CAD) system for emergency services in Finland, intended for use by emergency dispatch centers and emergency service organizations whose units they dispatch.

It is specified and implemented as if it was to be used in the real world, but in practice *this is a learning project*. Its primary purpose is to explore design and implementation patterns that could be used in a real-world CAD system.

### Out of Scope

iDispatchX does not have any VoIP features. Calls are received outside the system and call details are entered manually into the system.

iDispatchX does not integrate with any radio networks like TETRA.

iDispatchX does not include command and control or incident reporting functionality. Integration with separate systems may be explored in the future.


## Actors

iDispatchX is used by the following actors:

* *Dispatchers* use the system to enter information about calls and incidents, geocode addresses, dispatch units and track their status and location on a map.
* *Units* (field personnel or vehicle-mounted systems) use the system to receive alerts and other messages, and update their status and location.
* *Stations* (building-mounted systems) use the system to receive alerts and notify the personnel in quarters.
* *Administrators* use the system to manage users, stations, units, unit types, and incident types. They also use the system to update the map and address information databases.

Detailed specifications are provided in the use cases (separate documents).

*Note:* Citizens and callers do not interact with iDispatchX directly.


## External Entities

iDispatchX uses the following external data sources:

* Map rasters and address information from the National Land Survey of Finland (NLS). The information needs to be downloaded manually from the NLS web site and imported into iDispatchX. It is used as a read-only data source at runtime.

iDispatchX integrates with the following external systems:

* An SMTP server for sending alert notifications by email to predefined recipients.
* An SMS gateway for sending alert notifications by SMS to predefined recipients.
* An OIDC provider for authenticating dispatchers, units, and administrators. Authorization is handled internally.


## Trust Boundaries

iDispatchX is the system of record for calls, incidents, and unit status. External systems are treated as supporting or notification services and do not maintain authoritative state.
