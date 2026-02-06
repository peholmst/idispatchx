# Dispatcher Client UX Guidelines

This document is an advisory document for the visual design of Dispatcher Client. It does not contain any technical details about how Dispatcher Client should be implemented. It does not override any other specification documents. It will be expanded as new use cases emerge.

## General Look and Feel

* The look and feel of Dispatcher Client is similar to that of the dark mode of Visual Studio Code.
* When a user logs in, they end up at a launcher page.
* From the launcher page, they can open two windows; one for the primary monitor and one for the secondary monitor.
* Within the same browser session, you can only have one instance of each window (launcher, primary, secondary) open at any given time.
* The launcher also has actions for logging out and going to the OIDC account page
* Dispatcher windows (primary and secondary) have a header (brighter color) and a footer (dark mode).
  * The header contains the logo of the application and the current date and time (left corner), primary actions (center) and customization actions (right)
  * The footer contains the name of the current user (left corner) and normal/degraded mode (right)
    * In the future, the footer might contain other info or actions.
* Error messages that prevent operation (like lost connection, logged out, session expired) show up as a full-screen dialog or popover.
* User recoverable error messages (like validation errors) show up in banners or next to the widget where the error can be fixed. No dialogs or "toasts".
  * They either disappear when the error is resolved, or when the user closes them (depending on the type of error)
* Intermediate status messages (e.g. disconnected -> connecting -> connected) are used where appropriate.
* The application can be used with a keyboard only for power users who don't want to reach for the mouse.

## Layouts and Customization

* Dispatcher windows have customizable layouts, but no user-draggable splitters. Dispatchers switch between pre-defined layouts optimized for various usages.
* Layout change buttons are in the right part of the window header
  * They can also be triggered by keyboard shortcuts, e.g. Ctrl+Shift+1, Ctrl+Shift+2, etc.
* Layout configurations are user specific and persist logouts.

## Primary Window

* Split into three columns: call details, incident details, call/incident lists (split vertically on top of each other)
* Primary actions in the header: New Call, New Incident
* Layout options: Dispatcher can change the widths of the columns by selecting from pre-defined layouts.
  * For example, when receiving a call, the call column is wider
  * When dealing with an incident, the incident column is wider
  * When looking up incidents or calls from the list, that column is wider
* Call detail form
  * Field at the top, buttons at the bottom
  * Filling in coordinates or geocoding address should result in a marker on the map in the secondary window
    * Moving the marker on the map should update the coordinates in the call
  * Possible to show the call on a map in the secondary window (center on call)
* Incident detail form
  * Fields at the top, assigned units and buttons at the bottom
  * Filling in coordinates or geocoding address should result in a marker on the map in the secondary window
    * Moving the marker on the map should update the coordinates in the incident
  * Possible to show the incident on a map in the secondary window (center on incident)
  * Possible to show a selected unit on the map (center on unit)
* Call list at the top, Incident list at the bottom
  * Possible to show a selected incident or call on the map

## Secondary Window

* Shows the map and the unit dashboard
* Layout options: Dispatcher can choose to show only the map, only the dashboard, split the view 1-1, 1-2, or 2-1.
* Unit dashboard features:
  * Units are color coded by unit state, but also shows state in text
  * Filter by callsign
  * Sort (asc & desc) by callsign or state - callsign
  * Show as cards or in a list
  * Unit actions:
    * Change state
    * Show on map (center on unit position)
    * Assign to incident open in primary window
    * Open incident in primary window (if unit is assigned to an incident)
* Map features:
  * Zoom in & out, move, etc. (provided by map component)
  * Select background layer (GIS server may provide different tile sets, one for terrain, another for navigation and buildings, etc)
  * Show/hide:
    * Stations
    * Units
    * Active incidents
    * Active calls
  * Lookup by coordinates 
  * Lookup by address (geocoding)
