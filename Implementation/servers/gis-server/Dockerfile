# GIS Server Docker image — multi-stage build
#
# Build context must be the Implementation/ directory (the Maven project root):
#
#   cd Implementation/
#   docker build -t idispatchx/gis-server -f servers/gis-server/Dockerfile .
#
# Or equivalently, from the gis-server directory:
#
#   cd Implementation/servers/gis-server/
#   docker build -t idispatchx/gis-server -f Dockerfile ../..

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper so the build does not require Maven to be installed on the host
COPY .mvn/ .mvn/
COPY mvnw ./
RUN chmod +x mvnw

# Copy parent and module POMs before source to maximise layer cache reuse.
# Only source changes should bust the dependency-download layer.
COPY pom.xml ./
COPY shared/java-common/pom.xml shared/java-common/pom.xml
COPY shared/gis-database/pom.xml shared/gis-database/pom.xml
COPY servers/cad-server/pom.xml servers/cad-server/pom.xml
COPY servers/gis-server/pom.xml servers/gis-server/pom.xml
COPY tools/gis-data-importer/pom.xml tools/gis-data-importer/pom.xml

# Resolve and cache dependencies
RUN ./mvnw dependency:go-offline -pl servers/gis-server -am -q --no-transfer-progress

# Copy source for all modules needed by the GIS Server
COPY shared/java-common/src/ shared/java-common/src/
COPY shared/gis-database/src/ shared/gis-database/src/
COPY servers/gis-server/src/ servers/gis-server/src/

# Build the executable fat JAR (tests are run separately in CI before building the image)
RUN ./mvnw package -pl servers/gis-server -am -DskipTests -q --no-transfer-progress

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/servers/gis-server/target/gis-server-exec.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-Djava.awt.headless=true", "-jar", "app.jar"]
