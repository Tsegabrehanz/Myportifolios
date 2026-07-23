# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the POM first and pre-fetch dependencies so this layer is cached
# across rebuilds that only change source files, not pom.xml.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as a non-root user rather than the container default root.
RUN addgroup -S eems && adduser -S eems -G eems

# Create the uploads directory (FileStorageService's default target,
# see application.yml file.upload-dir) and hand it to the eems user
# before the volume is ever attached - a named volume copies the
# image's existing directory content/ownership into itself on first
# use, so this is what makes /app/uploads actually writable by the
# non-root user once docker-compose.yml mounts a volume there.
RUN mkdir -p /app/uploads && chown -R eems:eems /app/uploads

USER eems

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Defaults to the "docker" Spring profile (see application.yml) which
# points at the "postgres" service name from docker-compose.yml with
# ddl-auto=update for a self-contained demo stack. Override
# SPRING_PROFILES_ACTIVE=prod for a real deployment with your own
# migration-managed database - see README.md "Docker" section.
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-jar", "app.jar"]
