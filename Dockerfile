# Build stage: compile the application JAR
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Leverage layer caching by separating dependency resolution
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: slim JRE image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from the build stage
# Copy only the repackaged Spring Boot JAR (exclude original-*)
COPY --from=build /build/target/alexandria-api-*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
