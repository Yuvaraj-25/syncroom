# Build stage
FROM maven:3.9.13-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Pre-fetch dependencies
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/syncroom-backend-1.0.0.jar app.jar

EXPOSE 4000
ENTRYPOINT ["java", "-jar", "app.jar"]
