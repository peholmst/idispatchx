# NFR: Internationalization

iDispatchX is designed to be used in Finland only. This implies the following:

## Geospatial Data

* At no point will the system have to handle geographical coordinates outside the borders of Finland.
* Geographical coordinates can be in either EPSG:4326 (WGS 84) or EPSG:3067 (EUREF-FIN / TM35FIN(E,N)). The system must:
  * Support both Coordinate Reference Systems (CRS),
  * Know which CRS a particular set of coordinates are in, and
  * Be able to convert a set of coordinates from one CRS to another.
* Coordinate conversions must be performed in backend services; clients must not assume responsibility for CRS conversion.
* The geospatial material from the National Land Survey of Finland always use EPSG:3067. The system must use this CRS for geocoding and rasters.
* For operational data (such as incident, station, and unit locations), the system must use EPSG:4326 for storage and processing.
  * The system must convert geocoded addresses from EPSG:3067 to EPSG:4326.
* Coordinates are always presented in user interfaces in EPSG:4326. Clients must not assume or expose any other CRS.

### Coordinate Precision and Bounds

All EPSG:4326 coordinates in the system must adhere to the following rules:

* **Precision:** 6 decimal places maximum. This provides approximately 0.1 meter precision, which is sufficient for operational purposes.
* **Bounds:** Coordinates must fall within the WGS84 bounds of the EPSG:3067 coverage area (Finland):
  * Latitude: 58.84° to 70.09° N
  * Longitude: 19.08° to 31.59° E
* Coordinates outside these bounds must be rejected by the system.

### Coordinate Display and Entry Formats

User interfaces that allow coordinate entry or display must support the following formats:

* **DD (Decimal Degrees):** e.g., 60.169857, 24.938379
* **DDM (Degrees Decimal Minutes):** e.g., 60° 10.1914' N, 24° 56.3027' E
* **DMS (Degrees Minutes Seconds):** e.g., 60° 10' 11.49" N, 24° 56' 18.16" E

Requirements:
* Users must be able to enter coordinates in any of the three formats.
* Users must be able to switch between display formats at any time.
* When switching formats, entered data must be automatically converted without loss of precision (up to the 6 decimal place limit).
* The default display format is **DDM (Degrees Decimal Minutes)**.
* Format conversion is a client-side display concern; storage and processing always use DD in EPSG:4326.

## Terminology and Language

* Domain terminology is based on regulations, processes, and procedures of the Finnish emergency services.
* All user interfaces must be available in both Finnish and Swedish.
  * To allow for international developers, the source code, documentation and system log messages are in English. All user interfaces are also available in English.
  * Applications are not required to support changing language on the fly. In other words, an application restart or session reload is acceptable when changing the language of the UI.
  * If a translation is missing, English is used as a fallback language.

## Date and Time

* The system must always present dates and times in the `Europe/Helsinki` timezone using a 24 hour clock.
  * The system should always store dates and times in UTC to avoid problems with daylight savings time.
