FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S enhara && adduser -S enhara -G enhara
RUN mkdir -p /app/data/vehicle-photos && chown -R enhara:enhara /app/data
COPY --from=build /workspace/target/enhara-api-*.jar app.jar
USER enhara
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
