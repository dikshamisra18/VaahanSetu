# Multi-stage build for VaahanSetu Spring Boot Backend
# Stage 1: build with Maven (uses Debian-based image for better compatibility)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Install Maven via apt (Debian's package manager has a working Maven package,
# unlike Alpine's apk). This is the simplest, most reliable approach.
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

# Verify Maven installed correctly
RUN mvn -version

# Pre-copy pom.xml and download deps (better Docker layer caching)
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

# Copy source and build the JAR
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: runtime (smaller JRE image)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR with a fallback for the name
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=default
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -Xms256m -Xmx512m"
ENV SERVER_PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
