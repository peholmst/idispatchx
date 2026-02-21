# Technical Designs

This directory contains technical design documents for iDispatchX components.

Technical designs describe **how** a component will be implemented at a detailed level, bridging the gap between high-level specifications (C4, Domain, Use Cases) and actual code.

## Purpose

Technical designs:

* Provide detailed implementation guidance for specific containers or subsystems
* Define package structures, class hierarchies, and key interfaces
* Document threading models, synchronization strategies, and data flow
* Capture design patterns and their application
* Serve as reference during implementation and code review

## Relationship to Other Specifications

Technical designs are **subordinate** to:

1. Non-Functional Requirements (must comply with all NFRs)
2. C4 Architectural Specifications (must respect container boundaries)
3. Domain Model (must implement domain concepts correctly)
4. Use Cases (must support required behavior)

Technical designs may propose implementation approaches, but cannot contradict higher-level specifications.

## When to Create a Technical Design

Create a technical design when:

* Implementing a new container or major subsystem
* The implementation approach is non-obvious and warrants documentation
* Multiple developers will work on the component
* The design involves significant architectural decisions (threading, persistence, etc.)

## Document Structure

Each technical design should include:

1. **Overview** - Purpose and scope
2. **References** - Links to relevant specifications
3. **Package/Module Structure** - How code is organized
4. **Key Interfaces and Classes** - Core abstractions
5. **Data Flow** - How data moves through the system
6. **Threading/Concurrency Model** - If applicable
7. **Verification Strategy** - How to test the design

## File Index

| File | Description |
|------|-------------|
| [CAD-Server-Domain-Core.md](CAD-Server-Domain-Core.md) | Domain core design for CAD Server with in-memory state, thread-safety, and WAL integration |
| [GIS-Data-Import-and-Schema.md](GIS-Data-Import-and-Schema.md) | PostGIS database schema and NLS GML data importer design for GIS Server geocoding |
| [GIS-Server-REST-API.md](GIS-Server-REST-API.md) | REST API design for GIS Server WMTS tiles and geocoding endpoints |
