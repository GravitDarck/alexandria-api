FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/alexandria-api-0.1.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
