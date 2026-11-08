FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S opspilot && adduser -S -G opspilot -u 10001 opspilot
WORKDIR /app
COPY --from=build --chown=opspilot:opspilot /workspace/target/opspilot-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 CMD wget -qO- http://127.0.0.1:8081/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
