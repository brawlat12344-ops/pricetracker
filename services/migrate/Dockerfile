# Service: migrate — one-shot Flyway migration
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
LABEL service="migrate" profile="migrate"
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/pricetracker-1.0.0.jar app.jar
RUN chown -R app:app /app
USER app
# No web server and no healthcheck: the container must exit with code 0.
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=migrate", "--spring.main.web-application-type=none"]
