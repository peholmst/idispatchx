# NFR: Security

iDispatchX deals with very sensitive information (PII and operational data that can be used by hostile powers). Because of this it is crucial that the data is protected against unauthorized access, disclosure, and misuse.

This NFR focuses on application-level security concerns such as authentication, authorization, session handling, and data access control.

## Out of Scope

This specification does *not* cover:

* network security such as firewalls
* operating system security such as users, file system permissions and disk drive encryption

In other words, it is assumed that:

* the network is correctly configured
* the servers have been correctly hardened and patched
* the deployment environment has the necessary infrastructure in place to detect and protect against attacks such as DDoS or gaining unauthorized access to the servers themselves


## Authentication & Identity

* All server APIs in iDispatchX require *strong authentication*. There are no endpoints available to anonymous users.
* All clients are considered untrusted and must be authenticated and authorized for every request.
* All users - dispatchers, administrators, stations, and units - are managed in a centralized identity provider.
* All interactive users must authenticate with the centralized identity provider.
* The authentication mechanism must support browser-based, mobile, and headless clients.
* Clients must not handle or store user credentials directly.
* Revoked or expired credentials must prevent further access.
* Authentication must not rely on client-side secrets for public clients.
* GIS Data Importer does not require authentication. It is executed manually by administrators in a controlled environment with direct access to GIS FS and GIS DB.


## Authorization

* Any user in iDispatchX has one of the following roles:
  * `Dispatcher` - can access Dispatcher Client and its services (including GIS Server)
  * `Observer` - can access Dispatcher Client and its services (including GIS Server) but only for reading
  * `Admin` - can access Admin Client and its services
  * `Station` - can access Station Alert Client and its services
  * `Unit` - can access Mobile Unit Client and its services
* Roles are stored in the centralized identity provider.
* Each server application must ensure the user has the correct role before granting access.


## Session Security

* Administrators must be able to immediately invalidate active sessions.
* Clients must handle forced session termination gracefully.
* Authentication state must not survive server failover unless explicitly intended.
* All clients must have a maximum session lifetime, after which re-authentication is required.
* Dispatcher Client and Admin Client must have idle session timeouts.


## Auditability Expectations

* The system must log *at least* the following events:
  * Client connections
  * Successful authentications
  * Failed authentication attempts
  * Forced session terminations
  * Dispatcher commands
  * Admin commands
* Audit logs must be append-only and protected against modification.


## Data Security

* No application level encryption of data at rest is required by iDispatchX.
* All data must be encrypted in transit.
* No PII must be included in system or audit logs.
* PII can be stored in the CAD WAL, but must be purged once it has been archived.
* PII can be stored in the CAD Archive, after which its protection is the responsibility of the database and deployment environment, not the application.
