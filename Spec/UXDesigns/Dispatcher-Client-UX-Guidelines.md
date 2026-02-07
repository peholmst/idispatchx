# Dispatcher Client UX Guidelines

This document is an advisory document for the visual design of Dispatcher Client. It does not contain any technical details about how Dispatcher Client should be implemented. It does not override any other specification documents. It will be expanded as new use cases emerge.


## General Look and Feel

* The look and feel of Dispatcher Client is similar to that of the dark mode of Visual Studio Code.
* When a user logs in, they end up at a launcher page.
* From the launcher page, they can open two windows; one for the primary monitor and one for the secondary monitor.
* Within the same browser session, you can only have one instance of each window (launcher, primary, secondary) open at any given time.
* The launcher also has actions for logging out and going to the OIDC account page.
* Dispatcher windows (primary and secondary) have a header (brighter color) and a footer (dark mode).
  * The header contains the logo of the application and the current date and time (left corner), primary actions (center) and customization actions (right).
  * The footer contains the name of the current user (left corner) and normal/degraded mode (right).
    * In the future, the footer might contain other info or actions.
* Error messages that prevent operation (like lost connection, logged out, session expired) show up as a full-screen dialog or popover.
* User recoverable error messages (like validation errors) show up in banners or next to the widget where the error can be fixed. No dialogs or "toasts".
  * They either disappear when the error is resolved, or when the user closes them (depending on the type of error).
* Intermediate status messages (e.g. disconnected -> connecting -> connected) are used where appropriate.
* The application can be used with a keyboard only for power users who don't want to reach for the mouse.

### Language

* The user interface is available in Finnish, Swedish, and English.
* An application restart or session reload is acceptable when changing the language of the UI.
* If a translation is missing, English is used as a fallback language.

### Date and Time

* All dates and times are displayed in the `Europe/Helsinki` timezone using a 24-hour clock.
* The current date and time shown in the header updates continuously.

### Coordinate Display

* The default coordinate display format is DDM (Degrees Decimal Minutes).
* Dispatchers can switch the display format between DD, DDM, and DMS at any time.
* When switching formats, displayed coordinates are automatically converted without loss of precision.

### Real-Time Synchronization

* Changes made by one dispatcher (e.g. call details, incident state, unit assignments) appear on other dispatchers' screens within seconds.
* The UI should not require manual refresh to see updates from other dispatchers.

### Loading and Progress Indicators

* The UI must respond to user input within 100–200 ms.
* Operations that take longer (e.g. geocoding requests, server commands) show a progress indicator or spinner.
* The UI must remain responsive during progress — the dispatcher can continue working in other areas while an operation completes.


## Launcher Page

* The launcher page is the first page the dispatcher sees after logging in via the OIDC provider.
* The launcher provides actions to:
  * Open the primary window (for the primary monitor)
  * Open the secondary window (for the secondary monitor)
  * Log out of the application
  * Navigate to the OIDC account management page (opens externally)
* If a window (primary or secondary) is already open in the current browser session, the corresponding action on the launcher is either disabled or brings the existing window to focus.
* The launcher itself does not display operational data (calls, incidents, units).


## Layouts and Customization

* Dispatcher windows have customizable layouts, but no user-draggable splitters. Dispatchers switch between pre-defined layouts optimized for various usages.
* Layout change buttons are in the right part of the window header.
  * They can also be triggered by keyboard shortcuts, e.g. Ctrl+Shift+1, Ctrl+Shift+2, etc.
* Layout configurations are user specific and persist across logouts.


## Primary Window

* Split into three columns: call details, incident details, call/incident lists (split vertically on top of each other).
* Primary actions in the header: New Call, New Incident.
* Layout options: Dispatcher can change the widths of the columns by selecting from pre-defined layouts.
  * For example, when receiving a call, the call column is wider.
  * When dealing with an incident, the incident column is wider.
  * When looking up incidents or calls from the list, that column is wider.

### Call Detail Form

* Fields at the top, action buttons at the bottom.
* Fields (see [Domain: Call](../Domain/Call.md)):
  * Caller name — text field, max 100 characters
  * Caller phone number — text field, E.164 format
  * Location — see [Location Entry](#location-entry) section
  * Description — text area, max 1000 characters
  * Outcome — displayed when setting the outcome before ending the call
  * Outcome rationale — text area, max 1000 characters; shown when outcome requires a rationale
* Outcome selection:
  * The six outcome values (`incident_created`, `attached_to_incident`, `caller_advised`, `hoax`, `accidental`, `other_no_actions_taken`) are presented as selectable options (e.g. dropdown or button group).
  * `incident_created` and `attached_to_incident` are set automatically by the corresponding actions and are not directly selectable by the dispatcher.
  * When `caller_advised`, `hoax`, `accidental`, or `other_no_actions_taken` is selected, a rationale text field appears.
* Vicinity check:
  * When a call's coordinates are known, the system checks for and displays nearby active calls and incidents.
  * This is presented as a special filter applied to the call and incident lists in the third column. The filtered state is clearly indicated visually (e.g. a banner above the list, distinct background color, or other prominent treatment) so the dispatcher immediately notices that the lists are showing vicinity results rather than the default view.
  * The dispatcher can dismiss the vicinity filter to return to the normal list view.
  * This helps the dispatcher determine whether the call concerns an already-reported incident.
* Actions:
  * New Call — creates a new call in state `active` (see [UC: Enter Call Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md))
  * End Call — ends the active call; requires outcome to be set
  * Create Incident From Call — creates a new incident linked to this call (see [UC: Create Incident From Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md))
  * Attach to Incident — attaches this call to an existing incident (see [UC: Attach Call To Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md))
  * Detach from Incident — detaches this call from its incident; only available when outcome is `attached_to_incident` (see [UC: Detach Call From Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md))
  * Copy Location to Incident — copies the call's location to the linked incident's location field
* Filling in coordinates or geocoding an address should result in a marker on the map in the secondary window.
  * Moving the marker on the map should update the coordinates in the call.
* Possible to show the call on a map in the secondary window (center on call).

### Incident Detail Form

* Fields at the top, assigned units area and action buttons at the bottom.
* Fields (see [Domain: Incident](../Domain/Incident.md)):
  * Incident type — selection from available incident types, showing code and description in the dispatcher's language
  * Incident priority — selection showing priority levels A, B, C, D, N
  * Location — see [Location Entry](#location-entry) section
  * Description — text area, max 1000 characters
* State indicator:
  * The current incident state (`new`, `queued`, `active`, `monitored`, `ended`) is prominently displayed.
  * See [Incident State and Priority Indicators](#incident-state-and-priority-indicators) for visual treatment.
* State transition controls:
  * Controls for manually setting state to `queued`, `active`, or `monitored` (see [UC: Set Incident State](../UseCases/Dispatcher/UC-Set-Incident-State.md)).
  * Only valid transitions from the current state are offered.
  * Close Incident action to transition to `ended` (see [UC: Close Incident](../UseCases/Dispatcher/UC-Close-Incident.md)).
* Validation feedback:
  * Before state transitions that require `incident_type`, `incident_priority`, and `location` (transition to `queued` or `active`), missing fields are highlighted.
  * Before transition to `active`, the system also checks that at least one unit is assigned.
  * Validation messages appear inline next to the relevant fields.
* Bottom area — tabs:
  * The bottom area of the incident detail form uses tabs to switch between three views:
    * **Assigned Units** tab
    * **Linked Calls** tab
    * **Incident Log** tab
* Assigned Units tab:
  * Lists all `IncidentUnit` records for the incident.
  * Each entry shows: unit callsign, staffing (if available), assignment timestamps (`unit_assigned_at`, `unit_dispatched`, `unit_en_route`, `unit_on_scene`, `unit_available`, `unit_back_at_station`, `unit_unassigned_at`).
  * Active assignments (without `unit_unassigned_at`) are visually distinct from completed ones.
  * Actions per unit: unassign (when in `assigned_radio`/`assigned_station`), show on map.
  * Actions per selected units: Dispatch Selected Units.
* Linked Calls tab:
  * Lists all calls linked to this incident (where `Call.incident_id` references this incident).
  * Each call is shown as a card displaying the complete call details: caller name, caller phone number, location, description, outcome, outcome rationale, receiving dispatcher, call started/ended timestamps.
  * Cards are read-only — call editing is done in the call detail column.
  * The list is scrollable when multiple calls are linked to the incident.
* Actions:
  * Create Incident — creates a new standalone incident (see [UC: Create Incident](../UseCases/Dispatcher/UC-Create-Incident.md))
  * Close Incident — ends the incident (see [UC: Close Incident](../UseCases/Dispatcher/UC-Close-Incident.md))
  * Set State — transitions the incident to a selected state
  * Dispatch New Units — dispatches all units in `assigned_radio`/`assigned_station` state (see [UC: Dispatch Units](../UseCases/Dispatcher/UC-Dispatch-Units.md))
  * Dispatch Selected Units — dispatches or re-alerts selected units
  * Manual Dispatch Confirmation — confirms dispatch for a unit stuck in `dispatching` state
* Filling in coordinates or geocoding an address should result in a marker on the map in the secondary window.
  * Moving the marker on the map should update the coordinates in the incident.
* Possible to show the incident on a map in the secondary window (center on incident).
* Possible to show a selected unit on the map (center on unit).

### Call and Incident Lists

* Call list at the top, Incident list at the bottom.
* Call list columns: call started time, caller name, caller phone number, location summary, state, outcome, receiving dispatcher.
* Incident list columns: incident created time, incident type, incident priority, location summary, state, number of assigned units.
* Filtering:
  * Both lists support text-based filtering (e.g. by caller name, description keywords, incident type code).
  * Incident list supports filtering by state and priority.
  * By default, ended incidents are hidden. Dispatchers can toggle a filter to show ended incidents.
* Sorting:
  * Both lists support sorting by any visible column.
  * Default sort: most recent first.
* Selection:
  * Selecting a call opens it in the call detail form.
  * Selecting an incident opens it in the incident detail form.
* Read-only summary view:
  * It is possible to open a read-only summary of a call or incident without loading it into the call or incident detail column. This is useful when the dispatcher wants to check whether another call or incident is related without losing the context of what they are currently working on.
  * The read-only summary replaces the list view in the third column, showing the key details of the selected call or incident.
  * A back button closes the summary and restores the list view with its previous sorting and filtering intact.
* Possible to show a selected incident or call on the map.


## Secondary Window

* Shows the map and the unit dashboard.
* Layout options: Dispatcher can choose to show only the map, only the dashboard, split the view 1-1, 1-2, or 2-1.

### Unit Dashboard

* Shows all active units and their current status.
* Information shown per unit card/row:
  * Callsign
  * Current state (color coded — see [Unit State Visual Encoding](#unit-state-visual-encoding))
  * Staffing (officers + subofficers + crew format, e.g. `1+1+3`), if declared
  * Home station name
  * Assigned incident identifier (if assigned)
  * Coordinates age — how long ago the unit's coordinates were last updated; helps dispatchers assess position freshness
* Filter by callsign.
* Sort (ascending and descending) by callsign or state.
* Show as cards or in a list.
* Unit actions:
  * Change state — allows the dispatcher to set the unit's operational state (e.g. `unavailable`, `available_over_radio`, `available_at_station`, `en_route`, `on_scene`); only states that the dispatcher is authorized to set are offered
  * Show on map (center on unit position)
  * Assign to incident open in primary window
  * Open incident in primary window (if unit is assigned to an incident)

### Map

* Zoom in and out, move, etc. (provided by map component).
* Select background layer (GIS Server may provide different tile sets, one for terrain, another for navigation and buildings, etc).
* Show/hide layers:
  * Stations
  * Units
  * Active incidents
  * Active calls
* Markers for different entity types are visually distinct (different shapes, icons, or labels).
* Markers placed from the primary window (call location, incident location) are synchronized to the map in the secondary window.
* Temporary lookup markers (from address or coordinate lookup) are visually distinct from operational markers.
* Lookup by coordinates.
* Lookup by address (geocoding) — see [UC: Lookup Address](../UseCases/Dispatcher/UC-Lookup-Address.md).


## Location Entry

Location entry is used in the call detail form and the incident detail form. It maps to the four [Location](../Domain/Location.md) variants.

### Flat Form — "Fill What You Know"

Instead of selecting a location variant before entering data, the dispatcher fills in a single flat form and the variant resolves automatically from which fields are filled. All fields are visible from the start in a stable layout that never changes shape.

#### Form Layout

```
Municipality:     [                         ▼]
Location name:    [                           ]
Number: [        ]    or    Cross street: [                   ]
Coordinates:      [                           ]
Details:          [                           ]
                                    ☐ Approximate location
```

* **Municipality** — selected from a searchable list of Finnish municipalities.
* **Location name** — maps to `address_name`, `road_name_a`, `name`, or `reference_place` depending on the resolved variant. Supports geocoding search when GIS Server is available (see [Geocoding Integration](#geocoding-integration)).
* **Number** and **Cross street** — mutually exclusive fields on the same row, separated by an "or" label (see [Mutual Exclusivity](#mutual-exclusivity-number-vs-cross-street)).
* **Coordinates** — manual entry in DD, DDM, or DMS format.
* **Details** — additional information about the location.
* **Approximate location** checkbox — toggles between NamedPlace and RelativeLocation semantics (see [NamedPlace vs. RelativeLocation](#namedplace-vs-relativelocation)).

#### Variant Auto-Resolution Rules

The location variant is determined automatically by which fields the dispatcher fills in:

| Number filled | Cross street filled | Approximate flag | Resolved variant |
|:---:|:---:|:---:|---|
| yes | no | no | **ExactAddress** |
| no | yes | no | **RoadIntersection** |
| no | no | no | **NamedPlace** |
| no | no | yes | **RelativeLocation** |

* A small **variant badge** (e.g., `Exact Address`, `Intersection`) appears near the location section header, updating in real time as fields change.
* The badge helps the dispatcher confirm what the system will save, but requires no action from them.

#### Mutual Exclusivity: Number vs. Cross Street

* Number and Cross street are on the **same row**, visually separated by an "or" label.
* When the dispatcher types in one, the other is **visually deemphasized** (e.g., dimmed border, reduced opacity) to signal they are alternatives.
* If the dispatcher fills in both, an inline validation error appears: "Enter either a number or a cross street, not both."
* Clearing one re-enables the other. The primary location name is never lost.

#### NamedPlace vs. RelativeLocation

* An **"Approximate location" checkbox** below the Details field toggles between NamedPlace and RelativeLocation semantics.
* When checked:
  * The "Location name" label changes to "Reference place" to reflect the different semantics.
  * Details becomes **required** (inline validation if empty).
  * The variant badge shows "Relative Location".
* When unchecked (default): variant is NamedPlace (assuming Number and Cross street are both empty).

#### Geocoding Integration

* The "Location name" field supports geocoding search when GIS Server is available.
* As the dispatcher types, matching results appear in a dropdown, including location type indicators (address, intersection, place).
* Selecting a geocoding result **auto-fills the appropriate fields**:
  * Selecting an address result fills location name, optionally number, municipality, and coordinates.
  * Selecting an intersection result fills location name (road A) and cross street (road B), municipality, and coordinates.
  * Selecting a place result fills location name, municipality, and coordinates.
* After auto-fill, the variant badge updates to reflect the resolved type.
* The dispatcher can always edit or clear auto-filled fields.

#### Manual Coordinate Entry

* The dispatcher can enter coordinates manually in DD, DDM, or DMS format.
* Entered coordinates are validated against Finland bounds (latitude 58.84° to 70.09°, longitude 19.08° to 31.59°).
* Invalid coordinates are rejected with an inline error message.

#### Map Marker Synchronization

* When coordinates are entered or geocoded, a marker appears on the map in the secondary window.
* Moving the marker on the map updates the coordinates in the form.
* This synchronization works bidirectionally between the primary and secondary windows.

#### Keyboard Workflow

* Tab order: Municipality → Location name → Number → Cross street → Coordinates → Details → Approximate checkbox.
* The dispatcher can Tab through fields quickly, filling in what they know and skipping past what they don't.
* The form's stable shape means the Tab order never changes.

#### Degraded Mode (GIS Server Unavailable)

* The flat form and variant auto-resolution work identically to connected mode — they do not depend on geocoding.
* The only difference: geocoding suggestions do not appear in the location name dropdown.
* All fields can be entered manually by the dispatcher.
* Manual coordinate entry and map marker placement remain functional.


## Unit State Visual Encoding

* Each unit state is assigned a distinct color to enable at-a-glance recognition.
* The specific color assignments are left to the visual designer; the important principle is that:
  * Available states (`available_over_radio`, `available_at_station`) are visually calm and neutral.
  * Assignment and dispatch states (`assigned_radio`, `assigned_station`, `dispatching`, `dispatched`) are visually prominent to indicate pending action.
  * Active response states (`en_route`, `on_scene`) are visually distinct from assignment states.
  * `unavailable` is visually subdued.
* A text label showing the state name is always displayed alongside the color, ensuring the state is accessible without relying on color alone.
* The same color encoding is used consistently across the unit dashboard and any unit references in other views.


## Incident State and Priority Indicators

### Incident State

* Each incident state (`new`, `queued`, `active`, `monitored`, `ended`) has a distinct visual treatment (e.g. background color, badge, or icon).
* The specific visual assignments are left to the visual designer; the important principles are:
  * `new` is visually distinct to signal that the incident needs attention.
  * `queued` indicates waiting — units are needed but not yet available.
  * `active` is the most prominent state, indicating ongoing response.
  * `monitored` is calmer than `active`, indicating passive observation.
  * `ended` is visually subdued.
* A text label showing the state name is always displayed alongside any visual indicator.

### Incident Priority

* Priority levels A, B, C, D, and N are visually distinguished.
* The specific visual assignments are left to the visual designer; the important principles are:
  * Higher urgency priorities (A, B) are more visually prominent than lower ones (C, D).
  * Priority N (operational order, not a real-world incident) is visually distinct from all other priorities.
* Priority is displayed in both the incident detail form and the incident list.


## Incident Log View

* The incident log is displayed in the Incident Log tab in the bottom area of the incident detail form (shared with the Assigned Units and Linked Calls tabs).
* The log is presented as a scrollable, chronological timeline of entries (newest at the top or bottom — the visual designer may choose).
* Each log entry shows:
  * Timestamp — displayed in `Europe/Helsinki` timezone, 24-hour clock
  * Entry type indicator — visually distinguishes automatic entries from manual entries
  * Dispatcher name — shown for entries resulting from dispatcher input; absent for system-generated entries
  * Content:
    * For manual entries: the free-form text description
    * For automatic entries: a human-readable description of the change (e.g. "State changed to active", "Unit RVS11 assigned", "Call linked")
* Manual log entry creation:
  * An "Add Log Entry" action is available at the top or bottom of the log view.
  * The dispatcher enters free-form text (max 1000 characters) and submits.
  * The entry is immediately visible in the log.
* Automatic log entries are not editable or deletable.


## Cross-Window Communication

The primary and secondary windows coordinate to provide a unified operational view.

* **Show on Map**: from the primary window, the dispatcher can request to show a call, incident, or unit on the map in the secondary window. The map centers on the entity's coordinates.
* **Center on Unit**: from the incident detail form or unit dashboard, the dispatcher can center the map on a specific unit's last known position.
* **Map Marker to Location Field**: when a dispatcher places or moves a marker on the map in the secondary window, the coordinates update in the corresponding location field in the primary window (call or incident).
* **Location Field to Map Marker**: when a dispatcher enters or changes coordinates in the primary window (via geocoding or manual entry), the marker updates on the map in the secondary window.
* **Assign to Incident**: from the unit dashboard in the secondary window, the dispatcher can assign a unit to the incident currently open in the primary window.
* **Open Incident**: from the unit dashboard in the secondary window, the dispatcher can open the incident a unit is assigned to in the primary window's incident detail form.


## Keyboard Accessibility

* All actions available through mouse interaction must also be accessible via keyboard.
* Focus management:
  * When a new call or incident is created, focus moves to the first editable field in the corresponding form.
  * When a dialog or overlay appears, focus moves to it. When it is dismissed, focus returns to the element that triggered it.
  * Tab order follows a logical reading order within each column and form.
* Key workflows that benefit from keyboard-only operation include:
  * Creating and filling in call details
  * Creating an incident from a call
  * Assigning units and dispatching
  * Switching between calls and incidents in the list
  * Switching layouts
* No specific key bindings are prescribed — the visual designer and implementer should choose bindings that are intuitive and do not conflict with browser defaults.


## Notifications and Alerts

Notifications inform the dispatcher of events that require attention but do not block the current workflow.

* **Dispatch timeout**: when a unit remains in `dispatching` state beyond the configured timeout, the dispatcher receives a notification. The notification identifies the unit and the incident, and offers a path to manual dispatch confirmation.
* **No active Alert Targets**: when a unit to be dispatched has no active Alert Targets, a warning is displayed indicating the unit cannot receive alerts automatically. The dispatcher can still use manual dispatch confirmation.
* **GIS Server unavailable**: when the GIS Server is unavailable, a persistent message is displayed (e.g. in the footer or as a banner) indicating that geocoding and map tiles may be affected. Manual coordinate entry and map operations that do not require the GIS Server remain available.
* **Vicinity alerts**: when a call's coordinates match the vicinity of an active call or incident, the system highlights this in the call detail form so the dispatcher can evaluate whether the call should be attached to an existing incident.
* **Session expiry warning**: a warning banner appears well in advance of the session reaching its maximum lifetime, giving the dispatcher time to save work and re-authenticate.


## Session and Authentication

* After authenticating via the OIDC provider, the dispatcher arrives at the launcher page.
* **Session expiry warning**: when the session approaches its maximum lifetime, a persistent warning banner appears across all open windows (launcher, primary, secondary). The warning gives the dispatcher time to prepare for re-authentication.
* **Idle session timeout**: the Dispatcher Client has an idle session timeout. The dispatcher is warned before the timeout expires.
* **Forced logout**: when an administrator terminates the session, all open windows display a full-screen overlay informing the dispatcher that they have been logged out, with an option to re-authenticate.
* **Connection lost**: when the connection to the CAD Server is lost, a full-screen overlay is displayed showing reconnection status (e.g. "Disconnected — Reconnecting..."). The overlay is removed automatically when the connection is re-established. The dispatcher cannot interact with operational data while disconnected.
* **Reconnection**: the client automatically reconnects with exponential back-off. Intermediate status (disconnected, reconnecting, connected) is shown to the dispatcher.


## Degraded Mode Behavior

* The footer of each dispatcher window displays whether the system is in normal or degraded mode.
* Degraded mode is entered when certain services become unavailable (e.g. GIS Server).
* In degraded mode:
  * Geocoding search (address lookup) is unavailable. The dispatcher is informed and must enter location details manually.
  * Map tile loading may fail or show stale tiles. The map remains navigable but may not display updated imagery.
  * Unit location data may be outdated. The coordinates age indicator in the unit dashboard helps the dispatcher assess freshness.
* Features that remain fully available in degraded mode:
  * Creating and managing calls and incidents
  * Assigning and dispatching units
  * Manual coordinate entry
  * Manual state changes
  * All actions that do not depend on GIS Server availability
* The dispatcher can always contact units over radio as an out-of-band fallback for status updates, location queries, and dispatch confirmation.


## Accessibility Considerations

* All interactive elements must be operable via keyboard (see [Keyboard Accessibility](#keyboard-accessibility)).
* Color is never the sole means of conveying information — text labels, icons, or patterns accompany color-coded elements (unit states, incident states, incident priorities).
* Focus indicators must be clearly visible against the dark theme background.
* Screen reader considerations:
  * State changes (unit state, incident state) that update dynamically should be announced to screen readers using appropriate techniques (e.g. live regions).
  * Form validation errors should be associated with their corresponding fields for screen reader users.
  * Dialogs and overlays should be properly announced when they appear.
