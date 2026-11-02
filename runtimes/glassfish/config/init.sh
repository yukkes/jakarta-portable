#!/bin/bash
# Run by the image's entrypoint before the server starts. GlassFish has no post-boot command
# file, and the deployment needs a running domain, so it waits in the background. The context
# root is "/" like every other runtime (an archive name would give /app).
(
    for _ in $(seq 600); do
        # Captured first: piping asadmin into grep -q can fail at random (SIGPIPE).
        domains=$(asadmin list-domains 2>/dev/null || true)
        if grep -qx "${AS_DOMAIN_NAME} running" <<<"$domains"; then
            asadmin --passwordfile="${AS_ADMIN_PASSWORDFILE}" deploy --contextroot / --name app /opt/app/app.war
            exit
        fi
        sleep 0.5
    done
    echo "init.sh: the domain did not start, app.war was not deployed" >&2
) &
