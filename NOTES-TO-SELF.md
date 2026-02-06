# NOTES-TO-SELF (written by humans, not AI)

# 2026-02-06

* Still haven't checked the code from 2026-02-03
* Did a first UX spec for the Dispatcher Client. It is pretty good, but the location selector must be smarter. The dispatcher doesn't necessarily know what type of address they are getting until after the caller has told them. Therefore you can't start by selecting a type; the type selection must be automatic and contextual.
  * [ ] Iterate this design with Claude
* Asked Claude to generate HTML mockups based on the UX spec, and they look quite nice!!!
  * Although some details need tweaking, and the background colors of the footer and header have been mixed up. Although I kind of like it.

# 2026-02-05

* Continued with small value objects. Even with them you have to be careful about nitty-gritty details.

# 2026-02-04

* Updated design and implementation with snapshot support.
* Address items from yesterday before proceeding with new stuff
* [ ] Consider adding ArchUnit tests (and an ADR about this).
* [ ] Before implementing the first use case, have Claude write a use case execution plan that splits it up into steps. Iterate it until you're happy with it. Then use it to actually implement the thing, one step at a time. Regularly have Claude check whether there are missing pieces of a use case, but never let it fill in the gaps itself. Update the execution plan instead.

# 2026-02-03

* Started to work on the technical design of CAD Server. Did not do a review of use cases and domain concepts yet.
  * [x] Callsign must be defined
* Claude did an initial design of the domain core of CAD Server, but forgot to include WAL snapshots
  * [x] Proceed with adding support for snapshots to the design and implementation
  * [ ] Review the code carefully, including the tests. Make sure you understand it fully yourself before proceeding.
* [x] Consider adding nullability annotations (and an ADR about this).

# 2026-02-02

* Most important Dispatcher use cases should now be ready.
* Additional domain concepts and ADRs also added
* Next:
  * [ ] Ask Claude to do a review of the use cases and domain concepts and look for missing things
  * [x] If nothing big shows up, proceed with technical design of CAD Server

# 2026-02-01

* All containers now build
* Installed Cross manually. Need to write a script that installs all the cross-compilation tools automatically when the devcontainer is created.
  * Installed `aarch64-unknown-linux-gnu` manually as well.
  * Cross compilation fails because of a missing SSL library. I remember seeing something about this somewhere, it's probably a common problem. 
  * [ ] Let Claude handle it when we actually get started on the station alert client.
* Updated all dependencies to their latest versions.
  * Checked Maven dependency versions manually. Not sure which VSCode plugin to install yet.
* Next:
  * [ ] cSpell does not need to check configuration files and installation scripts.
  * [x] Proceed with the use case specifications

# 2026-01-31

* Wrote specifications the entire day
* Generated the first source code
* Coming up next:
  * [x] Continue iterating with Claude until it can build all containers successfully
  * [ ] Install a suitable VS Code plugin for checking Maven dependencies (Dependi is already installed)
  * [x] Go through all current dependencies and make sure they are the latest ones
  * [x] Commit

