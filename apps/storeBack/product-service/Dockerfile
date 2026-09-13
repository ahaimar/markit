FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :product-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/product-service/build/libs/product-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]