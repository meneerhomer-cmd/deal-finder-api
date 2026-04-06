FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/deal-finder-api-1.0.0-SNAPSHOT-runner.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xmx256m"

CMD ["java", "-jar", "app.jar"]
