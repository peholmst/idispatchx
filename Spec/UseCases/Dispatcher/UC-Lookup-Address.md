# UC: Lookup Address

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.

## Postconditions

* The map view is centered on the looked-up location.
* No persistent data has been created or modified.

## Triggers

* Dispatcher issues a Lookup Address command.

## Main Success Scenario

1. Dispatcher issues the Lookup Address command and enters a search query.
   * The search query is free-form text (e.g., street address, place name, road intersection).
   * The search query may be a road name without a street number; in this case the system returns a representative result for the road.
2. System sends the search query to the GIS Server geocoding service.
3. GIS Server returns a list of matching locations.
4. System displays the list of matching locations to the dispatcher.
   * Each result shows the location name(s), municipality, and location type (address, place, intersection).
   * For address results, the street number is shown alongside the street name when one is present.
   * Results are displayed in the dispatcher's preferred language where available.
5. Dispatcher selects a location from the list.
   * The dispatcher may click a result with the mouse.
   * The dispatcher may also navigate the list with the up/down arrow keys and confirm the selection with Enter. (See [UX Guidelines: Keyboard Navigation](../../UXDesigns/Dispatcher-Client-UX-Guidelines.md).)
6. System centers the map view on the selected location's coordinates.
7. System places a temporary marker on the map at the selected location.
   * The marker is purely visual and is not persisted.
   * The marker remains visible until the dispatcher performs another lookup or clears it explicitly via the Clear Lookup command.
   * The marker remains visible when the dispatcher pans or zooms the map.

## Alternative Flow A: Single Match

1. System receives exactly one matching location from the GIS Server.
2. System automatically centers the map view on the matching location and places a temporary marker.
3. Use case ends.

## Alternative Flow B: No Matches Found

1. System receives no matching locations from the GIS Server.
2. System displays a message indicating no locations match the search query.
3. Dispatcher may refine the search query and retry.
4. Use case returns to step 1 of the Main Success Scenario.

## Alternative Flow C: Lookup by Coordinates

1. Dispatcher enters coordinates directly in any supported format (DD, DDM, or DMS per [NFR: Internationalization](../../NonFunctionalRequirements/Internationalization.md)).
   * The dispatcher may switch between DD, DDM, and DMS formats using a format selector control. When switching formats, any parseable coordinate value in the input is converted automatically to the new format.
   * The degree symbol (`°`) is not required; the parser accepts entries without it (e.g. `60.17, 24.94` for DD, `60 10.1914N 24 56.3027E` for DDM, or `60 10 11.49N 24 56 18.16E` for DMS).
   * The coordinate entry component is shared across call detail and incident detail forms (future use).
2. System validates that coordinates are within Finland bounds (latitude 58.84 to 70.09, longitude 19.08 to 31.59).
3. If valid, System centers the map view on the coordinates and places a temporary marker.
4. If invalid, System displays an error indicating the coordinates are outside the supported area.

## Alternative Flow D: Clear Lookup Result

1. Dispatcher issues a Clear Lookup command.
2. System removes the temporary marker from the map view.
   * Clicking Clear is the only explicit way to remove the marker; panning or zooming the map does not remove it.
3. Map view remains at its current position.

## Exceptions

* **Exception: GIS Server is unavailable**
  * System displays a message indicating that address lookup is temporarily unavailable.
  * Dispatcher may enter coordinates directly using Alternative Flow C, which does not require GIS Server.
  * Dispatcher may pan and zoom the map manually to find the desired location.

* **Exception: Search query is too short or empty**
  * System displays a message indicating the search query must contain at least 3 characters.
  * Dispatcher must enter a longer search query.

* **Exception: GIS Server request times out**
  * System displays a message indicating the request took too long.
  * Dispatcher may retry the search or use manual navigation.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without GIS Server; dispatcher can enter coordinates manually or navigate the map manually
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - geocoding requests should return within seconds; UI must respond to user input within 100-200ms
* [NFR: Internationalization](../../NonFunctionalRequirements/Internationalization.md) - coordinates must be in EPSG:4326; coordinate bounds validation for Finland
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access; GIS Server endpoints require JWT bearer authentication

## Relevant Domain Concepts

* [Domain Concept: Location](../../Domain/Location.md) - location variants (ExactAddress, RoadIntersection, NamedPlace), coordinate format and bounds
* [Domain Concept: Municipality](../../Domain/Municipality.md) - municipality context for search results
* [Domain Concept: MultilingualName](../../Domain/MultilingualName.md) - multilingual display of location names in search results
