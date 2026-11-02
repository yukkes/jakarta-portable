# Payara Server Full: the vendor image, plus the WAR and the JDBC driver staged by pom.xml.
# Build context: this directory after `mvn package`.
FROM payara/server-full:7.2026.10
COPY target/image/postgresql.jar ${PAYARA_DIR}/glassfish/domains/domain1/lib/
COPY config/post-boot.asadmin ${POSTBOOT_COMMANDS}
# The image deploys everything in DEPLOY_DIR with DEPLOY_PROPS; "/" like every other runtime.
COPY target/image/app.war ${DEPLOY_DIR}/app.war
ENV DEPLOY_PROPS="--contextroot / --name app"
# EclipseLink: no shared L2 cache, JDBC statement cache on, no deploy-time weaving
# (docs/performance.md). Payara 7 rejects these as asadmin JVM options, hence JVM_ARGS.
ENV JVM_ARGS="-Declipselink.cache.shared.default=false -Declipselink.jdbc.cache-statements=true -Declipselink.weaving=false"
# The image ships an empty http-thread-pool, which leaves 5 HTTP workers (docs/performance.md).
# asadmin silently drops unknown attributes, so the pool is written into domain.xml directly.
RUN sed -i -E 's#<thread-pool name="http-thread-pool"[^>]*>(</thread-pool>)?#<thread-pool name="http-thread-pool" min-thread-pool-size="64" max-thread-pool-size="200" max-queue-size="1000"></thread-pool>#' \
        ${PAYARA_DIR}/glassfish/domains/domain1/config/domain.xml \
 && grep -q 'name="http-thread-pool" min-thread-pool-size="64"' ${PAYARA_DIR}/glassfish/domains/domain1/config/domain.xml
EXPOSE 8080
