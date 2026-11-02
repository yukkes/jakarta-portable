# Payara Micro, the WAR and the JDBC driver, all staged by pom.xml.
# Build context: this directory after `mvn package`.
FROM eclipse-temurin:25-jre
WORKDIR /opt/payara-micro
COPY target/image/payara-micro.jar target/image/app.war target/image/postgresql.jar ./
COPY config/post-boot.asadmin ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "payara-micro.jar", "--port", "8080", "--contextroot", "/", "--nocluster", \
            "--deploy", "app.war", "--addlibs", "postgresql.jar", "--postbootcommandfile", "post-boot.asadmin"]
