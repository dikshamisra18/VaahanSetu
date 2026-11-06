# Multi-stage build for VaahanSetu Spring Boot Backend
# Stage 1: build with Maven (uses Debian-based image for better compatibility)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Install Maven via the official tarball (more reliable than apt on slim images)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    curl -fsSL https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz -o /tmp/maven.tar.gz && \
    tar xzf /tmp/maven.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn && \
    rm /tmp/maven.tar.gz

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
