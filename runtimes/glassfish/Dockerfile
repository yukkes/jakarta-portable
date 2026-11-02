# GlassFish: the vendor image, configured at build time by config/setup.asadmin, plus the WAR
# and the JDBC driver staged by pom.xml. Build context: this directory after `mvn package`.
FROM ghcr.io/eclipse-ee4j/glassfish:8.0.5
COPY --chown=glassfish:glassfish target/image/postgresql.jar config/setup.asadmin /tmp/setup/
# The image creates its domain on first start; doing it here lets the configuration be baked in.
# multimode exits 0 even when a command fails, so its output is checked.
RUN docker-entrypoint.sh true \
 && cp /tmp/setup/postgresql.jar ${PATH_GF_DOMAIN}/lib/ \
 && asadmin start-domain \
 && asadmin --passwordfile=${AS_ADMIN_PASSWORDFILE} multimode --file /tmp/setup/setup.asadmin | tee /tmp/setup/out \
 && asadmin stop-domain \
 && ! grep -q "failed\." /tmp/setup/out \
 && rm -r /tmp/setup
# Deployed on every start by the image's custom/init.sh hook (the deployment needs the database).
COPY target/image/app.war /opt/app/app.war
COPY config/init.sh custom/init.sh
EXPOSE 8080
