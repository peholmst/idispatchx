# AI TOOL GUIDANCE

This file provides guidance when working with code in this repository.

## Specifications

The specifications for this system are located in the `Spec` directory and are authoritative.

### Structure

* `Spec/C4` – system architecture specifications using the C4 model
  * `Spec/C4/Context.md` – system context  
    **Must be read before implementing any feature.**
  * `Spec/C4/Containers.md` – system containers  
    **Must be read before implementing any feature that crosses a container boundary.**

* `Spec/NonFunctionalRequirements` – non-functional requirements  
  **All NFRs must be read and respected before implementing any feature.  
  NFRs override use cases and implementation convenience.**

* `Spec/UseCases` – use case specifications  
  **Only read and implement the specific use case you are working on.  
  Do not infer behavior from other use cases.**

### Rules

* Specifications are authoritative. Do not invent functionality, behavior, or requirements not explicitly described.
* If a use case conflicts with a non-functional requirement, the non-functional requirement takes precedence.
* If an implementation detail is not specified, prefer the simplest solution that does not violate:
  * the system context,
  * container boundaries, or
  * non-functional requirements.
* Degraded modes described in the NFRs are intentional and must not be “fixed” by adding hidden dependencies or shortcuts.
* When in doubt, ask for clarification instead of making assumptions.

## Git

* You are sandboxed without direct access to the remote repository. 
* Never try to push or pull.
* Assume the branch is always up to date with the origin.
