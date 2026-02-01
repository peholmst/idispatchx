# NOTES-TO-SELF (written by humans, not AI)

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
  * [ ] Proceed with the use case specifications

# 2026-01-31

* Wrote specifications the entire day
* Generated the first source code
* Coming up next:
  * [x] Continue iterating with Claude until it can build all containers successfully
  * [ ] Install a suitable VS Code plugin for checking Maven dependencies (Dependi is already installed)
  * [x] Go through all current dependencies and make sure they are the latest ones
  * [x] Commit

