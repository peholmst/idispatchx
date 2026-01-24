# NFR: Performance

This document specifies the performance requirements for iDispatchX. 

In this document, “within seconds” refers to low single-digit seconds, not tens of seconds or longer.

iDispatchX does not contain *any* features with real-time requirements.


## User Interfaces

* User interfaces must respond to user input within 100-200 ms.
  * The operations triggered by user input can take longer, but the UI should clearly indicate when something is in progress.
  * The user interfaces must remain responsive even when in degraded modes and during failover.


## Dispatcher Client

* Changes made by one dispatcher must show up on the other dispatchers' screens within seconds to maintain a shared operational picture.


## Alerts

* Alerts must be delivered to the Station Alert Clients and Mobile Unit Clients within seconds.
  * It is assumed that Station Alert Clients are behind faster and more reliable network connections than Mobile Unit Clients and should thus receive alerts faster.


## Mobile Unit Client

* Unit status updates must be delivered to CAD Server within seconds.
  * The server timestamps may be used afterwards to determine whether the unit met the legislated minimum response times.
* Unit location updates can be sent every 10-20 seconds.


## CAD Server

* No state can be updated before the event changing the state has successfully been written to the WAL and synced. This process must therefore be as fast as possible.


## GIS Server

* Raster tiles must be returned in less than a second during normal load.
* Geocoding requests should return within seconds during normal load.
