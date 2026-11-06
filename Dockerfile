# This root-level Dockerfile delegates to backend/Dockerfile
# so Render can find it when it auto-detects (ignoring render.yaml).
#
# Render's blueprint parser sometimes ignores the dockerfilePath in
# render.yaml and looks for ./Dockerfile by default. Putting this
# file at the repo root ensures Render always finds a valid Dockerfile.
#
# The actual build logic lives in backend/Dockerfile.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Install Maven via apt (Debian's package manager)
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

# Copy backend source (we're at repo root, source is in backend/)
COPY backend/pom.xml .
COPY backend/src ./src

# Build the JAR
RUN mvn -B clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=default
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -Xms256m -Xmx512m"
ENV SERVER_PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
