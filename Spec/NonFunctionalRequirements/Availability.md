# NFR: Availability

iDispatchX can run in a high-availability (HA) or a non-HA configuration. This NFR specifies the requirements for the HA configuration.

## Out of Scope

This specification does *not* cover:

* network components and other infrastructure
* high availability storage solutions such as RAID
* high availability configurations of databases
* high availability configurations of the OIDC provider

In other words, it is assumed that:

* the network is robust enough to support continuous operation under normal conditions
* the power infrastructure is robust enough to support continuous operation under normal conditions
* shared hard disk storage used for WAL replication is robust enough
* the OIDC provider is running in a HA configuration
* any critical database server used by iDispatchX is running in a HA configuration


## Minimum Application-level Availability

This section lists requirements that must be met at all times. 

“At all times” refers to all normal operational states of the system, excluding the explicitly listed exceptions and planned downtime.

### Authenticated Clients

* After a user has logged in using any client, the user should only need to re-authenticate:
  * After logging out,
  * After an administrator has ended the session, or
  * The maximum session lifetime has been reached.
* A user of a client must always be notified well in advance of the session reaching its maximum lifetime, to allow uninterrupted operational use.
  * Example: crews logging out and into their Mobile Unit Clients at the start of each shift, and re-starting their Station Alert Clients every Sunday afternoon.

### Dispatchers

* A dispatcher must always be able to use the system to enter call and incident details and dispatch units.
  * Exception: the dispatcher's own workstation or web browser fails.
  * Exception: planned downtime.
* A dispatcher must always know whether the system is running in degraded mode or not.
* Alerts must reach the units in _at least_ one way:
  * Through the Station Alert Client
  * Through the Mobile Unit Client
  * Through an SMS to the unit leader's mobile phone
  * Through an e-mail to the unit leader's mobile phone
  * Over the radio (outside of iDispatchX)

### Units

* A unit must always know whether they are currently logged into Mobile Unit Client or not.
* A unit must always know whether the system is running in degraded mode or not, if it affects the services used by Mobile Unit Client.

### Stations

* A station must always know whether they are currently logged into Station Alert Client or not.
* A station must always know whether the system is running in degraded mode or not, if it affects the services used by Station Alert Client.

### Administrators

* While CAD Server is running, administrators must always be able to terminate sessions of compromised clients.


## Degraded Modes

iDispatchX does not require all services to be available to remain useful. Degraded modes rely on established operational procedures and parallel communication channels outside of iDispatchX.

* Dispatchers must be able to use the system without the GIS Server being available. 
  * Addresses and coordinates can be asked from the caller and entered manually.
  * Units can be selected manually for dispatching.
  * The system must not require coordinates to be included in alerts (units can look up addresses on a map).
* Dispatchers must be able to dispatch units even when their locations are unavailable or outdated in the system.
  * A dispatcher can ask the unit where it is over radio.  
* Dispatchers must be able to set the status of a unit manually if the Mobile Unit Client is unavailable.
  * The unit can report its status over radio.
* If CAD Server is unable to write archived data to the CAD Archive database, it must continue to operate without exhausting memory or disk space.  
* As stated earlier, it is sufficient that a unit receives an alert through at least one channel. This requires crews to know which alert channels to actively monitor.


## Failover Expectations

This section describes how various containers are expected to behave in the case of failure. These expectations describe observable behavior during failures and recovery, not specific implementation mechanisms.

### CAD Server

The CAD Server is expected to be deployed on hardware sized to handle peak load without requiring horizontal scaling.

The CAD Server should have a warm standby capable of taking over using persisted operational state (i.e., the WAL). If the primary CAD Server goes down, the warm standby should take over service. This process should be measured in seconds, not minutes.

Because failover may occur during command processing, all commands sent to the CAD Server must be idempotent.

Once the warm standby has taken over operations, the old primary server must detect it is no longer the primary and halt itself to avoid data corruption. Administrator intervention is required to turn the old primary server into a new warm standby.

### GIS Server

GIS Server is an important, but not critical part of iDispatchX. It also serves read-only data, which means it can be replicated and put behind a load balancer. If one of the nodes goes down, the load balancer should notice this and redirect requests to healthy nodes instead.

### Clients

All clients must have built-in support for automatic reconnections and retries (where applicable), with exponential back-off.

If the client is configured to connect to a server using an IP-address, the deployment environment must ensure that the IP-address remains unchanged after a server failover (e.g., floating IP).

If the client is configured to connect to a server using a DNS-name, the deployment environment must ensure that the DNS-name remains unchanged after a server failover.

#### Station Alert Client

The Station Alert Client must be able to restart itself if it stops working, provided that the rest of the system (RaspberryPI and Linux) is operating normally.
