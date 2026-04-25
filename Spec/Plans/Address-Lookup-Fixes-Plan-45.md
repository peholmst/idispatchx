# Address Lookup Fixes — Implementation Plan (Issue #45)

## References

- [Issue #45](https://github.com/peholmst/iDispatchX/issues/45) — Bug report: address lookup not working in the secondary window
- [UC: Lookup Address](../UseCases/Dispatcher/UC-Lookup-Address.md) — Use case being fixed
- [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) — Coordinate formats and bounds
- [Technical Design: GIS Server REST API](../TechnicalDesigns/GIS-Server-REST-API.md) — Geocoding API spec
- [Dispatcher Client UX Guidelines](../UXDesigns/Dispatcher-Client-UX-Guidelines.md) — Font, keyboard nav, coordinate display

## Overview

Eight bugs were reported in the address lookup feature of the secondary dispatcher window. This plan covers the spec corrections, code fixes, and Playwright tests needed to resolve all eight.

| Phase | Description | Tasks |
|-------|-------------|-------|
| 1 | Spec corrections | 3 |
| 2 | Client-side font and i18n fixes | 2 |
| 3 | Client-side result display fixes | 2 |
| 4 | Marker persistence fix | 1 |
| 5 | Coordinate entry improvements | 2 |
| 6 | Keyboard navigation | 1 |
| 7 | GIS Server: road name search | 2 |
| 8 | Map view reset fix | 1 |
| 9 | Playwright tests | 1 |

---

## Phase 1 — Spec Corrections

### Task 1.1 — Update UC: Lookup Address (marker persistence)

**Status:** Not Started

**Problem:** Step 7 of the Main Success Scenario states that the marker is removed when the dispatcher pans the map away. Issue #45 #7 reports this is wrong behaviour: the marker should remain until a new lookup is performed or the Clear button is clicked.

**Changes to `Spec/UseCases/Dispatcher/UC-Lookup-Address.md`:**

- Step 7: Remove the clause "or pans the map away" from the marker lifetime description.
- Alternative Flow D (Clear Lookup Result): Clarify that Clear is the only explicit way to remove the marker (in addition to starting a new lookup).
- Add a new bullet under step 7: "The marker remains visible when the dispatcher pans or zooms the map."

**Acceptance criteria:**
- UC step 7 no longer mentions removing the marker on map pan.
- Alternative Flow D is accurate.

**Dependencies:** None.

---

### Task 1.2 — Update UC: Lookup Address (keyboard navigation and address number display)

**Status:** Not Started

**Problem:**
- Issue #8: The use case does not describe keyboard navigation of search results.
- Issue #6: The use case does not explicitly require the street number to be shown alongside the street name in the results list.

**Changes to `Spec/UseCases/Dispatcher/UC-Lookup-Address.md`:**

- Step 4: Add that each address result also shows the street number when one is present.
- Step 5: Add an explicit alternative for keyboard selection: "The dispatcher may also navigate the list with the up/down arrow keys and confirm with Enter."
- Cross-reference the UX Guidelines keyboard-only requirement.

**Acceptance criteria:**
- UC step 4 mentions street number in result display.
- UC step 5 covers keyboard navigation.

**Dependencies:** None.

---

### Task 1.3 — Update UC: Lookup Address (road-name-only search and coordinate format switching)

**Status:** Not Started

**Problem:**
- Issue #5: The UC does not explicitly state that searching by road name only (without a street number) must return results.
- Issue #3: The coordinate entry alternative flow (Flow C) does not mention format switching or that the degree symbol is optional.

**Changes to `Spec/UseCases/Dispatcher/UC-Lookup-Address.md`:**

- Step 1: Add a note that the search query may be a road name without a number; the system returns a representative result for the road in this case.
- Alternative Flow C: Expand to say the dispatcher can switch between DD, DDM, and DMS formats using a format selector control. The input is converted automatically when switching formats. The degree symbol is not required; the parser accepts entries without it (e.g. `60.17, 24.94` or `60 10.1914N 24 56.3027E`).
- Note that Alternative Flow C uses a shared coordinate entry component that is reused across call detail and incident detail forms (future use).

**Acceptance criteria:**
- UC Flow C covers format switching and degree-symbol-optional entry.
- UC step 1 mentions road-name-only search.

**Dependencies:** None.

---

## Phase 2 — Client-Side Font and Internationalisation Fixes

### Task 2.1 — Fix font family in toolbar and search results (Issue #1)

**Status:** Not Started

**Root cause:** `base.css` does not set `font-family` on `body` or `html`, so all shadow DOM elements that use `font-family: inherit` fall back to the browser default (typically a serif font). The `--font-family-ui` CSS token is defined but never applied to the root element.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/styles/base.css` — add `font-family: var(--font-family-ui)` to the `html, body` rule.
- `Implementation/clients/dispatcher-client/src/ui/SecondaryWindow.css` — add `font-family: var(--font-family-ui)` to `:host`.
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.css` — add `font-family: var(--font-family-ui)` to `:host`.

**Acceptance criteria:**
- All visible text in the toolbar (Base label, Layers label, layer select, layer toggle buttons) uses the design-system font ("Segoe UI", system-ui, sans-serif).
- All visible text in the lookup bar (inputs, buttons, dropdown results) uses the same font.
- No other component's visual appearance is changed.

**Dependencies:** None.

---

### Task 2.2 — Internationalise "Base:" and "Layers:" toolbar labels (Issue #2)

**Status:** Not Started

**Root cause:** `SecondaryWindow.ts#makeToolbar()` hard-codes the English strings `'Base:'` and `'Layers:'`.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/i18n/index.ts`
  - Add translation keys: `lookup.toolbar.base`, `lookup.toolbar.layers`.
  - Add Finnish and Swedish translations:
    - `lookup.toolbar.base`: FI `Pohjakartta:`, SV `Bakgrundskarta:`.
    - `lookup.toolbar.layers`: FI `Tasot:`, SV `Lager:`.
- `Implementation/clients/dispatcher-client/src/ui/SecondaryWindow.ts`
  - Replace `'Base:'` with `t('lookup.toolbar.base')`.
  - Replace `'Layers:'` with `t('lookup.toolbar.layers')`.

**Acceptance criteria:**
- With Finnish locale active, toolbar shows "Pohjakartta:" and "Tasot:".
- With Swedish locale active, toolbar shows "Bakgrundskarta:" and "Lager:".
- With English locale (default), toolbar shows "Base:" and "Layers:".

**Dependencies:** None.

---

## Phase 3 — Result Display Fixes

### Task 3.1 — Show street number in address results (Issue #6)

**Status:** Not Started

**Root cause:** `resultDisplayName()` in `LookupBar.ts` returns only `result.name` for address-type results, ignoring `result.number`.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.ts`
  - In `resultDisplayName()`, for `type === 'address'` (and the default branch), append `result.number` if present:
    ```
    const name = result.name ? pickName(result.name, pref) : '';
    const number = result.number ?? '';
    return number ? `${name} ${number}` : name;
    ```

**Acceptance criteria:**
- A result of type `address` with `name: {fi: "Mannerheimintie"}` and `number: "5"` displays as `"Mannerheimintie 5"`.
- A result without a number still displays the name only (no trailing space or undefined).

**Dependencies:** None.

---

### Task 3.2 — Remove incorrect duplicate marker layer in SecondaryWindow (minor correctness fix)

**Status:** Not Started

**Root cause:** `SecondaryWindow.ts#initMap()` creates a `VectorLayer` with `markerSource` and adds it to the OL map; then `LookupBar.initialize()` creates a *second* `VectorLayer` with the same `markerSource` (this one has the orange `MARKER_STYLE`) and adds it as well. This means each feature in `markerSource` is rendered twice — once with no style (invisible) and once with the orange style. While not visually harmful today, it wastes resources and can cause unpredictable behaviour with future OL versions.

**Fix:** Remove the unstyled `markerLayer` from `SecondaryWindow.ts`. The styled layer created inside `LookupBar.initialize()` is sufficient.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/SecondaryWindow.ts`
  - In `#initMap()`: remove the `markerLayer` variable and remove it from the `layers` array passed to `OlMap`.
  - Retain `markerSource`; it is still passed to `lookupBarEl.initialize()`.

**Acceptance criteria:**
- Only one marker layer exists in the OL map after initialisation.
- The orange marker still renders correctly.

**Dependencies:** None.

---

## Phase 4 — Marker Persistence Fix

### Task 4.1 — Keep marker visible when panning or zooming (Issue #7)

**Status:** Not Started

**Root cause:** `LookupBar.ts#initialize()` registers a `moveend` listener that clears the marker whenever the map centre moves more than 1 metre from the marker's coordinate. This is the source of the bug. The use case spec (Task 1.1) must be corrected before or in parallel.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.ts`
  - Remove the `olMap.on('moveend', ...)` listener entirely.
  - Remove the `#markerCoord` field (no longer needed for tracking map-pan removal).
  - The `#clearMarker()` method and `#clearAll()` method remain unchanged; the marker is still removed when a new lookup is started or Clear is clicked.
  - Remove the `coordsClose` helper function (no longer needed).

**Acceptance criteria:**
- After a location is selected and a marker is placed, panning the map does not remove the marker.
- After zooming in or out, the marker remains on the map.
- Clicking Clear still removes the marker.
- Starting a new lookup still removes the previous marker.

**Dependencies:** Task 1.1 (spec must reflect this behaviour).

---

## Phase 5 — Coordinate Entry Improvements

### Task 5.1 — Make degree symbol optional in DDM and DMS coordinate parsing (Issue #3, partial)

**Status:** Not Started

**Root cause:** The DDM and DMS regexes in `coordinates.ts` require `°` (Unicode degree sign) in the pattern `'(\\d+)\\s*°\\s*'`. Users on keyboards without a degree symbol cannot enter DDM or DMS coordinates. The DD parser already makes `°` optional (`°?`).

**Fix:** Make `°` optional in `tryParseDDM` and `tryParseDMS` as well.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/geo/coordinates.ts`
  - In `tryParseDDM`, change `'(\\d+)\\s*°\\s*'` to `'(\\d+)\\s*°?\\s*'`.
  - In `tryParseDMS`, change the same pattern in its token definition.
  - Update the `tryParseDD` guard: it currently rejects input containing `°`; this guard is still correct and does not need changing.
  - Update unit tests in `coordinates.test.ts` to cover DDM and DMS entry without degree symbol.

**Acceptance criteria:**
- `60 10.1914N 24 56.3027E` is parsed as valid DDM coordinates.
- `60 10 11.49N 24 56 18.16E` is parsed as valid DMS coordinates.
- Existing tests with `°` still pass.

**Dependencies:** None.

---

### Task 5.2 — Add coordinate format selector (DD / DDM / DMS) to LookupBar (Issue #3, full)

**Status:** Not Started

**Context:** Per the NFR (Internationalization) and UX Guidelines, the default coordinate display format is DDM, and dispatchers must be able to switch formats at any time with automatic conversion.

**Design:**
- Add a segmented control (three small buttons: `DD`, `DDM`, `DMS`) next to the coordinate input.
- The currently active format is highlighted.
- When the dispatcher switches format:
  - If the coordinate input contains a parseable value, it is converted to the new format and the input is updated in place.
  - If the input is empty or not parseable, it is left unchanged.
- The active format is stored in `localStorage` under key `idispatch:coordFormat` so it persists across sessions.
- The default format is `DDM`.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.ts`
  - Add `#coordFormat: 'DD' | 'DDM' | 'DMS'` state (read from `localStorage`, default `DDM`).
  - In `#render()`, add three `<button>` elements for the format selector; append them alongside the coordinate input.
  - Add a `#setCoordFormat(fmt)` method that: updates `#coordFormat`, updates active button styling, and if the coord input has a parseable value, converts it to the new format using `formatCoordinates()`.
  - The placeholder text should be updated to match the active format.
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.css`
  - Add styles for the format selector buttons (small, grouped, similar to `.layer-toggle` style).
- `Implementation/clients/dispatcher-client/src/i18n/index.ts`
  - Add keys `lookup.coordFormat.DD`, `lookup.coordFormat.DDM`, `lookup.coordFormat.DMS` with appropriate translations.
  - Update `lookup.coordsPlaceholder` to be format-specific (one key per format) or dynamically generated.

**Acceptance criteria:**
- The coordinate input area shows DD, DDM, and DMS buttons.
- DDM is selected by default on first use.
- Clicking a format button when the coord input contains a valid coordinate converts the value.
- Clicking a format button when the input is empty or invalid does not change the input.
- The selected format persists after a page reload.
- Coordinate entry and lookup still work in all three formats.

**Dependencies:** Task 5.1.

---

## Phase 6 — Keyboard Navigation

### Task 6.1 — Add keyboard navigation to the search results dropdown (Issue #8)

**Status:** Not Started

**Context:** Per the UX Guidelines, the application must be fully usable with a keyboard.

**Design:**
- Track a `#focusedIndex: number | null` state for the currently highlighted dropdown item.
- Add a `keydown` listener on the address input that handles:
  - `ArrowDown`: move focus to the first item (or next item if one is already focused); wrap at the bottom.
  - `ArrowUp`: move focus to the last item (or previous item); wrap at the top.
  - `Enter`: if an item is focused, select it (call `#selectResult`).
  - `Escape`: close the dropdown and return focus to the address input.
- When `#focusedIndex` changes, add/remove a CSS class (e.g. `focused`) on the corresponding dropdown item.
- When mouse hover occurs, do NOT change `#focusedIndex` (to avoid conflicts); the existing `:hover` CSS is sufficient for mouse use.
- Reset `#focusedIndex` to `null` when the dropdown closes or new search results arrive.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.ts`
  - Add `#focusedIndex: number | null = null` field.
  - Extend `#showDropdown()` to reset `#focusedIndex`.
  - Add keyboard handling in `#render()` on the address input `keydown` event.
  - Add `#moveFocus(delta: number)` helper.
  - In `#selectResult`, reset `#focusedIndex`.
- `Implementation/clients/dispatcher-client/src/ui/LookupBar.css`
  - Add `.result-item.focused { background: #0e4579; color: var(--color-text-primary); }` (same as `:hover`).

**Acceptance criteria:**
- After typing a search query and receiving results, pressing ArrowDown highlights the first result.
- Pressing ArrowDown again moves to the second result.
- Pressing ArrowUp from the first item wraps to the last item.
- Pressing Enter on a highlighted item selects it and closes the dropdown.
- Pressing Escape closes the dropdown without selecting.
- Mouse click still works as before.
- `#focusedIndex` is reset when new results arrive.

**Dependencies:** None.

---

## Phase 7 — GIS Server: Road Name Search

### Task 7.1 — Add road segment name-only search to RoadSegmentSearcher (Issue #5, server-side)

**Status:** Not Started

**Root cause:** `AddressPointSearcher.search()` filters out results where `number` is null or blank (line: `.filter(r -> r.number() != null && !r.number().isBlank())`). `RoadSegmentSearcher` has no method for name-only queries. As a result, searching for a road name without a number returns zero results.

**Required changes:**

1. Add `RoadSegmentRepository.searchByName(streetName, limit, municipality)` — a jOOQ query that looks up road segments by name (pg_trgm similarity) and returns one representative point per unique (streetName, municipality) combination (e.g. midpoint of the first matching segment).

2. Add `RoadSegmentSearcher.searchByName(streetName, limit, municipality)` — calls the new repository method and maps results to `ScoredResult` with `AddressSource.ROAD_SEGMENT` and an appropriate score (e.g. 0.85 — below interpolated addresses).

3. Update `GeocodeService.dispatchSearches()`:
   - For `StreetQuery`: also dispatch `roadSegmentSearcher.searchByName(...)`.
   - For `PlaceQuery`: also dispatch `roadSegmentSearcher.searchByName(...)`.

4. The road name result returned by `searchByName` should have `number = null` so the client can distinguish it as a "road" result (name only, no specific address).

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/repository/RoadSegmentRepository.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/geocode/RoadSegmentSearcher.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/geocode/GeocodeService.java`

**Acceptance criteria:**
- Searching for `Mannerheimintie` (no number, single word → PlaceQuery) returns at least one result of type `address` or a new type representing a road.
- Searching for `Mannerheimintie 5` still returns address results with number `5`.
- The representative point returned for a road name result falls on or near the road geometry.
- Unit tests for `QueryParser`, `RoadSegmentSearcher`, and `GeocodeService` cover the new case.

**Dependencies:** None.

---

### Task 7.2 — Handle road results (no number) correctly on the client (Issue #5, client-side)

**Status:** Not Started

**Problem:** When the server returns an address result without a number, the client should still display it and place a marker on the road. The current `resultDisplayName` function would display the road name without a number, which is correct (fixed by Task 3.1). However, the `#selectResult` path works the same regardless of whether `number` is present — it places a marker at the coordinates returned by the server. No additional client-side changes are required beyond Task 3.1, but validation is needed.

**Verification only task.** Confirm through a Playwright test (added in Phase 9) that:
- A result with `type: address`, a `name` but no `number` appears in the dropdown.
- Clicking it places the marker at the result's coordinates.

**Files to modify:** None (purely verification).

**Dependencies:** Task 3.1, Task 7.1.

---

## Phase 8 — Map View Reset Fix

### Task 8.1 — Prevent map view from resetting (Issue #4)

**Status:** Not Started

**Root cause (suspected):** When `SecondaryWindow.connectedCallback()` fires, the map container element is freshly appended to the shadow DOM and has no rendered size yet. OpenLayers initialises the map immediately and records the container dimensions as zero. When the browser renders the layout and the container gets its actual size, OL may fire an internal resize event that causes the view to animate back to its initial `center` and `zoom`. Additionally, calling `setSource()` on the tile layer may internally trigger a view update in some OL versions.

**Fix:**

1. After `this.#initMap(mapEl, lookupBarEl)`, call `this.#olMap.updateSize()` in a `requestAnimationFrame` callback to ensure OL measures the real container size on the next paint.

2. Attach a `ResizeObserver` to `mapEl` that calls `this.#olMap.updateSize()` whenever the container changes size (handles window resize, layout preset changes, etc.).

3. Store the resize observer and disconnect it in `disconnectedCallback()`.

**Files to modify:**
- `Implementation/clients/dispatcher-client/src/ui/SecondaryWindow.ts`
  - After `this.#initMap(...)`, schedule `requestAnimationFrame(() => this.#olMap?.updateSize())`.
  - Add `#mapResizeObserver: ResizeObserver | null` field.
  - After `#initMap`, create and attach a `ResizeObserver` on `mapEl` that calls `updateSize()`.
  - In `disconnectedCallback()`, call `this.#mapResizeObserver?.disconnect()`.

**Note:** If the root cause turns out to be different (e.g. a bug in tile loading resetting the view), the fix may need to be revisited. A Playwright test that verifies the view centre and zoom persist after a delay is the best way to catch a regression.

**Acceptance criteria:**
- After the secondary window opens and tiles load, the zoom level and map centre selected by the user are not reset.
- Moving to a different map position and waiting 5 seconds does not reset the view.
- Resizing the window does not reset the view.

**Dependencies:** None.

---

## Phase 9 — Playwright Tests

### Task 9.1 — Add and update Playwright tests to verify all fixes

**Status:** Not Started

**File to update:** `Implementation/clients/dispatcher-client/e2e/lookup-address.spec.ts`

**New test cases to add:**

| Test | Covers | Mock needed |
|------|--------|-------------|
| Toolbar labels change when Finnish locale is active | Issue #2 | layers, tiles |
| Toolbar uses non-serif font (computed font-family check) | Issue #1 | layers, tiles |
| Address result shows street number in dropdown | Issue #6 | geocode: result with `number: "5"` |
| Marker persists after map pan (check marker still in source after moveend) | Issue #7 | geocode: single result |
| Marker persists after map zoom | Issue #7 | geocode: single result |
| Marker removed after clicking Clear | Issue #7 (confirm Clear still works) | geocode: single result |
| Arrow-down highlights first result; Enter selects it | Issue #8 | geocode: 2 results |
| Arrow-up from first item wraps to last; Escape closes dropdown | Issue #8 | geocode: 2 results |
| DDM coordinates entered without degree symbol are accepted | Issue #3 | tiles |
| DMS coordinates entered without degree symbol are accepted | Issue #3 | tiles |
| Format selector switches coordinate from DDM to DD format | Issue #3 | tiles |
| Road name without number returns result and places marker | Issue #5 | geocode: address result, no number |
| Map view does not reset after 5-second wait | Issue #4 | layers, tiles |

**Note on font check (Issue #1):** Use `page.evaluate()` to read `getComputedStyle(element).fontFamily` inside the shadow DOM and assert it contains `"Segoe UI"` or `system-ui`.

**Note on marker persistence (Issue #7):** Use `page.evaluate()` to inspect the OL `VectorSource` feature count inside the shadow DOM after a programmatic map pan.

**Acceptance criteria:**
- All 13 new tests pass.
- All previously passing tests still pass.

**Dependencies:** All tasks in phases 2–8.

---

## Execution Notes

### Recommended order

1. **Phase 1** (spec corrections) — these can be done before any code changes.
2. **Phase 2, 3, 4** (font, i18n, result display, marker persistence) — independent client fixes; can be done in parallel.
3. **Phase 5** (coordinate improvements) — Task 5.1 first, then 5.2.
4. **Phase 6** (keyboard nav) — independent.
5. **Phase 7** (GIS Server) — Task 7.1 (server), then 7.2 (client verification).
6. **Phase 8** (map reset) — independent.
7. **Phase 9** (tests) — after all fixes are in place.

### Parallelisation

Phases 2, 3, 4, 6, and 8 are entirely independent and can be worked on in parallel. Phase 7 requires the GIS Server Docker test stack (see [GIS-Server-Docker-Testing-Plan.md](GIS-Server-Docker-Testing-Plan.md)) for integration-level verification.

### No spec changes needed for

- Issue #1 (font): purely a CSS implementation gap; no spec update needed.
- Issue #6 (street number display): the API spec already includes `number` in the response; the bug is in the rendering code only.
- Issue #4 (map reset): an OL initialisation issue; no spec update needed.
