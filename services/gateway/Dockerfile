# Service: gateway — REST API + Thymeleaf UI, вызывает catalog по HTTP
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
LABEL service="gateway" profile="gateway"
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/pricetracker-1.0.0.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-Dloader.main=com.example.pricetracker.gateway.GatewayApplication", \
    "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher", \
    "--spring.profiles.active=gateway"]
