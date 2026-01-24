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

## Terminology and Language

* Domain terminology is based on regulations, processes, and procedures of the Finnish emergency services.
* All user interfaces must be available in both Finnish and Swedish.
  * To allow for international developers, the source code, documentation and system log messages are in English. All user interfaces are also available in English.
  * Applications are not required to support changing language on the fly. In other words, an application restart or session reload is acceptable when changing the language of the UI.
  * If a translation is missing, English is used as a fallback language.

## Date and Time

* The system must always present dates and times in the `Europe/Helsinki` timezone using a 24 hour clock.
  * The system should always store dates and times in UTC to avoid problems with daylight savings time.
