FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app
COPY pom.xml .

RUN mvn dependency:go-offline -B 2>/dev/null || true

COPY src src
RUN mvn package -Dquarkus.package.type=uber-jar -DskipTests -B \
    -Dquarkus.datasource.db-kind=postgresql \
    -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost/unused \
    -Dquarkus.datasource.username=unused \
    -Dquarkus.datasource.password=unused \
    -Dquarkus.hibernate-orm.database.generation=none

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/deal-finder-api-1.0.0-SNAPSHOT-runner.jar app.jar

EXPOSE 8080

CMD ["java", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8080", "-jar", "app.jar"]
