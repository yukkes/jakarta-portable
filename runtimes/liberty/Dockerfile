# Open Liberty provisioned by pom.xml (only the features config/server.xml names).
# Build context: this directory after `mvn package`.
FROM eclipse-temurin:25-jre
COPY target/liberty/wlp /opt/wlp
COPY config/ /opt/wlp/usr/servers/defaultServer/
COPY target/image/app.war /opt/wlp/usr/servers/defaultServer/apps/app.war
COPY target/image/postgresql.jar /opt/wlp/usr/servers/defaultServer/lib/postgresql.jar
# Start the server once so that the image ships a populated workarea (OSGi bundle and feature
# caches); otherwise every container start is a cold first start (about 1.3 s slower, see
# docs/performance.md). There is no database here, so the application fails to start; only the
# server's caches are kept, and the application is deployed again on every start.
RUN /opt/wlp/bin/server start defaultServer \
 && /opt/wlp/bin/server stop defaultServer \
 && rm -rf /opt/wlp/usr/servers/defaultServer/logs
EXPOSE 8080
ENTRYPOINT ["/opt/wlp/bin/server", "run", "defaultServer"]
