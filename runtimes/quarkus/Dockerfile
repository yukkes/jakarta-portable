# Quarkus fast-jar. Build context: this directory after `mvn package`.
FROM eclipse-temurin:25-jre
WORKDIR /opt/app
COPY target/quarkus-app/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
