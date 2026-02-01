# AI TOOL GUIDANCE

This file provides guidance for AI agents working with code and specifications in this repository.

The specifications in the `Spec` directory are **authoritative**.  
AI agents must follow them strictly and must not invent behavior, data, or requirements.

## Specification Authority

The specification set is the single source of truth for iDispatchX.

In case of conflicts, the following order of precedence applies:

1. Non-Functional Requirements (NFRs)
2. C4 Architectural Specifications
3. Domain Model
4. Use Cases

No implementation may violate a higher-precedence specification.

## Specification Structure

### `Spec/C4/` – Architecture (C4 Model)

* `Spec/C4/Context.md`  
  **Must be read before implementing any feature.**

* `Spec/C4/Containers.md`  
  **Must be read before implementing any feature that crosses a container boundary.**

Refer to `Spec/C4/README.md` for architectural scope and usage rules.

---

### `Spec/NonFunctionalRequirements/` – System-wide Constraints

* All Non-Functional Requirements are **authoritative**.
* **All NFRs must be read and respected before implementing any feature.**
* NFRs override use cases, domain concepts, and implementation convenience.

Refer to `Spec/NonFunctionalRequirements/README.md` for authority and scope.

---

### `Spec/Domain/` – Domain Model

* Defines core domain concepts, attributes, invariants, and validation rules.
* Domain concepts describe meaning and constraints, not implementation.
* Degraded-mode semantics and authority boundaries are explicitly defined here.

Refer to `Spec/Domain/README.md` before implementing or modifying domain-related code.

---

### `Spec/UseCases/` – System Behavior

* Use cases describe observable system behavior.
* Use cases are grouped by **primary actor**.
* **Only read and implement the specific use case you are working on.**
* Do not infer behavior from other use cases.

Use cases must:
* Be consistent with C4 specifications
* Respect all applicable NFRs
* Reference relevant NFRs when behavior is constrained

Refer to `Spec/UseCases/README.md` for usage rules.

---

### `Implementation/` – Container Implementations

The `Implementation/` directory contains the actual code for all iDispatchX containers.

Refer to `Implementation/README.md` for the complete directory structure, namespaces, tech stacks, and build instructions.

#### Key Subdirectories

| Directory        | Contents                                         |
|------------------|--------------------------------------------------|
| `servers/`       | Backend services (CAD Server, GIS Server)        |
| `clients/`       | Frontend applications (Dispatcher, Admin, Mobile, Station Alert) |
| `tools/`         | CLI utilities (GIS Data Importer)                |
| `shared/`        | Shared libraries (Java Common)                   |
| `deploy/`        | Deployment configurations (Docker, Kubernetes)   |

#### Implementation Rules for AI Agents

* **Specifications are authoritative.** All implementation code must conform to the specifications in `Spec/`.
* Before implementing a feature:
  1. Read the relevant NFRs
  2. Read the relevant C4 specifications
  3. Read the relevant domain concepts
  4. Read the specific use case being implemented
* Follow the namespace conventions defined in `Implementation/README.md`.
* Use the tech stack specified for each container; do not introduce alternative frameworks or libraries without an ADR.
* Build commands are documented in `Implementation/README.md`.

## Maintaining Specification Indexes

Each specification directory has a README with a file index table. When you add, rename, or remove a specification file:

* Update the corresponding README's file index table
* Keep entries alphabetically sorted within each section
* Include a brief description (one line) for each file

## General Rules for AI Agents

* **Always ignore `NOTES-TO-SELF.md`.** This file contains informal notes for human developers and is not relevant to AI agents.
* Specifications are authoritative. Do not invent functionality, behavior, or requirements.
* Do not infer missing behavior, data, or rules.
* If behavior is unclear or unspecified, ask for clarification instead of making assumptions.
* Prefer the simplest implementation that does not violate:
  * Non-Functional Requirements
  * C4 architecture boundaries
  * Domain invariants
* Degraded modes described in the NFRs are intentional and must not be “fixed” by adding hidden dependencies, automation, or shortcuts.
* Do not increase precision or completeness of data beyond what is explicitly provided.

## Git and Repository Constraints

* You are sandboxed without direct access to the remote repository.
* Never attempt to push, pull, or modify Git configuration.
* Assume the current branch is up to date with the origin.
