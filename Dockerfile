FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app \
 && mkdir logs \
 && chown 10001:10001 logs
COPY --from=build /src/target/dealership-0.1.0.jar app.jar
USER 10001
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -XX:MaxRAMPercentage=50.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
