FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :user-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/user-service/build/libs/user-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]