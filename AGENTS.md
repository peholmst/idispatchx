# Agent Guidance

Specifications in `Spec/` are authoritative. Agents must not invent behavior, data, or requirements.

This repository uses active human-in-the-loop development. Surface uncertainty early, ask when
specifications leave meaningful choices open, and avoid hidden automation or tooling that replaces
developer judgment.

## Authority

Precedence:

1. Non-Functional Requirements
2. C4 Architectural Specifications
3. Domain Model
4. Use Cases

No implementation may violate a higher-precedence specification.

## Before Implementing

Read only the sources relevant to the task:

* Applicable NFRs in `Spec/NonFunctionalRequirements/`.
* `Spec/C4/Context.md` before implementing features.
* `Spec/C4/Containers.md` when crossing container boundaries.
* Relevant domain concepts in `Spec/Domain/`.
* Only the specific use case being implemented from `Spec/UseCases/`.
* Relevant technical designs in `Spec/TechnicalDesigns/` for containers or major subsystems.
* `Implementation/README.md` for structure, namespaces, tech stacks, and build commands.

Directory README files define local usage rules and indexes.

## Implementation Rules

* Keep changes focused, small, and reviewable.
* Follow existing architecture, namespaces, and tech stacks.
* Do not add frameworks, services, generated assets, or automation unless specified by specs, ADRs, or the developer.
* Do not infer missing behavior from unrelated use cases.
* Preserve degraded-mode semantics from the NFRs.
* Do not increase data precision or completeness beyond what is specified.
* Make assumptions visible in notes, commits, or review comments.
* Leave Git authentication, remotes, and credentials to the developer or execution environment.

## Specification Indexes

When adding, renaming, or removing a specification file, update the corresponding README index, keep
entries alphabetically sorted, and include a one-line description.
