# GIS Server Docker image
#
# The gis-database module uses docker-maven-plugin to start a PostGIS container
# during code generation, so the Maven build must run on the host (not inside
# Docker). Build the fat JAR first, then build this image:
#
#   cd Implementation/
#   ./mvnw package -pl servers/gis-server -am -DskipTests
#   docker build -t idispatchx/gis-server -f servers/gis-server/Dockerfile .

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY servers/gis-server/target/gis-server-exec.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-Djava.awt.headless=true", "-jar", "app.jar"]
