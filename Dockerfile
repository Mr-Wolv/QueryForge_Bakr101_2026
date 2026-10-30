# Build stage
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

# Use the same pinned k6 release in the containerized runner.
FROM grafana/k6:2.3.0 AS k6

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/queryforge-1.0.0.jar app.jar
COPY --from=k6 /usr/bin/k6 /usr/local/bin/k6
COPY benchmark/k6 ./benchmark/k6
COPY benchmark/results ./benchmark/results
COPY docs/experiments ./docs/experiments
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
