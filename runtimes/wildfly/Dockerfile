# WildFly provisioned by pom.xml (Galleon layers), configured offline by config/setup.cli.
# Build context: this directory after `mvn package`.
FROM eclipse-temurin:25-jre
COPY target/server /opt/wildfly
COPY target/image/postgresql.jar config/setup.cli /tmp/setup/
RUN /opt/wildfly/bin/jboss-cli.sh --file=/tmp/setup/setup.cli && rm -r /tmp/setup
# ROOT.war is served at "/", like every other runtime.
COPY target/image/app.war /opt/wildfly/standalone/deployments/ROOT.war
EXPOSE 8080
ENTRYPOINT ["/opt/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
